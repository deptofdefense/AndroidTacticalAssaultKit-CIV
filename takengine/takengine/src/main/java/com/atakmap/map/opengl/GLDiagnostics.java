package com.atakmap.map.opengl;

import android.os.SystemClock;
import com.atakmap.util.Profiler;

public class GLDiagnostics {
    public final Profiler profiler = new Profiler();

    private long targetMillisPerFrame = 0;
    private long timeCall;
    private long lastCall;
    private long currCall;
    private long lastReport;
    private long count = 0;
    private double currentFramerate = 0;

    public void eventTick() {
        if (count == 0) {
            timeCall = SystemClock.uptimeMillis();
        } else if (count > 1000) {
            currentFramerate = (1000000.0 / (SystemClock.uptimeMillis() - timeCall));
            timeCall = SystemClock.uptimeMillis();
            count = 0;
            lastReport = timeCall;
        } else if ((SystemClock.uptimeMillis() - lastReport) > 1000) {
            currentFramerate = ((count * 1000.0d) / (SystemClock.uptimeMillis() - timeCall));
            lastReport = SystemClock.uptimeMillis();

            if ((SystemClock.uptimeMillis() - timeCall) > 5000) {
                timeCall = SystemClock.uptimeMillis();
                count = 0;
                lastReport = timeCall;
            }
        }
        count++;

        final long tick = SystemClock.elapsedRealtime();

        // slows the pipeline down to effect the desired frame rate
        currCall = SystemClock.uptimeMillis();
        lastCall = currCall;
    }

    public double eventFramerate() {
        return currentFramerate;
    }

    public void flush(GLMapView view) {
        flush(view, true);
    }
    public void flush(GLMapView view, boolean reset) {
        for(Profiler.Measurement m : profiler.getProfile().children.values())
            flushDiagnostics(view, m, "", m.metrics.getDuration());
        if(reset)
            profiler.reset();
    }

    static void flushDiagnostics(GLMapView view, Profiler.Measurement m, String indent, long total) {
        view.addRenderDiagnostic(String.format("%s%s count %3d duration %7dus %03.1f", indent, m.name, m.metrics.getCount(), (int)(m.metrics.getDuration()/1000L), (double)m.metrics.getDuration()/(double)total*100d));
        for(Profiler.Measurement c : m.children.values()) {
            flushDiagnostics(view, c, indent +"  ", total);
        }
    }
}
