package com.netscope.lan.core.net;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

public final class NetbiosResolver {
    private NetbiosResolver() {}

    public static String query(String ip, int timeoutMs) {
        DatagramSocket socket = null;
        try {
            byte[] request = buildRequest();
            socket = new DatagramSocket();
            socket.connect(new InetSocketAddress(InetAddress.getByName(ip), 137));
            socket.setSoTimeout(timeoutMs);
            socket.send(new DatagramPacket(request, request.length));
            byte[] response = new byte[1024];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            return parse(packet.getData(), packet.getLength());
        } catch (SocketTimeoutException e) {
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (socket != null) socket.close();
        }
    }

    private static byte[] buildRequest() {
        byte[] packet = new byte[50];
        packet[0] = 0x12;
        packet[1] = 0x34;
        packet[2] = 0x00;
        packet[3] = 0x00;
        packet[4] = 0x00;
        packet[5] = 0x01;
        int i = 12;
        packet[i++] = 0x20;
        packet[i++] = 'C';
        packet[i++] = 'K';
        for (int n = 0; n < 30; n++) packet[i++] = 'A';
        packet[i++] = 0x00;
        packet[i++] = 0x00;
        packet[i++] = 0x21;
        packet[i++] = 0x00;
        packet[i++] = 0x01;
        return packet;
    }

    private static String parse(byte[] data, int length) {
        if (length < 12) return null;
        int questions = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
        int answers = ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        int offset = 12;
        for (int q = 0; q < questions; q++) {
            while (offset < length) {
                int len = data[offset++] & 0xFF;
                if (len == 0) break;
                offset += len;
            }
            offset += 4;
        }
        for (int a = 0; a < answers && offset + 10 < length; a++) {
            if ((data[offset] & 0xC0) == 0xC0) {
                offset += 2;
            } else {
                while (offset < length) {
                    int len = data[offset++] & 0xFF;
                    if (len == 0) break;
                    offset += len;
                }
            }
            if (offset + 10 >= length) return null;
            offset += 2; // type
            offset += 2; // class
            offset += 4; // ttl
            int rdLength = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            offset += 2;
            if (offset + rdLength > length || rdLength < 1) return null;
            int numNames = data[offset] & 0xFF;
            int cursor = offset + 1;
            String fallback = null;
            for (int n = 0; n < numNames && cursor + 18 <= offset + rdLength; n++) {
                StringBuilder name = new StringBuilder();
                for (int c = 0; c < 15; c++) {
                    int value = data[cursor + c] & 0xFF;
                    if (value == 0x20 || value == 0x00) break;
                    if (value >= 32 && value < 127) name.append((char) value);
                }
                int flags = ((data[cursor + 16] & 0xFF) << 8) | (data[cursor + 17] & 0xFF);
                String value = name.toString().trim();
                if (value.length() > 0 && !value.equals("*")) {
                    boolean group = (flags & 0x8000) != 0;
                    if (!group) return value;
                    if (fallback == null) fallback = value;
                }
                cursor += 18;
            }
            if (fallback != null) return fallback;
            offset += rdLength;
        }
        return null;
    }
}
