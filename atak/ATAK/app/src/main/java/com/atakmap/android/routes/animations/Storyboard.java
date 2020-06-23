
package com.atakmap.android.routes.animations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Storyboard implements MapWidgetAnimationInterpolatorInterface {

    //-------------------- Fields and Properties ---------------------------

    private final List<MapWidgetAnimationInterpolatorInterface> storyboardParts;
    private final StoryboardEventListener listener;
    private boolean isCancelled = false;

    private Storyboard continuationStoryboard = null;

    public Storyboard getContinuationStoryboard() {
        return continuationStoryboard;
    }

    public Storyboard continueWith(Storyboard continueWith) {
        continuationStoryboard = continueWith;
        return this;
    }

    //-------------------- CTOR ---------------------------

    public Storyboard(StoryboardEventListener listener,
            MapWidgetAnimationInterpolatorInterface... storyboardParts) {
        this.storyboardParts = new ArrayList<>(
                Arrays.asList(storyboardParts));
        this.listener = listener;
    }

    public StoryboardPlayer start() {
        StoryboardPlayer player = new StoryboardPlayer(this);
        player.execute();
        return player;
    }

    void cancel() {
        if (!isCancelled) {
            isCancelled = true;

            this.storyboardParts.clear(); //Don't hang on to references here.

            if (this.listener != null) {
                this.listener.onCancelled(this);
            }
        }
    }

    //-------------------- Interface Implementation ---------------------------
    @Override
    public boolean interpolate(long timeInMs) {

        if (storyboardParts.isEmpty())
            return false;

        MapWidgetAnimationInterpolatorInterface part;

        for (int i = 0; i < storyboardParts.size(); i++) {
            part = storyboardParts.get(i);

            if (!part.interpolate(timeInMs)) {
                storyboardParts.remove(i);
                i--;
            }
        }

        if (storyboardParts.isEmpty() && listener != null)
            listener.onCompleted(this);

        return !storyboardParts.isEmpty();
    }

    //-------------------- Event Listener ---------------------------

    public interface StoryboardEventListener {
        void onCompleted(Storyboard storyboard);

        void onCancelled(Storyboard storyboard);
    }

}
