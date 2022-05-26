
package com.atakmap.android.routes.nav;

import java.util.ArrayList;
import java.util.List;

/**
 * Class representing a navigational cue along a route.
 */
public class NavigationCue {

    /**
     * Defines how the cue should be triggered--based on distance away from a point or estimated
     * time until the point is reached. For now, only distance is supported.
     */
    public enum TriggerMode {
        DISTANCE('d'),
        ETA('t');

        private final Character _mode;

        TriggerMode(Character mode) {
            _mode = mode;
        }

        /**
         * Encodes the Trigger Mode as a character.
         *
         * @return Character representing the trigger mode
         */
        public Character toValue() {
            return _mode;
        }

        /**
         * Decodes the TriggerMode from its encoding as a character.
         * @param val the encoded character
         * @return the corresponding trigger mode
         */
        public static TriggerMode fromValue(Character val) {
            if (val == 'd') {
                return TriggerMode.DISTANCE;
            } else if (val == 't') {
                return TriggerMode.ETA;
            } else {
                throw new IllegalArgumentException(
                        "Unknown character encoding of " + val
                                + " was passed.");
            }
        }
    }

    private final String id;
    private final String voiceCue;
    private final String textCue;

    private final List<ConditionalNavigationCue> _cues = new ArrayList<>();

    /**
     * Constructs a new Navigation Cue.
     *
     * @param id the id for the navigation cue
     * @param voiceCue the voice cue to be spoken
     * @param textCue the text cue to be displayed
     */
    public NavigationCue(final String id, final String voiceCue,
            final String textCue) {
        this.id = id;
        this.voiceCue = voiceCue;
        this.textCue = textCue;
    }

    /**
     * Adds a cue to the NavigationCue.
     * @param triggerMode distance or time
     * @param triggerValue the trigger value related to the mode
     */
    public void addCue(TriggerMode triggerMode, int triggerValue) {
        _cues.add(new ConditionalNavigationCue(triggerMode, triggerValue));
    }

    /**
     * Get the id of the waypoint that is associated with this Navigation Cue
     */
    public String getID() {
        return id;
    }

    /**
     * Gets the voice cue associated with this cue.
     *
     * @return return the voice cue
     */
    public String getVoiceCue() {
        return voiceCue;
    }

    /**
     * Gets the text cue associated with this cue.
     *
     * @return returns the text cue
     */
    public String getTextCue() {
        return textCue;
    }

    /**
     * Gets a copy of the map associating departure UIDs to ConditionalNav cues.
     *
     * @return get all cues
     */
    public List<ConditionalNavigationCue> getCues() {
        return new ArrayList<>(_cues);
    }

    /**
     * Changes "Right" to "Left" and "Left" to "Right"
     */
    private static String flipText(String oldText) {
        String newText;

        if (oldText.contains("Right")) {
            newText = oldText.replace("Right", "Left");
        } else if (oldText.contains("Left")) {
            newText = oldText.replace("Left", "Right");
        } else {
            newText = oldText;
        }

        return newText;
    }

    /**
     * Creates a new cue that is the inverse of the one that is passed as a parameter. Conditional
     * cues from the source cue will be preserved for the new cue.
     *
     * @param newCueId The ID that new cue should have
     * @param cue The cue to create the inverse of
     * @return the navigation cue reversed.
     */
    public static NavigationCue inverseCue(String newCueId, NavigationCue cue) {
        // Invert the text and voice cues
        String textCue = flipText(cue.getTextCue());
        String voiceCue = flipText(cue.getVoiceCue());

        List<ConditionalNavigationCue> oldConCues = cue.getCues();

        NavigationCue newCue = new NavigationCue(newCueId, voiceCue, textCue);

        newCue._cues.clear();
        newCue._cues.addAll(oldConCues);

        return newCue;
    }

    /**
     * Class representing the guts of a Navigation Cue. 
     */
    public static class ConditionalNavigationCue {
        TriggerMode triggerMode;
        int triggerValue;

        public ConditionalNavigationCue(final TriggerMode triggerMode,
                final int triggerValue) {
            this.triggerMode = triggerMode;
            this.triggerValue = triggerValue;
        }

        public TriggerMode getTriggerMode() {
            return triggerMode;
        }

        public int getTriggerValue() {
            return triggerValue;
        }
    }
}
