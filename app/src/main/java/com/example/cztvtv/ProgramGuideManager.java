package com.example.cztvtv;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/** All state mutations and listener calls run on the supplied UI dispatcher. */
final class ProgramGuideManager {
    static final long SUMMARY_REFRESH_MS = 60_000;
    static final long GUIDE_REFRESH_MS = 5 * 60_000;
    static final long RETRY_MS = 30_000;
    private static final long MIN_REQUEST_MS = 2_000;

    interface Source {
        Map<String, String> loadCurrentPrograms() throws Exception;
        ProgramGuide loadSchedule(String code, String date) throws Exception;
    }

    interface Dispatcher {
        void post(Runnable runnable);
    }

    interface Clock {
        long wallTime();
        long elapsedTime();
    }

    static final class State {
        ProgramGuide guide;
        boolean loading;
        boolean failed;
        long lastAttempt = -1;
        long loadedAt = -1;
        long updatedAt;
    }

    private final Source source;
    private final Executor executor;
    private final Dispatcher dispatcher;
    private final Clock clock;
    private Runnable listener;
    private final Map<String, State> states = new HashMap<>();
    private final Map<String, String> summaries = new HashMap<>();
    private String date;
    private volatile int dateGeneration;
    private volatile boolean closed;
    private boolean summaryLoading;
    private boolean summaryFailed;
    private long summaryAttempt = -1;
    private long summaryLoadedAt = -1;

    ProgramGuideManager(Source source, Executor executor, Dispatcher dispatcher,
                        Clock clock, Runnable listener) {
        this.source = source;
        this.executor = executor;
        this.dispatcher = dispatcher;
        this.clock = clock;
        this.listener = listener;
        this.date = ProgramGuide.dateAt(clock.wallTime());
    }

    State state(String code) {
        checkDate();
        State state = states.get(code);
        if (state == null) {
            state = new State();
            states.put(code, state);
        }
        return state;
    }

    String currentTitle(String code) {
        State state = state(code);
        String summary = summaryLoadedAt >= 0
                && clock.elapsedTime() - summaryLoadedAt < 2 * SUMMARY_REFRESH_MS
                ? summaries.get(code) : null;
        if (state.guide != null) {
            int current = state.guide.currentIndex(clock.wallTime());
            if (current >= 0) {
                // A channel being browsed elsewhere may have a newer summary than its cached guide.
                if (summary != null && clock.elapsedTime() - state.loadedAt >= GUIDE_REFRESH_MS) {
                    return summary;
                }
                return state.guide.programs.get(current).title;
            }
        }
        // Do not let yesterday's or old network summaries override the actual schedule.
        return summary;
    }

    boolean isSummaryLoading() {
        return summaryLoading;
    }

    void refresh(String code, boolean force) {
        if (closed) {
            return;
        }
        checkDate();
        refreshSummaries(force);
        refreshGuide(code, force);
    }

    void refreshGuide(String code, boolean force) {
        if (closed) {
            return;
        }
        State state = state(code);
        long now = clock.elapsedTime();
        long interval = state.failed ? RETRY_MS : GUIDE_REFRESH_MS;
        if (state.loading || (state.lastAttempt >= 0
                && now - state.lastAttempt < (force ? MIN_REQUEST_MS : interval))) {
            return;
        }
        state.loading = true;
        state.lastAttempt = now;
        int generation = dateGeneration;
        String requestDate = date;
        changed();
        executor.execute(() -> {
            if (closed || generation != dateGeneration) {
                return;
            }
            try {
                ProgramGuide guide = source.loadSchedule(code, requestDate);
                dispatcher.post(() -> {
                    if (!accepts(generation)) {
                        return;
                    }
                    state.guide = guide;
                    state.loading = false;
                    state.failed = false;
                    state.loadedAt = clock.elapsedTime();
                    state.updatedAt = clock.wallTime();
                    changed();
                });
            } catch (Exception error) {
                dispatcher.post(() -> {
                    if (!accepts(generation)) {
                        return;
                    }
                    state.loading = false;
                    state.failed = true;
                    changed();
                });
            }
        });
    }

    long nextUpdateDelay() {
        checkDate();
        long now = clock.wallTime();
        long next = Math.min(now + 15_000, ProgramGuide.nextDayStart(now));
        for (State state : states.values()) {
            if (state.guide != null) {
                next = Math.min(next, state.guide.nextBoundary(now));
            }
        }
        return Math.max(250, next - now);
    }

    void close() {
        closed = true;
        listener = null;
    }

    private void refreshSummaries(boolean force) {
        long now = clock.elapsedTime();
        long interval = summaryFailed ? RETRY_MS : SUMMARY_REFRESH_MS;
        if (summaryLoading || (summaryAttempt >= 0
                && now - summaryAttempt < (force ? MIN_REQUEST_MS : interval))) {
            return;
        }
        summaryLoading = true;
        summaryAttempt = now;
        int generation = dateGeneration;
        executor.execute(() -> {
            if (closed || generation != dateGeneration) {
                return;
            }
            try {
                Map<String, String> result = source.loadCurrentPrograms();
                dispatcher.post(() -> {
                    if (!accepts(generation)) {
                        return;
                    }
                    summaries.clear();
                    summaries.putAll(result);
                    summaryLoadedAt = clock.elapsedTime();
                    summaryLoading = false;
                    summaryFailed = false;
                    changed();
                });
            } catch (Exception error) {
                dispatcher.post(() -> {
                    if (!accepts(generation)) {
                        return;
                    }
                    summaryLoading = false;
                    summaryFailed = true;
                    changed();
                });
            }
        });
    }

    private boolean accepts(int generation) {
        if (closed) {
            return false;
        }
        checkDate();
        return generation == dateGeneration;
    }

    private void checkDate() {
        String today = ProgramGuide.dateAt(clock.wallTime());
        if (!date.equals(today)) {
            date = today;
            dateGeneration++;
            states.clear();
            summaries.clear();
            summaryLoading = false;
            summaryFailed = false;
            summaryAttempt = -1;
            summaryLoadedAt = -1;
        }
    }

    private void changed() {
        if (!closed && listener != null) {
            listener.run();
        }
    }
}
