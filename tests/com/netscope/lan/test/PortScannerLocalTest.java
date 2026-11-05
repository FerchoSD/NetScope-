package com.netscope.lan.test;

import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.net.PortScanner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PortScannerLocalTest {
    private ServerSocket server;
    private int openPort;

    @Before
    public void startServer() throws Exception {
        server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        openPort = server.getLocalPort();
    }

    @After
    public void stopServer() throws Exception {
        if (server != null) server.close();
    }

    @Test
    public void detectsOpenLocalPort() {
        PortScanner scanner = new PortScanner();
        List<PortResult> open = scanner.scan("127.0.0.1", new int[]{openPort}, 500, 2, false, null);
        assertEquals(1, open.size());
        assertEquals(openPort, open.get(0).port);
        assertEquals(PortResult.STATE_OPEN, open.get(0).state);
    }

    @Test
    public void ignoresClosedAndNotifiesProgress() {
        PortScanner scanner = new PortScanner();
        final AtomicInteger progress = new AtomicInteger();
        final AtomicInteger opens = new AtomicInteger();
        int[] ports = new int[]{openPort, 1, 2};
        List<PortResult> open = scanner.scan("127.0.0.1", ports, 500, 3, false,
                new PortScanner.Listener() {
                    @Override public void onOpen(PortResult result) { opens.incrementAndGet(); }
                    @Override public void onProgress(int done, int total) {
                        if (done == total) progress.set(total);
                    }
                });
        assertEquals(1, open.size());
        assertEquals(openPort, open.get(0).port);
        assertEquals(1, opens.get());
        assertEquals(3, progress.get());
    }

    @Test
    public void cancelStopsScan() {
        PortScanner scanner = new PortScanner();
        scanner.cancel();
        List<PortResult> open = scanner.scan("127.0.0.1", new int[]{openPort}, 500, 2, false, null);
        assertTrue(open.isEmpty());
    }
}
