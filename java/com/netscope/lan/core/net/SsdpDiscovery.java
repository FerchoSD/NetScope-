package com.netscope.lan.core.net;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SsdpDiscovery {
    public interface Listener {
        void onDevice(String ip, String name, String vendor, String model,
                      String deviceType, String location);
    }

    private static final String GROUP = "239.255.255.250";
    private final Context context;
    private final String localIp;
    private final Listener listener;
    private final ExecutorService fetcher = Executors.newFixedThreadPool(4);
    private final Set<String> fetched = Collections.synchronizedSet(new HashSet<String>());
    private volatile boolean running;
    private MulticastSocket socket;
    private WifiManager.MulticastLock lock;
    private Thread thread;

    public SsdpDiscovery(Context context, String localIp, Listener listener) {
        this.context = context.getApplicationContext();
        this.localIp = localIp;
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::run, "netscope-ssdp");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                lock = wm.createMulticastLock("netscope-ssdp");
                lock.setReferenceCounted(false);
                lock.acquire();
            }
            InetAddress group = InetAddress.getByName(GROUP);
            socket = new MulticastSocket(new InetSocketAddress(1900));
            socket.setReuseAddress(true);
            socket.setSoTimeout(1200);
            socket.joinGroup(group);
            String search = "M-SEARCH * HTTP/1.1\r\n"
                    + "HOST: 239.255.255.250:1900\r\n"
                    + "MAN: \"ssdp:discover\"\r\n"
                    + "MX: 2\r\n"
                    + "ST: ssdp:all\r\n\r\n";
            byte[] payload = search.getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i < 3 && running; i++) {
                socket.send(new DatagramPacket(payload, payload.length, group, 1900));
                try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            }
            long deadline = System.currentTimeMillis() + 5500;
            while (running && System.currentTimeMillis() < deadline) {
                DatagramPacket packet = new DatagramPacket(new byte[4096], 4096);
                try {
                    socket.receive(packet);
                } catch (java.net.SocketTimeoutException e) {
                    continue;
                }
                handle(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        } finally {
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            if (lock != null && lock.isHeld()) {
                try { lock.release(); } catch (Exception ignored) {}
            }
        }
    }

    private void handle(String response) {
        Map<String, String> headers = new HashMap<>();
        String[] lines = response.split("\r?\n");
        for (String line : lines) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(),
                        line.substring(colon + 1).trim());
            }
        }
        final String location = headers.get("location");
        final String server = headers.get("server");
        final String st = headers.containsKey("st") ? headers.get("st") : headers.get("nt");
        if (location == null) return;
        String ip;
        try {
            ip = URI.create(location).getHost();
        } catch (Exception e) {
            return;
        }
        if (ip == null || ip.equals(localIp) || !SubnetMath.isPrivate(ip)) return;
        listener.onDevice(ip, null, server, null, st, location);
        if (fetched.add(location)) {
            fetcher.execute(() -> fetchDescription(location, ip));
        }
    }

    private void fetchDescription(String location, String ip) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(location);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2500);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "NetScope/1.0");
            if (connection.getResponseCode() != 200) return;
            InputStream input = connection.getInputStream();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) > 0 && buffer.size() < 65536) {
                buffer.write(chunk, 0, read);
            }
            input.close();
            String xml = buffer.toString("UTF-8");
            String name = tag(xml, "friendlyName");
            String manufacturer = tag(xml, "manufacturer");
            String model = tag(xml, "modelName");
            String type = tag(xml, "deviceType");
            if (name != null || manufacturer != null || model != null) {
                listener.onDevice(ip, name, manufacturer, model, type, location);
            }
        } catch (Exception ignored) {
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String tag(String xml, String name) {
        Pattern pattern = Pattern.compile("<" + name + "[^>]*>(.*?)</" + name + ">",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        if (!matcher.find()) return null;
        String value = matcher.group(1).trim();
        value = value.replaceAll("<[^>]+>", "").trim();
        if (value.isEmpty()) return null;
        return value.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'");
    }

    public void stop() {
        running = false;
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        fetcher.shutdownNow();
    }
}
