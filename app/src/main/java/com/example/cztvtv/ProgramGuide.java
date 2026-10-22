package com.example.cztvtv;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** A day's schedule. The broadcaster's timestamps and durations are milliseconds. */
final class ProgramGuide {
    private static final TimeZone BROADCAST_ZONE = TimeZone.getTimeZone("Asia/Shanghai");

    static final class Program {
        final String title;
        final long startTime;
        final long duration;

        Program(String title, long startTime, long duration) {
            this.title = title == null ? "" : title.trim();
            this.startTime = startTime;
            this.duration = duration;
        }
    }

    final String date;
    final List<Program> programs;

    ProgramGuide(String date, List<Program> input) {
        this.date = date;
        List<Program> sorted = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Program program : input) {
            if (program == null || program.title.isEmpty() || program.startTime <= 0
                    || !date.equals(dateAt(program.startTime))) {
                continue;
            }
            if (seen.add(program.startTime + "\n" + program.title)) {
                sorted.add(program);
            }
        }
        Collections.sort(sorted, (first, second) -> Long.compare(first.startTime, second.startTime));
        this.programs = Collections.unmodifiableList(sorted);
    }

    long endTime(int index) {
        Program program = programs.get(index);
        long next = index + 1 < programs.size()
                ? programs.get(index + 1).startTime : nextDayStart(program.startTime);
        if (program.duration <= 0) {
            return next;
        }
        long end = program.startTime > Long.MAX_VALUE - program.duration
                ? Long.MAX_VALUE : program.startTime + program.duration;
        // A new program takes precedence if the published durations overlap.
        return index + 1 < programs.size() ? Math.min(end, next) : end;
    }

    int currentIndex(long now) {
        if (!date.equals(dateAt(now))) {
            return -1;
        }
        for (int i = programs.size() - 1; i >= 0; i--) {
            if (programs.get(i).startTime <= now && now < endTime(i)) {
                return i;
            }
        }
        return -1;
    }

    int suggestedIndex(long now) {
        int current = currentIndex(now);
        if (current >= 0) {
            return current;
        }
        for (int i = 0; i < programs.size(); i++) {
            if (programs.get(i).startTime > now) {
                return i;
            }
        }
        return Math.max(0, programs.size() - 1);
    }

    long nextBoundary(long now) {
        long next = nextDayStart(now);
        for (int i = 0; i < programs.size(); i++) {
            if (programs.get(i).startTime > now) {
                next = Math.min(next, programs.get(i).startTime);
            }
            if (endTime(i) > now) {
                next = Math.min(next, endTime(i));
            }
        }
        return next;
    }

    static String dateAt(long time) {
        return format("yyyyMMdd", time);
    }

    static String displayDate(long time) {
        return format("yyyy年M月d日", time);
    }

    static String displayTime(long time) {
        return format("HH:mm", time);
    }

    static long nextDayStart(long time) {
        Calendar calendar = Calendar.getInstance(BROADCAST_ZONE, Locale.CHINA);
        calendar.setTimeInMillis(time);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTimeInMillis();
    }

    private static String format(String pattern, long time) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.CHINA);
        format.setTimeZone(BROADCAST_ZONE);
        return format.format(new Date(time));
    }
}
