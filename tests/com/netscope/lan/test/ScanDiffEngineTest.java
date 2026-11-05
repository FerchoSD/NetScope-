package com.netscope.lan.test;

import com.netscope.lan.core.diff.ScanDiffEngine;
import com.netscope.lan.core.model.Device;
import com.netscope.lan.core.model.PortResult;
import com.netscope.lan.core.model.ScanDiff;
import com.netscope.lan.core.model.ScanReport;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ScanDiffEngineTest {
    @Test
    public void nullInputsAreHandled() {
        assertTrue(ScanDiffEngine.compare(null, null).isEmpty());
        ScanReport current = new ScanReport();
        current.addOrGet("10.0.0.1");
        ScanDiff diff = ScanDiffEngine.compare(current, null);
        assertEquals(Collections.singletonList("10.0.0.1"), diff.addedIps);
    }

    @Test
    public void detectsAddedRemovedAndPortChanges() {
        ScanReport previous = new ScanReport();
        Device oldRouter = previous.addOrGet("192.168.1.1");
        oldRouter.addPortResult(open(80));
        oldRouter.addPortResult(open(22));
        previous.addOrGet("192.168.1.50");

        ScanReport current = new ScanReport();
        Device router = current.addOrGet("192.168.1.1");
        router.addPortResult(open(80));
        router.addPortResult(open(443));
        current.addOrGet("192.168.1.77");

        ScanDiff diff = ScanDiffEngine.compare(current, previous);
        assertEquals(Collections.singletonList("192.168.1.77"), diff.addedIps);
        assertEquals(Collections.singletonList("192.168.1.50"), diff.removedIps);
        assertEquals(1, diff.portChanges.size());
        ScanDiff.PortChange change = diff.portChanges.get(0);
        assertEquals("192.168.1.1", change.ip);
        assertEquals(Collections.singletonList(443), change.opened);
        assertEquals(Collections.singletonList(22), change.closed);
    }

    @Test
    public void noChangesMeansEmptyDiff() {
        ScanReport previous = new ScanReport();
        previous.addOrGet("10.0.0.2").addPortResult(open(80));
        ScanReport current = new ScanReport();
        current.addOrGet("10.0.0.2").addPortResult(open(80));

        ScanDiff diff = ScanDiffEngine.compare(current, previous);
        assertTrue(diff.isEmpty());
    }

    @Test
    public void portChangeListsAreSorted() {
        ScanReport previous = new ScanReport();
        previous.addOrGet("10.0.0.3").addPortResult(open(443));
        ScanReport current = new ScanReport();
        Device device = current.addOrGet("10.0.0.3");
        for (int port : new int[]{8080, 22, 3000}) device.addPortResult(open(port));

        ScanDiff diff = ScanDiffEngine.compare(current, previous);
        ScanDiff.PortChange change = diff.portChanges.get(0);
        assertEquals(Arrays.asList(22, 3000, 8080), change.opened);
        assertEquals(Collections.singletonList(443), change.closed);
    }

    private static PortResult open(int port) {
        return new PortResult(port, PortResult.STATE_OPEN, "", "", 1, 0, "");
    }
}
