package com.netscope.lan.core.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Cliente mínimo del protocolo ADB sobre TCP: CNXN, AUTH (firma RSA),
 * OPEN de un servicio shell y lectura de WRTE. No empaqueta binarios:
 * ejecuta los comandos disponibles para el usuario shell de adbd.
 */
public final class AdbClient {
    public interface Listener {
        void onOutput(String text);
    }

    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_WRTE = 0x45545257;
    private static final int A_CLSE = 0x45534c43;

    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_RSAPUBLICKEY = 3;

    private static final int VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 4096 * 16;
    private static final int READ_TIMEOUT_MS = 300000;
    private static final byte[] EMPTY = new byte[0];

    private volatile Socket socket;

    public void cancel() {
        Socket current = socket;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
            }
        }
    }

    public void shell(Context context, String host, int port, String command,
                      int authTimeoutMs, Listener listener) throws Exception {
        String cmd = command == null ? "" : command.trim();
        if (cmd.isEmpty()) throw new IllegalArgumentException("comando vacío");

        Socket connection = new Socket();
        socket = connection;
        try {
            connection.connect(new InetSocketAddress(host, port), 5000);
            connection.setTcpNoDelay(true);
            InputStream in = connection.getInputStream();
            OutputStream out = connection.getOutputStream();

            send(out, A_CNXN, VERSION, MAX_PAYLOAD,
                    "host::NetScope\0".getBytes(StandardCharsets.UTF_8));
            awaitConnection(context, connection, in, out, authTimeoutMs);

            connection.setSoTimeout(READ_TIMEOUT_MS);
            int localId = 1;
            send(out, A_OPEN, localId, 0,
                    ("shell:" + cmd + "\0").getBytes(StandardCharsets.UTF_8));

            int remoteId = -1;
            while (remoteId < 0) {
                int[] header = readHeader(in);
                byte[] data = readFully(in, header[3]);
                if (header[0] == A_OKAY && header[2] == localId) {
                    remoteId = header[1];
                } else if (header[0] == A_CLSE && header[2] == localId) {
                    throw new IOException(serviceError(data));
                }
            }

            while (true) {
                int[] header = readHeader(in);
                byte[] data = readFully(in, header[3]);
                if (header[0] == A_WRTE && header[2] == localId) {
                    if (data.length > 0 && listener != null) {
                        listener.onOutput(new String(data, StandardCharsets.UTF_8));
                    }
                    send(out, A_OKAY, localId, remoteId, EMPTY);
                } else if (header[0] == A_CLSE && header[2] == localId) {
                    send(out, A_CLSE, localId, remoteId, EMPTY);
                    break;
                }
            }
        } finally {
            socket = null;
            try {
                connection.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void awaitConnection(Context context, Socket connection, InputStream in, OutputStream out,
                                 int authTimeoutMs) throws Exception {
        boolean connected = false;
        boolean signed = false;
        long deadline = System.currentTimeMillis() + Math.max(10000, authTimeoutMs);
        while (!connected) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                throw new IOException("Sin autorización: acepta la clave RSA de NetScope en el dispositivo.");
            }
            connection.setSoTimeout((int) Math.min(remaining, 30000));
            int[] header;
            try {
                header = readHeader(in);
            } catch (SocketTimeoutException e) {
                continue;
            }
            byte[] data = readFully(in, header[3]);
            if (header[0] == A_CNXN) {
                connected = true;
            } else if (header[0] == A_AUTH) {
                if (header[1] == AUTH_TOKEN) {
                    if (!signed) {
                        send(out, A_AUTH, AUTH_SIGNATURE, 0, sign(context, data));
                        signed = true;
                    } else {
                        send(out, A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKey(context));
                    }
                } else {
                    send(out, A_AUTH, AUTH_RSAPUBLICKEY, 0, publicKey(context));
                }
            } else if (header[0] == A_CLSE) {
                throw new IOException("adbd rechazó la conexión");
            }
        }
    }

    private static String serviceError(byte[] data) {
        if (data != null && data.length > 0) {
            String text = new String(data, StandardCharsets.UTF_8).replace("\0", "").trim();
            if (!text.isEmpty()) return text;
        }
        return "El dispositivo cerró el servicio shell";
    }

    private static void send(OutputStream out, int command, int arg0, int arg1, byte[] data)
            throws IOException {
        ByteBuffer packet = ByteBuffer.allocate(24 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        packet.putInt(command);
        packet.putInt(arg0);
        packet.putInt(arg1);
        packet.putInt(data.length);
        packet.putInt(checksum(data));
        packet.putInt(command ^ 0xFFFFFFFF);
        packet.put(data);
        out.write(packet.array());
        out.flush();
    }

    private static int[] readHeader(InputStream in) throws IOException {
        byte[] raw = readFully(in, 24);
        ByteBuffer header = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int command = header.getInt();
        int arg0 = header.getInt();
        int arg1 = header.getInt();
        int length = header.getInt();
        header.getInt();
        int magic = header.getInt();
        if ((command ^ 0xFFFFFFFF) != magic) throw new IOException("Trama ADB corrupta");
        if (length < 0 || length > 1024 * 1024) throw new IOException("Longitud ADB no válida");
        return new int[]{command, arg0, arg1, length};
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read < 0) throw new IOException("Conexión ADB cerrada");
            if (read == 0) continue;
            offset += read;
        }
        return buffer;
    }

    private static int checksum(byte[] data) {
        int sum = 0;
        for (byte value : data) sum += value & 0xFF;
        return sum;
    }

    private static byte[] sign(Context context, byte[] token) throws GeneralSecurityException {
        Signature signer = Signature.getInstance("SHA1withRSA");
        signer.initSign(keyPair(context).getPrivate());
        signer.update(token);
        return signer.sign();
    }

    private static byte[] publicKey(Context context) throws GeneralSecurityException {
        KeyPair pair = keyPair(context);
        RSAPublicKey key = (RSAPublicKey) pair.getPublic();
        BigInteger modulus = key.getModulus();
        int words = (modulus.bitLength() + 31) / 32;
        int size = words * 4;
        BigInteger r32 = BigInteger.ONE.shiftLeft(32);
        BigInteger n0inv = r32.subtract(modulus.modInverse(r32)).mod(r32);
        BigInteger r = BigInteger.ONE.shiftLeft(size * 8);
        BigInteger rr = r.multiply(r).mod(modulus);

        ByteBuffer blob = ByteBuffer.allocate(8 + size * 2 + 4).order(ByteOrder.LITTLE_ENDIAN);
        blob.putInt(words);
        blob.putInt(n0inv.intValue());
        blob.put(toLittleEndian(modulus, size));
        blob.put(toLittleEndian(rr, size));
        blob.putInt(key.getPublicExponent().intValue());
        String encoded = Base64.encodeToString(blob.array(), Base64.NO_WRAP);
        return (encoded + " NetScope@android").getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] toLittleEndian(BigInteger value, int size) {
        byte[] out = new byte[size];
        byte[] bigEndian = value.toByteArray();
        int copy = Math.min(bigEndian.length, size);
        for (int i = 0; i < copy; i++) {
            out[i] = bigEndian[bigEndian.length - 1 - i];
        }
        return out;
    }

    private static synchronized KeyPair keyPair(Context context) throws GeneralSecurityException {
        SharedPreferences prefs = context.getSharedPreferences("netscope_adb", Context.MODE_PRIVATE);
        String encodedPrivate = prefs.getString("private", "");
        String encodedPublic = prefs.getString("public", "");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (!encodedPrivate.isEmpty() && !encodedPublic.isEmpty()) {
            try {
                PrivateKey privateKey = factory.generatePrivate(
                        new PKCS8EncodedKeySpec(Base64.decode(encodedPrivate, Base64.NO_WRAP)));
                PublicKey publicKey = factory.generatePublic(
                        new X509EncodedKeySpec(Base64.decode(encodedPublic, Base64.NO_WRAP)));
                return new KeyPair(publicKey, privateKey);
            } catch (Exception ignored) {
                // Clave ilegible: se genera una nueva.
            }
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        prefs.edit()
                .putString("private", Base64.encodeToString(pair.getPrivate().getEncoded(), Base64.NO_WRAP))
                .putString("public", Base64.encodeToString(pair.getPublic().getEncoded(), Base64.NO_WRAP))
                .apply();
        return pair;
    }
}
