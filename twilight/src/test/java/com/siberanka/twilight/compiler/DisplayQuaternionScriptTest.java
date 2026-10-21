package com.siberanka.twilight.compiler;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DisplayQuaternionScriptTest {
    @Test void emittedScriptPreservesRotationsAtBothGimbalPolesAndAcrossRandomOrientations() {
        Random random = new Random(49187);
        for (int sample = 0; sample < 400; sample++) {
            double x = random.nextDouble(-180, 180), z = random.nextDouble(-180, 180);
            double y = sample < 100 ? 90 : sample < 200 ? -90 : random.nextDouble(-89.9, 89.9);
            double[] quaternion = eulerQuaternion(x, y, z);
            MolangMath math = run(quaternion, quaternion, 1);
            double[] reconstructed = eulerQuaternion(math.variables.get("v.lex"), math.variables.get("v.ley"), math.variables.get("v.lez"));
            assertSameRotation(quaternion, reconstructed, 1e-7);
        }
    }

    @Test void emittedSlerpUsesShortestArcAndHandlesEquivalentQuaternionSigns() {
        double[] a = eulerQuaternion(0, 170, 0), b = eulerQuaternion(0, -170, 0);
        MolangMath quarter = run(a, b, .25);
        assertSameRotation(eulerQuaternion(0, 175, 0), result(quarter), 1e-10);
        for (int i = 0; i < 4; i++) b[i] = -a[i];
        assertSameRotation(a, result(run(a, b, .5)), 1e-10);
    }

    private static MolangMath run(double[] a, double[] b, double progress) {
        MolangMath math = new MolangMath();
        for (int i = 0; i < 4; i++) {
            math.variables.put("v.a" + (i + 6), a[i]);
            math.variables.put("v.b" + (i + 6), b[i]);
        }
        math.variables.put("v.p", progress);
        math.run(DisplayEntityResources.quaternionScript("l", 6), Map.of());
        return math;
    }

    private static double[] result(MolangMath math) {
        return new double[]{math.variables.get("v.lx"), math.variables.get("v.ly"), math.variables.get("v.lz"), math.variables.get("v.lw")};
    }

    // Independent quaternion composition: Z * Y * X, matching the nested rig.
    private static double[] eulerQuaternion(double x, double y, double z) {
        double[] result = {0, 0, 0, 1};
        double[] degrees = {x, y, z};
        for (int axis = 0; axis < 3; axis++) {
            double[] q = {0, 0, 0, Math.cos(Math.toRadians(degrees[axis]) / 2)};
            q[axis] = Math.sin(Math.toRadians(degrees[axis]) / 2);
            double[] r = result;
            result = new double[]{q[3]*r[0]+q[0]*r[3]+q[1]*r[2]-q[2]*r[1],
                    q[3]*r[1]-q[0]*r[2]+q[1]*r[3]+q[2]*r[0],
                    q[3]*r[2]+q[0]*r[1]-q[1]*r[0]+q[2]*r[3],
                    q[3]*r[3]-q[0]*r[0]-q[1]*r[1]-q[2]*r[2]};
        }
        return result;
    }

    private static void assertSameRotation(double[] expected, double[] actual, double tolerance) {
        double dot = 0;
        for (int i = 0; i < 4; i++) dot += expected[i] * actual[i];
        assertEquals(1, Math.abs(dot), tolerance, "Quaternion rotation differs");
    }
}
