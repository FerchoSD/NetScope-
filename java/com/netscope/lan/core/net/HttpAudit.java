package com.netscope.lan.core.net;

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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class HttpAudit {
    public static final class Result {
        public boolean reachable;
        public int statusCode = -1;
        public String statusLine = "";
        public String title = "";
        public String server = "";
        public String poweredBy = "";
        public String contentType = "";
        public String location = "";
        public String technologies = "";
        public String tls = "";
        public long durationMs;
        public String error = "";
        public final LinkedHashMap<String, String> headers = new LinkedHashMap<>();
        public final List<String> findings = new ArrayList<>();

        public String header(String name) {
            String value = headers.get(name.toLowerCase(Locale.ROOT));
            return value == null ? "" : value;
        }

        public String compact() {
            if (!reachable) return error.isEmpty() ? "Sin respuesta HTTP" : error;
            StringBuilder sb = new StringBuilder();
            sb.append(statusLine.isEmpty() ? "HTTP " + statusCode : statusLine);
            if (durationMs >= 0) sb.append(" · ").append(durationMs).append(" ms");
            if (!server.isEmpty()) sb.append(" · ").append(server);
            if (!technologies.isEmpty()) sb.append(" · ").append(technologies);
            if (findings.isEmpty()) sb.append(" · sin ausencias destacadas");
            else sb.append(" · ").append(findings.size()).append(" hallazgo(s)");
            return sb.toString();
        }

        public String summary() {
            if (!reachable) {
                StringBuilder sb = new StringBuilder("Sin respuesta HTTP.");
                if (!error.isEmpty()) sb.append("\n").append(error);
                return sb.toString();
            }
            StringBuilder sb = new StringBuilder();
            sb.append(statusLine.isEmpty() ? "HTTP " + statusCode : statusLine);
            if (durationMs >= 0) sb.append(" · ").append(durationMs).append(" ms");
            sb.append('\n');
            if (!server.isEmpty()) sb.append("Servidor: ").append(server).append('\n');
            if (!poweredBy.isEmpty()) sb.append("X-Powered-By: ").append(poweredBy).append('\n');
            if (!contentType.isEmpty()) sb.append("Tipo: ").append(contentType).append('\n');
            if (!location.isEmpty()) sb.append("Redirección: ").append(location).append('\n');
            if (!title.isEmpty()) sb.append("Título: ").append(title).append('\n');
            if (!technologies.isEmpty()) sb.append("Tecnologías: ").append(technologies).append('\n');
            if (!tls.isEmpty()) sb.append("TLS: ").append(tls).append('\n');

            sb.append("\nCabeceras (").append(headers.size()).append("):\n");
            int shown = 0;
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (shown++ >= 18) {
                    sb.append("… y ").append(headers.size() - 18).append(" más\n");
                    break;
                }
                String value = entry.getValue();
                if (value.length() > 90) value = value.substring(0, 90) + "…";
                sb.append("· ").append(entry.getKey()).append(": ").append(value).append('\n');
            }

            sb.append("\nSeguridad:\n");
            if (findings.isEmpty()) {
                sb.append("· Sin ausencias destacadas en cabeceras\n");
            } else {
                for (String finding : findings) sb.append("· ").append(finding).append('\n');
            }
            return sb.toString().trim();
        }
    }

    private static final Pattern TITLE = Pattern.compile(
            "<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CN = Pattern.compile("CN=([^,]+)");
    private static final int MAX_BODY = 32768;

    private HttpAudit() {}

    public static boolean isTlsPort(int port) {
        return port == 443 || port == 8443 || port == 9443 || port == 5001;
    }

    public static Result audit(String ip, int port, int timeoutMs) {
        return audit(ip, port, isTlsPort(port), timeoutMs);
    }

    public static Result audit(String ip, int port, boolean tls, int timeoutMs) {
        Result result = new Result();
        long start = System.currentTimeMillis();
        Socket socket = null;
        try {
            socket = tls ? tlsSocket() : new Socket();
            socket.connect(new InetSocketAddress(ip, port), Math.max(400, timeoutMs));
            socket.setSoTimeout(Math.max(400, timeoutMs));
            if (socket instanceof SSLSocket) ((SSLSocket) socket).startHandshake();
            if (socket instanceof SSLSocket) result.tls = tlsDetails((SSLSocket) socket);

            String host = (port == 80 || port == 443) ? ip : ip + ":" + port;
            String request = "GET / HTTP/1.0\r\nHost: " + host
                    + "\r\nUser-Agent: NetScope/1.1\r\nAccept: text/html,*/*"
                    + "\r\nConnection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
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
            result = parse(raw);
            result.durationMs = System.currentTimeMillis() - start;
            if (result.reachable && tls) {
                // parse() no conoce el contexto TLS; se conserva el certificado ya leído.
                String tlsInfo = tlsDetailsFromSession(socket);
                result.tls = tlsInfo.isEmpty() ? result.tls : tlsInfo;
            }
            if (!result.reachable) result.error = "La respuesta no parece HTTP.";
            return result;
        } catch (Exception e) {
            result.error = "No se pudo conectar: " + e.getClass().getSimpleName();
            result.durationMs = System.currentTimeMillis() - start;
            return result;
        } finally {
            if (socket != null) {
                try { socket.close(); } catch (Exception ignored) {}
            }
        }
    }

    public static Result parse(String raw) {
        Result result = new Result();
        if (raw == null || raw.isEmpty()) {
            result.error = "Respuesta vacía.";
            return result;
        }
        int lineEnd = raw.indexOf("\r\n");
        String statusLine = lineEnd >= 0 ? raw.substring(0, lineEnd) : raw;
        statusLine = statusLine.trim();
        result.statusLine = statusLine;
        if (!statusLine.startsWith("HTTP/")) {
            result.error = "La respuesta no es HTTP.";
            return result;
        }
        result.reachable = true;
        String[] parts = statusLine.split("\\s+");
        if (parts.length >= 2) {
            try {
                result.statusCode = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }

        int bodyAt = raw.indexOf("\r\n\r\n");
        String head = bodyAt >= 0 ? raw.substring(0, bodyAt) : raw;
        String body = bodyAt >= 0 ? raw.substring(bodyAt + 4) : "";
        String[] lines = head.split("\r\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (key.isEmpty()) continue;
            result.headers.put(key, value);
        }

        result.server = result.header("server");
        result.poweredBy = result.header("x-powered-by");
        result.contentType = result.header("content-type");
        result.location = result.header("location");

        Matcher matcher = TITLE.matcher(body);
        if (matcher.find()) result.title = cleanText(matcher.group(1), 100);
        result.technologies = technologies(result, body);
        result.findings.addAll(findings(result));
        return result;
    }

    private static Set<String> techSet(Result result, String body) {
        Set<String> tech = new LinkedHashSet<>();
        String server = result.server.toLowerCase(Locale.ROOT);
        String powered = result.poweredBy.toLowerCase(Locale.ROOT);
        if (server.contains("nginx")) tech.add("nginx");
        if (server.contains("apache")) tech.add("Apache");
        if (server.contains("iis") || server.contains("microsoft-httpapi")) tech.add("IIS");
        if (server.contains("lighttpd")) tech.add("lighttpd");
        if (server.contains("caddy")) tech.add("Caddy");
        if (server.contains("tomcat")) tech.add("Tomcat");
        if (server.contains("jetty")) tech.add("Jetty");
        if (server.contains("cloudflare")) tech.add("Cloudflare");
        if (powered.contains("php")) tech.add("PHP");
        if (powered.contains("asp.net")) tech.add("ASP.NET");
        if (powered.contains("express")) tech.add("Express");
        if (powered.contains("servlet")) tech.add("Java");

        String html = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (html.contains("wp-content") || html.contains("wp-includes")) tech.add("WordPress");
        if (html.contains("_next/") || html.contains("__next_data__")) tech.add("Next.js");
        if (html.contains("drupal")) tech.add("Drupal");
        if (html.contains("joomla")) tech.add("Joomla");
        if (html.contains("bootstrap")) tech.add("Bootstrap");
        if (html.contains("jquery")) tech.add("jQuery");
        if (html.contains("react")) tech.add("React");
        if (html.contains("vue.js") || html.contains("vue.min.js")) tech.add("Vue");

        String cookie = result.header("set-cookie").toLowerCase(Locale.ROOT);
        if (cookie.contains("phpsessid")) tech.add("PHP");
        if (cookie.contains("jsessionid")) tech.add("Java");
        if (cookie.contains("asp.net_sessionid")) tech.add("ASP.NET");
        return tech;
    }

    private static String technologies(Result result, String body) {
        StringBuilder sb = new StringBuilder();
        for (String tech : techSet(result, body)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(tech);
        }
        return sb.toString();
    }

    public static List<String> findings(Result result) {
        List<String> findings = new ArrayList<>();
        if (result.header("x-content-type-options").isEmpty()) {
            findings.add("X-Content-Type-Options ausente (riesgo de MIME sniffing)");
        }
        boolean frameProtection = !result.header("x-frame-options").isEmpty()
                || result.header("content-security-policy").toLowerCase(Locale.ROOT)
                .contains("frame-ancestors");
        if (!frameProtection) findings.add("Sin protección contra clickjacking (X-Frame-Options/CSP)");
        if (result.header("content-security-policy").isEmpty()) {
            findings.add("Content-Security-Policy ausente");
        }
        if (result.header("referrer-policy").isEmpty()) {
            findings.add("Referrer-Policy ausente");
        }
        if (!result.tls.isEmpty() && result.header("strict-transport-security").isEmpty()) {
            findings.add("HSTS ausente sobre HTTPS");
        }
        if (!result.poweredBy.isEmpty()) {
            findings.add("X-Powered-By revela la tecnología del servidor");
        }
        if (result.server.matches(".*\\d.*")) {
            findings.add("El servidor revela su versión en la cabecera Server");
        }
        if (result.statusCode >= 500) {
            findings.add("El servidor devolvió un error " + result.statusCode);
        }
        if (result.location.toLowerCase(Locale.ROOT).startsWith("http://")) {
            findings.add("Redirección a HTTP sin cifrar");
        }
        return findings;
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

    private static String tlsDetailsFromSession(Socket socket) {
        if (socket instanceof SSLSocket) return tlsDetails((SSLSocket) socket);
        return "";
    }

    private static String tlsDetails(SSLSocket socket) {
        try {
            SSLSession session = socket.getSession();
            Certificate[] chain = session.getPeerCertificates();
            if (chain == null || chain.length == 0) return "";
            X509Certificate cert = (X509Certificate) chain[0];
            String subject = commonName(cert.getSubjectX500Principal().getName());
            String issuer = commonName(cert.getIssuerX500Principal().getName());
            long days = TimeUnit.MILLISECONDS.toDays(cert.getNotAfter().getTime() - System.currentTimeMillis());
            StringBuilder sb = new StringBuilder();
            sb.append(session.getProtocol()).append(" · ").append(session.getCipherSuite());
            if (!subject.isEmpty()) sb.append(" · CN=").append(subject);
            if (!issuer.isEmpty()) sb.append(" · emisor ").append(issuer);
            sb.append(" · ").append(days).append(" días");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
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
