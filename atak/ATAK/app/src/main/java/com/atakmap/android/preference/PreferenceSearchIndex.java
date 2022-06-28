
package com.atakmap.android.preference;

import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;

import java.util.List;
import java.util.UUID;

/**
 * Index to support searching preferences
 */
public class PreferenceSearchIndex {

    private static final String TAG = "PreferenceSearchIndex";

    /**
     * Results of match to include a matched term (may have matched multiple) and a score
     */
    public static class MatchResults {
        /**
         * The overall score for a match
         */
        int score = 0;

        /**
         * A sample match for display/highlight
         */
        String match;

        public int score(int s) {
            score += s;
            return score;
        }

        public String getHighlight() {
            return match;
        }

        public int getScore() {
            return score;
        }
    }

    /**
     * Self type for index of a preference screen index e.g. Route Preferences
     * Parent type for individual preference index e.g. Route checkpoint reached distance
     */
    private final Class<? extends AtakPreferenceFragment> fragClass;

    /**
     * The parent key, used for tools where the preference needs to be looked up by its toolkey.
     */
    private final String parentKey;

    /**
     * Unique key of the preference
     */
    private final String key;

    /**
     * Summary/label of the preference
     */
    private final String summary;

    /**
     * Summary/label of the parent preference/screen
     */
    private final String parentSummary;

    /**
     * Icon of the preference
     */
    private final Drawable icon;

    /**
     * Terms to match on when searching
     */
    private final List<String> terms;

    /**
     * Reference to shared preferences underlying datastore
     */
    private SharedPreferences prefs;

    /**
     * Cache the last match results (may be used for UI/display/sorting)
     */
    private MatchResults _lastMatch;

    /**
     * Create index for an individual preference
     *
     * @param fragClass     Class/type of parent Preference/screen
     * @param key           Key of the preference
     * @param summary       Summary/label of the preference
     * @param parentSummary       Summary/label of the parent preference
     * @param icon          Icon of the preference
     * @param terms         Terms to match on when searching
     */
    public PreferenceSearchIndex(
            final Class<? extends AtakPreferenceFragment> fragClass,
            final String parentKey,
            String key, final String summary, final String parentSummary,
            final Drawable icon, final List<String> terms) {

        if (FileSystemUtils.isEmpty(key)) {
            //Log.w(TAG, "Key not set: " + summary);
            if (fragClass == null) {
                key = UUID.randomUUID().toString().substring(0, 5);
                //Log.d(TAG, "Key/Frag class not set, setting: " + key);
            } else {
                key = fragClass.getName();
            }
        }

        this.fragClass = fragClass;
        this.key = key;
        this.summary = summary;
        this.parentKey = parentKey;
        this.parentSummary = parentSummary;
        this.icon = icon;
        this.terms = terms;

        try {
            // TODO: Clean this interaction up as well.
            prefs = PreferenceManager.getDefaultSharedPreferences(
                    MapView.getMapView().getContext());
        } catch (Exception ignored) {
        }
    }

    /**
     * Gets the ATAK preference fragment that this search index represents.
     * @return the ATAK preference fragment
     */
    public Class<? extends AtakPreferenceFragment> getFragClass() {
        return fragClass;
    }

    /**
     * Gets the parent summary for the search index.
     * @return the parent summary
     */
    public String getParentSummary() {
        return parentSummary;
    }

    /**
     * Gets the parent key for the search index.
     * @return the parent key
     */
    public String getParentKey() {
        return parentKey;
    }

    /**
     * Gets the preference key for the preference described by the search index.
     * @return the preference key
     */
    public String getKey() {
        return key;
    }

    /**
     * Gets the preference summary for the preference described by the search index.
     * @return the preference summary
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Checks to see if the preference has a icon
     * @return true if the preference has an icon
     */
    public boolean hasIcon() {
        return icon != null;
    }

    public Drawable getIcon() {
        return icon;
    }

    public List<String> getTerms() {
        return terms;
    }

    public boolean match(String searchTerms) {
        return getMatch(searchTerms) != null;
    }

    /**
     * Match on the search terms
     *
     * @param searchTerms the search term to look for.
     * @return null if no match, otherwise a scored/weighted result
     */
    public MatchResults getMatch(String searchTerms) {

        // if the preference is hidden, then do not add to the search index.
        final String hidePref = "hidePreferenceItem_" + key;
        if (prefs != null && !prefs.getBoolean(hidePref, true)) {
            return null;
        }

        if (FileSystemUtils.isEmpty(searchTerms))
            return null;

        searchTerms = searchTerms.toLowerCase(LocaleUtil.getCurrent()).trim();

        MatchResults res = new MatchResults();
        if (!FileSystemUtils.isEmpty(summary) &&
                summary.toLowerCase(LocaleUtil.getCurrent())
                        .contains(searchTerms)) {
            //High score for match in the title/summary
            res.score(5);
            res.match = summary;
        }

        if (!FileSystemUtils.isEmpty(terms)) {
            for (String term : terms) {
                if (term != null && term.contains(searchTerms)) {
                    //score for each match in the associated terms
                    res.score(1);
                    //favor terms as match highlight for UI over title/summary
                    res.match = term;
                }
            }
        }

        if (res.score < 1) {
            res = null;
        }
        _lastMatch = res;

        return _lastMatch;
    }

    /**
     * Get results of last match
     * @return the Match Results based on a search.
     */
    public MatchResults getMatch() {
        return _lastMatch;
    }

    @Override
    public String toString() {
        return String.format("%s, %s, %s", fragClass.getName(), summary,
                terms.toString());
    }
}
