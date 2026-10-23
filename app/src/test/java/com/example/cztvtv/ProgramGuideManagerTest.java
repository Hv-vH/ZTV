package com.example.cztvtv;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;
import static com.example.cztvtv.ProgramGuideTest.DAY;
import static com.example.cztvtv.ProgramGuideTest.HOUR;

public class ProgramGuideManagerTest {
    private final FakeClock clock = new FakeClock();
    private final WorkQueue worker = new WorkQueue();
    private final WorkQueue ui = new WorkQueue();
    private final FakeSource source = new FakeSource();
    private int changes;
    private ProgramGuideManager manager;

    @Before
    public void setUp() {
        manager = new ProgramGuideManager(source, worker, ui::execute, clock, () -> changes++);
    }

    @Test
    public void repeatedRequestsAreDeduplicatedWhileLoading() {
        manager.refresh("106", false);
        manager.refresh("106", true);
        assertEquals(2, worker.queue.size()); // One summary and one daily schedule.
        complete();
        assertEquals(1, source.summaryCalls);
        assertEquals(1, source.guideCalls);
        assertFalse(manager.state("106").loading);
    }

    @Test
    public void summariesRefreshEveryMinuteAndSchedulesEveryFiveMinutes() {
        manager.refresh("106", false);
        complete();
        clock.advance(60_000);
        manager.refresh("106", false);
        complete();
        assertEquals(2, source.summaryCalls);
        assertEquals(1, source.guideCalls);
        clock.advance(4 * 60_000);
        manager.refresh("106", false);
        complete();
        assertEquals(2, source.guideCalls);
    }

    @Test
    public void currentProgramChangesFromScheduleWithoutWaitingForNetwork() {
        manager.refresh("106", false);
        complete();
        assertEquals("106 上午节目", manager.currentTitle("106"));
        clock.advance(12 * HOUR);
        assertEquals("106 下午节目", manager.currentTitle("106"));
        assertEquals(1, source.guideCalls);
    }

    @Test
    public void manualOrResumeRefreshBypassesCacheButLimitsButtonSpam() {
        manager.refresh("106", false);
        complete();
        manager.refresh("106", true);
        assertTrue(worker.queue.isEmpty());
        clock.advance(2_000);
        manager.refresh("106", true);
        complete();
        assertEquals(2, source.summaryCalls);
        assertEquals(2, source.guideCalls);
    }

    @Test
    public void firstFailureStopsLoadingAndRetriesAfterThirtySeconds() {
        source.fail = true;
        manager.refresh("106", false);
        complete();
        assertTrue(manager.state("106").failed);
        assertFalse(manager.state("106").loading);
        assertFalse(manager.isSummaryLoading());
        clock.advance(29_999);
        manager.refresh("106", false);
        assertTrue(worker.queue.isEmpty());
        clock.advance(1);
        source.fail = false;
        manager.refresh("106", false);
        complete();
        assertFalse(manager.state("106").failed);
        assertNotNull(manager.state("106").guide);
    }

    @Test
    public void failedUpdatePreservesTodaysUsableSchedule() {
        manager.refresh("106", false);
        complete();
        ProgramGuide original = manager.state("106").guide;
        source.fail = true;
        clock.advance(5 * 60_000);
        manager.refresh("106", false);
        complete();
        assertSame(original, manager.state("106").guide);
        assertTrue(manager.state("106").failed);
        assertEquals("106 上午节目", manager.currentTitle("106"));
    }

    @Test
    public void oldSummaryExpiresIfRefreshingFails() {
        manager.refresh("106", false);
        complete();
        // Channel 107 has not loaded a full guide, so it uses the summary.
        assertEquals("摘要节目", manager.currentTitle("107"));
        clock.advance(2 * 60_000);
        assertNull(manager.currentTitle("107"));
    }

