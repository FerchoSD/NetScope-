package com.netscope.lan.test;

import com.netscope.lan.core.net.HttpAudit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HttpAuditTest {
    @Test
    public void parsesStatusHeadersAndTitle() {
        String raw = "HTTP/1.1 200 OK\r\n"
                + "Server: nginx/1.24.0\r\n"
                + "X-Powered-By: PHP/8.1.2\r\n"
                + "Content-Type: text/html; charset=utf-8\r\n"
                + "Set-Cookie: PHPSESSID=abc; path=/\r\n"
                + "\r\n"
                + "<html><head><title>Panel del router</title></head><body>wp-content</body></html>";
        HttpAudit.Result result = HttpAudit.parse(raw);
        assertTrue(result.reachable);
        assertEquals(200, result.statusCode);
        assertEquals("nginx/1.24.0", result.server);
        assertEquals("PHP/8.1.2", result.poweredBy);
        assertEquals("Panel del router", result.title);
        assertTrue(result.technologies.contains("nginx"));
        assertTrue(result.technologies.contains("PHP"));
        assertTrue(result.technologies.contains("WordPress"));
        assertTrue(result.findings.toString().contains("X-Content-Type-Options"));
        assertTrue(result.findings.toString().contains("Content-Security-Policy"));
        assertTrue(result.summary().contains("HTTP/1.1 200 OK"));
    }

    @Test
    public void recognizesSecurityHeaders() {
        String raw = "HTTP/1.1 204 No Content\r\n"
                + "Server: Apache\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "X-Frame-Options: DENY\r\n"
                + "Content-Security-Policy: default-src 'self'\r\n"
                + "Referrer-Policy: no-referrer\r\n"
                + "\r\n";
        HttpAudit.Result result = HttpAudit.parse(raw);
        assertTrue(result.reachable);
        assertEquals(204, result.statusCode);
        assertFalse(result.findings.contains("Content-Security-Policy ausente"));
        assertFalse(result.findings.contains("Referrer-Policy ausente"));
        assertFalse(result.findings.toString().contains("clickjacking"));
    }

    @Test
    public void flagsInsecureRedirectAndServerVersion() {
        String raw = "HTTP/1.1 302 Found\r\n"
                + "Server: Apache/2.4.52\r\n"
                + "Location: http://example.local/login\r\n"
                + "\r\n";
        HttpAudit.Result result = HttpAudit.parse(raw);
        assertEquals(302, result.statusCode);
        assertTrue(result.findings.toString().contains("HTTP sin cifrar"));
        assertTrue(result.findings.toString().contains("versión"));
    }

    @Test
    public void rejectsNonHttpPayload() {
        HttpAudit.Result result = HttpAudit.parse("SSH-2.0-OpenSSH_9.0\r\n");
        assertFalse(result.reachable);
        assertEquals(-1, result.statusCode);
        assertTrue(result.summary().contains("Sin respuesta HTTP"));
    }

    @Test
    public void compactSummaryMentionsStatus() {
        HttpAudit.Result result = HttpAudit.parse("HTTP/1.0 200 OK\r\nServer: Caddy\r\n\r\n");
        String compact = result.compact();
        assertTrue(compact.contains("HTTP/1.0 200 OK"));
        assertTrue(compact.contains("Caddy"));
    }
}
