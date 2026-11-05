package com.netscope.lan.core.net;

import com.netscope.lan.core.model.PortResult;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class DeviceProbe {
    public static final class Info {
        public String httpTitle = "";
        public String server = "";
        public String tls = "";

        public boolean found() {
            return !httpTitle.isEmpty() || !server.isEmpty() || !tls.isEmpty();
        }
    }

    private static final Pattern TITLE = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CN = Pattern.compile("CN=([^,]+)");
    private static final int MAX_BODY = 24576;

    private DeviceProbe() {}

    public static Info probe(String ip, List<PortResult> openPorts, int timeoutMs) {
        Info info = new Info();
        if (ip == null || openPorts == null) return info;
        int timeout = Math.max(400, timeoutMs);
        int attempts = 0;
        for (PortResult port : openPorts) {
            if (!ServiceCatalog.isHttp(port.port)) continue;
            if (attempts++ >= 4) break;
            boolean tls = isTlsPort(port.port);
            try {
                Response response = fetch(ip, port.port, tls, timeout);
                if (response != null) {
                    if (info.httpTitle.isEmpty()) info.httpTitle = response.title;
                    if (info.server.isEmpty()) info.server = response.server;
                }
            } catch (Exception ignored) {
            }
            if (tls) {
                try {
                    String cert = tlsInfo(ip, port.port, timeout);
                    if (info.tls.isEmpty()) info.tls = cert;
                } catch (Exception ignored) {
                }
            }
        }
        return info;
    }

    private static final class Response {
        String title = "";
        String server = "";
    }

    private static Response fetch(String ip, int port, boolean tls, int timeoutMs) {
        Socket socket = null;
        try {
            socket = tls ? tlsSocket() : new Socket();
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            socket.setSoTimeout(Math.max(400, timeoutMs));
            if (socket instanceof SSLSocket) ((SSLSocket) socket).startHandshake();

            String host = port == 80 || port == 443 ? ip : ip + ":" + port;
            String request = "GET / HTTP/1.0\r\nHost: " + host
                    + "\r\nUser-Agent: NetScope/1.1\r\nAccept: text/html,*/*"
                    + "\r\nConnection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[2048];
            long deadline = System.currentTimeMillis() + Math.max(700, timeoutMs);
            while (buffer.size() < MAX_BODY && System.currentTimeMillis() < deadline) {
                int read;
                try {
                    read = in.read(chunk);
                } catch (SocketTimeoutException e) {
                    break;
                }
                if (read < 0) break;
                buffer.write(chunk, 0, read);
                if (read < chunk.length) break;
            }

            String raw = buffer.toString("UTF-8");
            if (!raw.startsWith("HTTP/")) return null;
            Response response = new Response();
            response.server = header(raw, "Server");
            int bodyAt = raw.indexOf("\r\n\r\n");
            String body = bodyAt >= 0 ? raw.substring(bodyAt + 4) : "";
            Matcher matcher = TITLE.matcher(body);
            if (matcher.find()) response.title = cleanText(matcher.group(1), 80);
            return response;
        } catch (Exception e) {
            return null;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static String tlsInfo(String ip, int port, int timeoutMs) {
        Socket socket = null;
        try {
            socket = tlsSocket();
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            socket.setSoTimeout(Math.max(500, timeoutMs));
            ((SSLSocket) socket).startHandshake();
            Certificate[] chain = ((SSLSocket) socket).getSession().getPeerCertificates();
            if (chain == null || chain.length == 0) return "";
            X509Certificate cert = (X509Certificate) chain[0];
            String subject = commonName(cert.getSubjectX500Principal().getName());
            String issuer = commonName(cert.getIssuerX500Principal().getName());
            String until = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    .format(cert.getNotAfter());
            return "CN=" + subject + " · emisor " + issuer + " · hasta " + until;
        } catch (Exception e) {
            return "";
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static Socket tlsSocket() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{TRUST_ALL}, new SecureRandom());
        return context.getSocketFactory().createSocket();
    }

    // Solo se inspecciona el certificado del dispositivo local; no se envían credenciales.
    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
    };

    public static boolean isTlsPort(int port) {
        return port == 443 || port == 8443 || port == 9443 || port == 5001;
    }

    private static String header(String text, String name) {
        String lower = text.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(name.toLowerCase(Locale.ROOT));
        if (index < 0) return "";
        int end = text.indexOf('\n', index);
        if (end < 0) end = text.length();
        String line = text.substring(index, end).trim();
        int colon = line.indexOf(':');
        return colon >= 0 ? line.substring(colon + 1).trim() : "";
    }

    private static String commonName(String dn) {
        Matcher matcher = CN.matcher(dn == null ? "" : dn);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private static String cleanText(String value, int max) {
        if (value == null) return "";
        String text = value.replaceAll("<[^>]+>", " ")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
        if (text.length() > max) text = text.substring(0, max) + "…";
        return text;
    }
}
