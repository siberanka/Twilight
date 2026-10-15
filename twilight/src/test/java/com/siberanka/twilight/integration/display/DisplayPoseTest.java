package com.siberanka.twilight.integration.display;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplayPoseTest {
    @Test void interpolatesTranslationAndNonUniformScaleWithoutLosingRightRotation() {
        float[] end = DisplayPose.identity().values();
        end[0] = 12; end[1] = -4; end[3] = 3; end[4] = -1;
        end[10] = 1; end[13] = 0;
        DisplayPose middle = DisplayPose.identity().interpolate(new DisplayPose(end), .5);
        assertEquals(6, middle.value(0)); assertEquals(-2, middle.value(1));
        assertEquals(2, middle.value(3)); assertEquals(0, middle.value(4));
        assertEquals(Math.sqrt(.5), middle.value(10), 1e-6);
        assertEquals(Math.sqrt(.5), middle.value(13), 1e-6);
    }

    @Test void equivalentQuaternionSignsDoNotIntroduceFullRotationOrNaN() {
        float[] end = DisplayPose.identity().values(); end[9] = -1; end[13] = -1;
        DisplayPose target = new DisplayPose(end);
        for (int i = 0; i <= 100; i++) {
            DisplayPose pose = DisplayPose.identity().interpolate(target, i / 100.0);
            assertEquals(1, pose.value(9), 1e-6); assertEquals(1, pose.value(13), 1e-6);
        }
    }

    @Test void slerpUsesConstantAngularSpeedAndShortestArcAcross180Degrees() {
        float[] a = DisplayPose.identity().values(), b = a.clone();
        a[7] = (float) Math.sin(Math.toRadians(170 / 2.0)); a[9] = (float) Math.cos(Math.toRadians(170 / 2.0));
        b[7] = (float) Math.sin(Math.toRadians(-170 / 2.0)); b[9] = (float) Math.cos(Math.toRadians(-170 / 2.0));
        DisplayPose quarter = new DisplayPose(a).interpolate(new DisplayPose(b), .25);
        double angle = Math.toDegrees(2 * Math.atan2(quarter.value(7), quarter.value(9)));
        assertEquals(175, angle, 1e-4);
    }

    @Test void clampsTimeAndProtectsStateFromMutablePacketVectors() {
        float[] input = DisplayPose.identity().values(); input[0] = 8;
        DisplayPose end = new DisplayPose(input); input[0] = 99;
        assertEquals(8, end.value(0)); end.values()[0] = 100;
        assertEquals(8, end.value(0));
        assertEquals(0, DisplayPose.identity().interpolate(end, -2).value(0));
        assertEquals(8, DisplayPose.identity().interpolate(end, 2).value(0));
    }

    @Test void rejectsNonFiniteAndDegeneratePacketTransforms() {
        float[] bad = DisplayPose.identity().values(); bad[9] = 0;
        assertThrows(IllegalArgumentException.class, () -> new DisplayPose(bad));
        bad[9] = 1; bad[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> new DisplayPose(bad));
    }
}