    @Test
    public void newSummaryCanReplaceAnUnrefreshedChannelsOldGuide() {
        manager.refresh("107", false);
        complete();
        assertEquals("107 上午节目", manager.currentTitle("107"));
        clock.advance(5 * 60_000);
        // The user is now watching a different channel; 107's guide is not fetched.
        manager.refresh("106", false);
        complete();
        assertEquals("摘要节目", manager.currentTitle("107"));
    }

    @Test
    public void midnightInvalidatesGuidesAndSummaryWithoutRestartingApp() {
        manager.refresh("106", false);
        complete();
        clock.advance(24 * HOUR);
        assertNull(manager.state("106").guide);
        assertNull(manager.currentTitle("107"));
        manager.refresh("106", false);
        complete();
        assertEquals("20261023", manager.state("106").guide.date);
    }

    @Test
    public void yesterdaysDelayedResponsesCannotReplaceTodaysPendingRequest() {
        manager.refresh("106", false);
        worker.runAll(); // Responses are waiting on the UI queue.
        clock.advance(24 * HOUR);
        manager.refresh("106", false);
        ui.runAll();
        assertNull(manager.state("106").guide);
        assertTrue(manager.state("106").loading);
        complete();
        assertEquals("20261023", manager.state("106").guide.date);
        assertFalse(manager.state("106").loading);
    }

    @Test
    public void channelResponsesStayWithTheirOwnChannel() {
        manager.refreshGuide("106", false);
        manager.refreshGuide("107", false);
        complete();
        assertEquals("106 上午节目", manager.currentTitle("106"));
        assertEquals("107 上午节目", manager.currentTitle("107"));
    }

    @Test
    public void closeDiscardsLateCallbacksAndPreventsNewRequests() {
        manager.refresh("106", false);
        worker.runAll();
        int beforeClose = changes;
        manager.close();
        ui.runAll();
        manager.refresh("106", true);
        assertEquals(beforeClose, changes);
        assertTrue(worker.queue.isEmpty());
        assertNull(manager.state("106").guide);
    }

    @Test
    public void updateTimerWakesAtTheNextProgramBoundary() {
        manager.refresh("106", false);
        complete();
        clock.advance(12 * HOUR - 1_000);
        assertEquals(1_000, manager.nextUpdateDelay());
        clock.advance(1_000);
        assertEquals(15_000, manager.nextUpdateDelay());
    }

    private void complete() {
        worker.runAll();
        ui.runAll();
    }

    private static final class FakeClock implements ProgramGuideManager.Clock {
        long wall = DAY;
        long elapsed;

        void advance(long millis) {
            wall += millis;
            elapsed += millis;
        }

        @Override
        public long wallTime() {
            return wall;
        }

        @Override
        public long elapsedTime() {
            return elapsed;
        }
    }

    private static final class WorkQueue implements Executor {
        final Queue<Runnable> queue = new ArrayDeque<>();

        @Override
        public void execute(Runnable runnable) {
            queue.add(runnable);
        }

        void runAll() {
            while (!queue.isEmpty()) {
                queue.remove().run();
            }
        }
    }

    private static final class FakeSource implements ProgramGuideManager.Source {
        int summaryCalls;
        int guideCalls;
        boolean fail;

        @Override
        public Map<String, String> loadCurrentPrograms() throws Exception {
            summaryCalls++;
            if (fail) {
                throw new IOException("network unavailable");
            }
            return Collections.singletonMap("107", "摘要节目");
        }

        @Override
        public ProgramGuide loadSchedule(String code, String date) throws Exception {
            guideCalls++;
            if (fail) {
                throw new IOException("network unavailable");
            }
            long start = "20261022".equals(date) ? DAY : DAY + 24 * HOUR;
            return new ProgramGuide(date, Arrays.asList(
                    new ProgramGuide.Program(code + " 上午节目", start, 12 * HOUR),
                    new ProgramGuide.Program(code + " 下午节目", start + 12 * HOUR, 12 * HOUR)
            ));
        }
    }
}
