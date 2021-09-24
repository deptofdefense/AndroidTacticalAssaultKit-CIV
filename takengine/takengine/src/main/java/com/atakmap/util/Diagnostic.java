package com.atakmap.util;

public final class Diagnostic {
    long timerStart;
    int count = 0;
    long duration = 0;

    public void reset() {
        count = 0;
        duration = 0L;
    }

    public long getDuration() {
        return duration;
    }

    public int getCount() {
        return count;
    }

    public void start() {
        timerStart = System.nanoTime();
    }

    public void stop() {
        final long timerStop = System.nanoTime();
        duration += (timerStop-timerStart);
        count++;
    }
}
