package com.netscope.lan.core.net;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class WebContentScanner {
    public static final class Finding {
        public final String path;
        public final int status;
        public final int length;
        public final String location;

        Finding(String path, int status, int length, String location) {
            this.path = path;
            this.status = status;
            this.length = length;
            this.location = location;
        }
    }

    public interface Listener {
        void onFinding(Finding finding);
        void onProgress(int done, int total);
    }

    // Lista de rutas habituales para auditoría de contenido en una red propia.
    private static final String[] PATHS = {
            "admin", "administrator", "admin/login", "login", "signin", "dashboard",
            "api", "api/v1", "api/status", "api/docs", "graphql", "swagger", "swagger-ui",
            "docs", "documentation", "help", "support", "status", "health", "healthz",
            "metrics", "version", "info", "debug", "test", "dev", "staging", "demo",
            "config", "configuration", "settings", "setup", "install", "backup",
            "backups", "db", "database", "dump.sql", "backup.sql", "backup.zip",
            "server-status", "server-info", "phpinfo.php", "info.php", "test.php",
            ".git/HEAD", ".git/config", ".env", ".htaccess", ".htpasswd", "robots.txt",
            "sitemap.xml", "crossdomain.xml", "security.txt", ".well-known/security.txt",
            "wp-admin/", "wp-login.php", "wp-content/", "wp-json/", "xmlrpc.php",
            "administrator/", "manager/html", "manager/status", "host-manager/html",
            "console", "console/login", "jenkins", "jenkins/login", "grafana", "grafana/login",
            "kibana", "prometheus", "alertmanager", "cadvisor", "portainer", "phpmyadmin",
            "pma", "adminer", "webmail", "mail", "cpanel", "webmin", "uploads", "files",
            "download", "downloads", "images", "img", "assets", "static", "css", "js",
            "public", "private", "tmp", "logs", "log", "error.log", "access.log",
            "cgi-bin/", "shell", "cmd", "exec", "login.php", "index.php", "home",
            "portal", "intranet", "remote", "vnc", "rtsp", "stream", "media", "video"
    };

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public static String[] wordlist() {
        return PATHS.clone();
    }

    public static int wordlistSize() {
        return PATHS.length;
    }

    public List<Finding> scan(String ip, int port, boolean tls, int timeoutMs,
                              int concurrency, Listener listener) {
        List<Finding> findings = Collections.synchronizedList(new ArrayList<Finding>());
        if (cancelled.get()) return findings;
        int threads = Math.max(1, Math.min(8, concurrency));
        int timeout = Math.max(400, timeoutMs);
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "netscope-wordlist");
            t.setDaemon(true);
            return t;
        });
        CompletionService<Finding> completion = new ExecutorCompletionService<>(pool);
        int total = PATHS.length;
        for (String path : PATHS) {
            completion.submit(() -> probe(ip, port, tls, path, timeout));
        }
        try {
            for (int i = 0; i < total; i++) {
                if (cancelled.get()) break;
                Future<Finding> future;
                try {
                    future = completion.poll(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (future == null) continue;
                Finding finding;
                try {
                    finding = future.get();
                } catch (Exception e) {
                    finding = null;
                }
                if (finding != null) {
                    findings.add(finding);
                    if (listener != null) listener.onFinding(finding);
                }
                if (listener != null) listener.onProgress(i + 1, total);
            }
        } finally {
            pool.shutdownNow();
        }
        return findings;
    }

    private Finding probe(String ip, int port, boolean tls, String path, int timeoutMs) {
        if (cancelled.get()) return null;
        Socket socket = null;
        try {
            socket = tls ? tlsSocket() : new Socket();
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            socket.setSoTimeout(Math.max(300, timeoutMs));
            if (socket instanceof SSLSocket) ((SSLSocket) socket).startHandshake();
            String host = (port == 80 || port == 443) ? ip : ip + ":" + port;
            String target = path.startsWith("/") ? path : "/" + path;
            String request = "GET " + target + " HTTP/1.0\r\nHost: " + host
                    + "\r\nUser-Agent: NetScope/1.1\r\nAccept: */*"
                    + "\r\nConnection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            long deadline = System.currentTimeMillis() + Math.max(400, timeoutMs);
            while (buffer.size() < 4096 && System.currentTimeMillis() < deadline) {
                int read = in.read(chunk);
                if (read < 0) break;
                buffer.write(chunk, 0, read);
                if (read < chunk.length) break;
            }
            if (cancelled.get()) return null;
            String head = buffer.toString("UTF-8");
            int status = parseStatus(head);
            if (!isInteresting(status)) return null;
            String location = header(head, "location");
            int length = parseLength(head);
            return new Finding(target, status, length, location);
        } catch (Exception e) {
            return null;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static boolean isInteresting(int status) {
        if (status >= 200 && status <= 302) return true;
        if (status == 307 || status == 308) return true;
        return status == 401 || status == 403;
    }

    public static int parseStatus(String head) {
        if (head == null || !head.startsWith("HTTP/")) return -1;
        int lineEnd = head.indexOf("\r\n");
        String line = lineEnd >= 0 ? head.substring(0, lineEnd) : head;
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) return -1;
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String header(String head, String name) {
        if (head == null) return "";
        String lowerName = name.toLowerCase(Locale.ROOT);
        String[] lines = head.split("\r\n");
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) continue;
            if (lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT).equals(lowerName)) {
                return lines[i].substring(colon + 1).trim();
            }
        }
        return "";
    }

    private static int parseLength(String head) {
        String value = header(head, "content-length");
        if (value.isEmpty()) return -1;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Socket tlsSocket() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());
        return context.getSocketFactory().createSocket();
    }

    // Solo se inspecciona el dispositivo local; no se valida la cadena de confianza.
    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    static List<String> uniqueSorted(String[] values) {
        Set<String> set = new LinkedHashSet<>(Arrays.asList(values));
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }
}
