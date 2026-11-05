package com.netscope.lan.test;

import com.netscope.lan.core.net.DistanceEstimator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DistanceEstimatorTest {
    @Test
    public void estimatesReferenceDistanceAtOneMeter() {
        assertEquals(1.0, DistanceEstimator.rssiToMeters(-40, 2412), 0.02);
        assertEquals(1.0, DistanceEstimator.rssiToMeters(-46, 5180), 0.02);
        assertEquals(1.0, DistanceEstimator.rssiToMeters(-47, 5955), 0.02);
    }

    @Test
    public void growsWithWeakerSignal() {
        double near = DistanceEstimator.rssiToMeters(-50, 2412);
        double far = DistanceEstimator.rssiToMeters(-80, 2412);
        assertTrue(far > near);
        assertEquals(12.9, DistanceEstimator.rssiToMeters(-70, 2412), 0.5);
    }

    @Test
    public void rejectsInvalidRssi() {
        assertEquals(-1.0, DistanceEstimator.rssiToMeters(-100, 2412), 0.0);
        assertEquals(-1.0, DistanceEstimator.rssiToMeters(0, 2412), 0.0);
    }

    @Test
    public void labelsAreMarkedAsEstimates() {
        assertTrue(DistanceEstimator.distanceLabel(-50, 2412).contains("estimado"));
        assertEquals("—", DistanceEstimator.distanceLabel(5, 2412));
        assertTrue(DistanceEstimator.distanceLabel(-95, 2412).contains("m (estimado)"));
    }

    @Test
    public void classifiesRttProximity() {
        assertEquals("—", DistanceEstimator.proximityLabel(-1));
        assertTrue(DistanceEstimator.proximityLabel(1).contains("Muy cerca"));
        assertTrue(DistanceEstimator.proximityLabel(8).contains("Cerca"));
        assertTrue(DistanceEstimator.proximityLabel(30).contains("media"));
        assertTrue(DistanceEstimator.proximityLabel(120).contains("Lejos"));
    }
}
