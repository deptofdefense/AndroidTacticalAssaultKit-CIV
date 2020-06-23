
package com.atakmap.android.hashtags.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Hash map that uses case-insensitive keys mapped to content sets
 */
public class HashtagContentMap extends HashtagMap<HashtagContentSet> {

    /**
     * Put new content set
     * @param tag Hashtag
     * @return New content set
     */
    public HashtagContentSet putNew(String tag) {
        HashtagContentSet set = new HashtagContentSet(tag);
        super.put(tag, set);
        return set;
    }

    /**
     * Get list of tags in this map
     * Different from {@link #keySet()} because the keys are always lowercase
     * @return List of tags
     */
    public List<String> getTags() {
        Collection<HashtagContentSet> values = values();
        List<String> tags = new ArrayList<>(values.size());
        for (HashtagContentSet set : values)
            tags.add(set.getTag());
        return tags;
    }
}
