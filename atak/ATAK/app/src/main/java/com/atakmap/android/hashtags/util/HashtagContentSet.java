
package com.atakmap.android.hashtags.util;

import androidx.annotation.NonNull;

import com.atakmap.android.hashtags.HashtagContent;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Set of content with associated hash tag
 */
public class HashtagContentSet extends HashMap<String, HashtagContent>
        implements Iterable<HashtagContent> {

    private final String _tag;

    public HashtagContentSet(String tag) {
        _tag = tag;
    }

    public String getTag() {
        return _tag;
    }

    public void add(HashtagContent content) {
        put(content.getURI(), content);
    }

    public void remove(HashtagContent content) {
        remove(content.getURI());
    }

    @Override
    public String toString() {
        return _tag + " (" + size() + ")";
    }

    @Override
    @NonNull
    public Iterator<HashtagContent> iterator() {
        return values().iterator();
    }
}
