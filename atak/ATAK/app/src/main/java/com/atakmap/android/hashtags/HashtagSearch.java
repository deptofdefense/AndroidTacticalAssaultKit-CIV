
package com.atakmap.android.hashtags;

import java.util.Collection;

/**
 * Action interface for searching content based on associated tags
 */
public interface HashtagSearch {

    /**
     * Search for content based on tags
     * @param tags List of hashtags to search for
     * @return List of matching content
     */
    Collection<HashtagContent> search(Collection<String> tags);
}
