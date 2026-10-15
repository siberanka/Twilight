package com.siberanka.twilight.integration.display;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DisplayTimelineTest {
    private static final long SECOND = 1_000_000_000L;

    @Test void initialPoseAppearsImmediatelyAndInterruptionStartsAtCurrentPose() {
        DisplayTimeline timeline = new DisplayTimeline();
        timeline.update(at(5), 20, 0, 0);
        assertEquals(5, timeline.sample(0).value(0));
        timeline.update(at(15), null, 0, SECOND);
        assertEquals(10, timeline.sample(SECOND + SECOND / 2).value(0));
        var interrupted = timeline.update(at(0), null, 0, SECOND + SECOND / 2);
        assertEquals(10, interrupted.from().value(0));
        assertEquals(5, timeline.sample(2 * SECOND).value(0));
    }

    @Test void poseUpdateWithoutStartMetadataDoesNotRestartClock() {
        DisplayTimeline timeline = new DisplayTimeline();
        timeline.update(at(0), 20, 0, 0);
        timeline.update(at(10), null, 0, 0);
        var update = timeline.update(at(20), null, null, SECOND / 2);
        assertEquals(-.5f, update.delay());
        assertEquals(5, update.from().value(0));
        assertEquals(12.5f, timeline.sample(SECOND / 2).value(0));
        assertEquals(20, timeline.sample(SECOND).value(0));
        timeline.update(at(30), null, null, 2 * SECOND);
        assertEquals(30, timeline.sample(2 * SECOND).value(0));
    }

    @Test void delayAndDurationOnlyUpdatesPreserveEndpointsIncludingNegativeDelay() {
        DisplayTimeline timeline = new DisplayTimeline();
        timeline.update(at(0), 20, 0, 0);
        timeline.update(at(10), null, 10, 0);
        assertEquals(0, timeline.sample(SECOND / 4).value(0));
        assertEquals(5, timeline.sample(SECOND).value(0));
        timeline.update(null, 40, null, SECOND);
        assertEquals(2.5f, timeline.sample(SECOND).value(0));
        timeline.update(null, null, -20, SECOND);
        assertEquals(5, timeline.sample(SECOND).value(0));
        timeline.update(null, 0, null, SECOND);
        assertEquals(10, timeline.sample(SECOND).value(0));
    }

    @Test void aNewPoseWithoutAnyStartMetadataIsAlreadyComplete() {
        DisplayTimeline timeline = new DisplayTimeline();
        timeline.update(at(0), 20, null, 0);
        var state = timeline.update(at(10), null, null, SECOND);
        assertEquals(-1f, state.delay());
        assertEquals(10, timeline.sample(SECOND).value(0));
    }

    private static DisplayPose at(float x) {
        float[] values = DisplayPose.identity().values();
        values[0] = x;
        return new DisplayPose(values);
    }
}
