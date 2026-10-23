package com.example.cztvtv;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

import static org.junit.Assert.*;

public class ProgramGuideTest {
    static final long DAY = 1792598400000L; // 2026-10-22 00:00 in Beijing.
    static final long HOUR = 3_600_000L;

    @Test
    public void programBoundariesAreHalfOpen() {
        ProgramGuide guide = guide(
                program("节目一", 0, HOUR), program("节目二", HOUR, HOUR));
        assertEquals(0, guide.currentIndex(DAY));
        assertEquals(0, guide.currentIndex(DAY + HOUR - 1));
        assertEquals(1, guide.currentIndex(DAY + HOUR));
        assertEquals(-1, guide.currentIndex(DAY + 2 * HOUR));
    }

    @Test
    public void gapsDoNotPretendAnOldProgramIsStillPlaying() {
        ProgramGuide guide = guide(
                program("节目一", 0, HOUR), program("节目二", 2 * HOUR, HOUR));
        assertEquals(-1, guide.currentIndex(DAY + HOUR));
        assertEquals(1, guide.suggestedIndex(DAY + HOUR));
    }

    @Test
    public void overlappingDurationsYieldToTheNextProgram() {
        ProgramGuide guide = guide(
                program("旧节目", 0, 2 * HOUR), program("新节目", HOUR, HOUR));
        assertEquals(DAY + HOUR, guide.endTime(0));
        assertEquals(1, guide.currentIndex(DAY + HOUR));
    }

    @Test
    public void missingDurationUsesNextStartOrMidnight() {
        ProgramGuide guide = guide(
                program("节目一", 0, 0), program("节目二", HOUR, -1));
        assertEquals(DAY + HOUR, guide.endTime(0));
        assertEquals(DAY + 24 * HOUR, guide.endTime(1));
    }

    @Test
    public void sortsDeduplicatesAndRejectsInvalidOrWrongDayPrograms() {
        ProgramGuide guide = guide(
                program("长标题完整保留，不因显示当前节目而截断", HOUR, HOUR),
                program("节目一", 0, HOUR),
                program("节目一", 0, HOUR),
                program("  ", 2 * HOUR, HOUR),
                program("明天", 24 * HOUR, HOUR),
                new ProgramGuide.Program("坏时间", -1, HOUR));
        assertEquals(2, guide.programs.size());
        assertEquals("节目一", guide.programs.get(0).title);
        assertEquals("长标题完整保留，不因显示当前节目而截断", guide.programs.get(1).title);
    }

    @Test
    public void expiredDayNeverHasACurrentProgramEvenIfLastDurationCrossesMidnight() {
        ProgramGuide guide = guide(program("深夜节目", 23 * HOUR, 2 * HOUR));
        assertEquals(0, guide.currentIndex(DAY + 24 * HOUR - 1));
        assertEquals(-1, guide.currentIndex(DAY + 24 * HOUR));
    }

    @Test
    public void broadcasterDateAndTimesIgnoreDeviceTimeZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
            assertEquals("20261022", ProgramGuide.dateAt(DAY));
            assertEquals("00:00", ProgramGuide.displayTime(DAY));
            assertEquals("2026年10月22日", ProgramGuide.displayDate(DAY));
            assertEquals("20261023", ProgramGuide.dateAt(DAY + 24 * HOUR));
            assertEquals(DAY + 24 * HOUR, ProgramGuide.nextDayStart(DAY + 23 * HOUR));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    public void nextBoundaryIncludesEndOfProgramAndMidnight() {
        ProgramGuide guide = guide(program("节目", 0, HOUR));
        assertEquals(DAY + HOUR, guide.nextBoundary(DAY + HOUR - 500));
        assertEquals(DAY + 24 * HOUR, guide.nextBoundary(DAY + HOUR));
    }

    @Test
    public void emptyGuideCanBeRenderedWithoutAnInvalidIndex() {
        ProgramGuide guide = new ProgramGuide("20261022", Collections.emptyList());
        assertEquals(-1, guide.currentIndex(DAY));
        assertEquals(0, guide.suggestedIndex(DAY));
    }

    private ProgramGuide guide(ProgramGuide.Program... programs) {
        return new ProgramGuide("20261022", Arrays.asList(programs));
    }

    private ProgramGuide.Program program(String title, long offset, long duration) {
        return new ProgramGuide.Program(title, DAY + offset, duration);
    }
}
