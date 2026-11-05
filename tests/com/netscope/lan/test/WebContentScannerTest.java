package com.netscope.lan.test;

import com.netscope.lan.core.net.WebContentScanner;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebContentScannerTest {
    @Test
    public void wordlistIsClean() {
        String[] paths = WebContentScanner.wordlist();
        assertTrue(paths.length >= 80);
        Set<String> seen = new HashSet<>();
        for (String path : paths) {
            assertFalse(path.startsWith("/"));
            assertFalse(path.contains(" "));
            assertTrue(path.length() > 0);
            assertTrue("duplicada: " + path, seen.add(path));
        }
        assertTrue(seen.contains("admin"));
        assertTrue(seen.contains(".env"));
        assertTrue(seen.contains("wp-login.php"));
        assertTrue(seen.contains("robots.txt"));
    }

    @Test
    public void onlyPredictableContentIsReported() {
        assertTrue(WebContentScanner.isInteresting(200));
        assertTrue(WebContentScanner.isInteresting(204));
        assertTrue(WebContentScanner.isInteresting(301));
        assertTrue(WebContentScanner.isInteresting(302));
        assertTrue(WebContentScanner.isInteresting(307));
        assertTrue(WebContentScanner.isInteresting(401));
        assertTrue(WebContentScanner.isInteresting(403));
        assertFalse(WebContentScanner.isInteresting(404));
        assertFalse(WebContentScanner.isInteresting(400));
        assertFalse(WebContentScanner.isInteresting(500));
        assertFalse(WebContentScanner.isInteresting(-1));
    }

    @Test
    public void parsesStatusCode() {
        assertEquals(403, WebContentScanner.parseStatus("HTTP/1.1 403 Forbidden\r\nServer: x\r\n\r\n"));
        assertEquals(200, WebContentScanner.parseStatus("HTTP/1.0 200 OK\r\n\r\n"));
        assertEquals(-1, WebContentScanner.parseStatus("no http"));
        assertEquals(-1, WebContentScanner.parseStatus(null));
    }
}
