package com.netscope.lan.core.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public final class BannerGrabber {
    private BannerGrabber() {}

    public static String grab(String ip, int port, int timeoutMs) {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(ip, port), Math.max(250, timeoutMs));
            socket.setSoTimeout(Math.max(300, timeoutMs));
            if (ServiceCatalog.isHttp(port) && port != 443 && port != 8443
                    && port != 9443 && port != 5001) {
                String request = "HEAD / HTTP/1.0\r\nHost: " + ip
                        + "\r\nUser-Agent: NetScope/1.0\r\nConnection: close\r\n\r\n";
                OutputStream out = socket.getOutputStream();
                out.write(request.getBytes(StandardCharsets.US_ASCII));
                out.flush();
            }
            InputStream in = socket.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[256];
            long deadline = System.currentTimeMillis() + Math.max(400, timeoutMs);
            while (buffer.size() < 1024 && System.currentTimeMillis() < deadline) {
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
            return clean(buffer.toString("UTF-8"), ServiceCatalog.isHttp(port));
        } catch (IOException e) {
            return "";
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static String clean(String raw, boolean http) {
        if (raw == null || raw.isEmpty()) return "";
        String text = raw.replace("\r", "").replace('\0', ' ').trim();
        if (http) {
            String server = header(text, "Server:");
            String first = text.split("\n", 2)[0];
            if (server != null) return first + " · " + server;
            return first;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\t') sb.append(' ');
            else if (c >= 32) sb.append(c);
        }
        String result = sb.toString().replaceAll("\\s+", " ").trim();
        if (result.length() > 140) result = result.substring(0, 140) + "…";
        return result;
    }

    private static String header(String text, String name) {
        String lower = text.toLowerCase();
        int idx = lower.indexOf(name.toLowerCase());
        if (idx < 0) return null;
        int end = text.indexOf('\n', idx);
        if (end < 0) end = text.length();
        return text.substring(idx, end).trim();
    }
}
