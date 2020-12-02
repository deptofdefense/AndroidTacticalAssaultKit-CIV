
package com.atakmap.android.hashtags;

import com.atakmap.android.hashtags.util.HashtagContentMap;
import com.atakmap.android.hashtags.util.HashtagContentSet;
import com.atakmap.android.maps.MapView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks all registered hashtags in the system and provides methods of
 * filtering content by hashtag
 */
public class HashtagManager {

    // Sort tags alphabetically
    public static final Comparator<String> SORT_BY_NAME = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            return o1.compareToIgnoreCase(o2);
        }
    };

    // Listener for tags/content list changed
    public interface OnUpdateListener {
        void onHashtagsUpdate(HashtagContent content);
    }

    // Singleton
    private static HashtagManager _instance;

    /**
     * The primary method of accessing the hashtag manager
     * @return Hashtag manager instance (instantiated if not already)
     */
    public static synchronized HashtagManager getInstance() {
        return _instance != null ? _instance
                : (_instance = new HashtagManager());
    }

    // Map each list of contents by hashtag
    private final HashtagContentMap _contentMap;

    // Update listeners
    private final Set<OnUpdateListener> _updateListeners;

    private HashtagManager() {
        _contentMap = new HashtagContentMap();
        _updateListeners = new HashSet<>();
    }

    /**
     * Register content to this manager
     * The tags associated with this content are pulled from
     * {@link HashtagContent#getHashtags()}
     *
     * @param content Content to register
     */
    public void registerContent(HashtagContent content) {
        Collection<String> cTags = content.getHashtags();
        if (cTags.isEmpty())
            return;

        synchronized (_contentMap) {
            for (String tag : cTags)
                registerContentNoSync(content, tag);
        }

        // Notify listeners
        onHashtagsUpdate(content);
    }

    /**
     * Unregister content from the manager
     * When updating tags, this should be called BEFORE setting the new tags
     *
     * @param content Content to unregister
     */
    public void unregisterContent(HashtagContent content) {
        Collection<String> cTags = content.getHashtags();
        if (cTags.isEmpty())
            return;

        synchronized (_contentMap) {
            for (String tag : cTags)
                unregisterContentNoSync(content, tag);
        }

        // Notify listeners
        onHashtagsUpdate(content);
    }

    /**
     * Update content with a new set of registered hashtags
     *
     * This will automatically read the current (old) hashtags and unregister
     * before registering with the new tags
     * It's up to the caller to set the new tags on the content itself so that
     * later calls to {@link HashtagContent#getHashtags()} return them
     *
     * @param content Content to register
     * @param newTags New tags to register
     */
    public void updateContent(HashtagContent content,
            Collection<String> newTags) {

        // Make sure tag lists are modifiable (see below)
        Collection<String> existingTags = content.getHashtags();

        // Null checks
        if (existingTags == null)
            existingTags = Collections.emptyList();
        if (newTags == null)
            newTags = Collections.emptyList();

        // Check if there's anything to do before entering the lock
        if (existingTags.isEmpty() && newTags.isEmpty())
            return;

        synchronized (_contentMap) {
            for (String tag : existingTags)
                unregisterContentNoSync(content, tag);
            for (String tag : newTags)
                registerContentNoSync(content, tag);
        }

        // Notify listeners
        onHashtagsUpdate(content);
    }

    /**
     * Find all contents associated with a set of hashtags
     * @param tags List of hashtags
     * @return List of associated contents
     */
    public Set<HashtagContent> findContents(Collection<String> tags) {
        Set<HashtagContent> ret = new HashSet<>();
        synchronized (_contentMap) {
            for (String tag : tags) {
                HashtagContentSet contents = _contentMap.get(tag);
                if (contents != null)
                    ret.addAll(contents.values());
            }
        }
        return ret;
    }

    public Set<HashtagContent> findContents(String tag) {
        return findContents(Collections.singletonList(tag));
    }

    /**
     * Get complete list of registered hashtags
     * @param sortMode Method of sorting hashtags
     * @return List of hashtags
     */
    public List<String> getTags(HashtagSortMode sortMode) {
        List<String> tags;
        if (sortMode == HashtagSortMode.USAGE) {
            // Special usage sorting
            final Map<String, Integer> usages = new HashMap<>();
            synchronized (_contentMap) {
                tags = _contentMap.getTags();
                for (HashtagContentSet set : _contentMap.values())
                    usages.put(set.getTag(), set.size());
            }
            Collections.sort(tags, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    int uCmp = Integer.compare(usages.get(o2), usages.get(o1));
                    return uCmp == 0 ? SORT_BY_NAME.compare(o1, o2) : uCmp;
                }
            });
        } else {
            // Default sorting
            synchronized (_contentMap) {
                tags = _contentMap.getTags();
            }
            if (sortMode == HashtagSortMode.ALPHA) {
                // Simple alphabetical sort
                Collections.sort(tags, SORT_BY_NAME);
            }
        }
        return tags;
    }

    public List<String> getTags() {
        return getTags(HashtagSortMode.USAGE);
    }

    /**
     * Get the number of contents associated with a given tag
     * @param tag Hashtag
     * @return Usage count
     */
    public int getUsageCount(String tag) {
        synchronized (_contentMap) {
            HashtagContentSet contents = _contentMap.get(tag);
            return contents != null ? contents.size() : 0;
        }
    }

    // ************* LISTENERS ************* //

    public void registerUpdateListener(OnUpdateListener l) {
        synchronized (_updateListeners) {
            _updateListeners.add(l);
        }
    }

    public void unregisterUpdateListener(OnUpdateListener l) {
        synchronized (_updateListeners) {
            _updateListeners.remove(l);
        }
    }

    // ************** PRIVATE ************** //

    /**
     * Register content with a specific tag
     * @param content Content to register
     * @param tag Tag to associate with content
     */
    private void registerContentNoSync(HashtagContent content, String tag) {
        HashtagContentSet contents = _contentMap.get(tag);
        if (contents == null)
            contents = _contentMap.putNew(tag);

        // Add content to list associated with this tag
        contents.add(content);
    }

    /**
     * Unregister content with a specific tag
     * @param content Content to unregister
     * @param tag Tag associated with content
     */
    private void unregisterContentNoSync(HashtagContent content, String tag) {
        HashtagContentSet contents = _contentMap.get(tag);
        if (contents == null)
            return;

        // Remove content from list associated with this tag
        contents.remove(content);

        // Remove empty list and associated if tag if needed
        if (contents.isEmpty())
            _contentMap.remove(tag);
    }

    /**
     * Run hashtag update listeners on UI thread (if we can)
     */
    private void onHashtagsUpdate(final HashtagContent content) {
        Runnable r = new Runnable() {
            @Override
            public void run() {
                for (OnUpdateListener l : getUpdateListeners())
                    l.onHashtagsUpdate(content);
            }
        };
        MapView mv = MapView.getMapView();
        if (mv != null)
            mv.post(r);
        else
            r.run();
    }

    /**
     * Get the list of update listeners
     *
     * We construct a new list in case one of the listeners decides to
     * unregister itself within the callback method
     * (otherwise leads to a {@link ConcurrentModificationException})
     *
     * @return List of update listeners
     */
    private List<OnUpdateListener> getUpdateListeners() {
        synchronized (_updateListeners) {
            return new ArrayList<>(_updateListeners);
        }
    }
}
