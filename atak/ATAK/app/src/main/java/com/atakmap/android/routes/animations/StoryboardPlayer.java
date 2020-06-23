
package com.atakmap.android.routes.animations;

import android.os.AsyncTask;
import android.os.Process;
import android.os.SystemClock;

//TODO:: Add an animation thread pool so we can move away from AsyncTask due to queuing limits and priority restrictions.

public class StoryboardPlayer extends AsyncTask<Void, Void, Void> {

    //-------------------- Fields and Properties ---------------------------
    private long startTime = 0;
    private final Storyboard storyboard;

    public StoryboardPlayer(Storyboard storyboard) {
        this.storyboard = storyboard;
    }

    @Override
    protected void onPreExecute() {
        startTime = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onCancelled(Void aVoid) {
        storyboard.cancel();
    }

    @Override
    protected void onCancelled() {
        storyboard.cancel();
    }

    @Override
    protected Void doInBackground(Void... voids) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND
                + Process.THREAD_PRIORITY_MORE_FAVORABLE);

        Storyboard currentStoryboard = storyboard;
        boolean interpolated;
        while (!isCancelled() && currentStoryboard != null) {
            interpolated = currentStoryboard
                    .interpolate(SystemClock.elapsedRealtime() - startTime);

            if (!interpolated) {
                currentStoryboard = storyboard.getContinuationStoryboard();
                startTime = SystemClock.elapsedRealtime();
            }
        }
        return null;
    }
}
