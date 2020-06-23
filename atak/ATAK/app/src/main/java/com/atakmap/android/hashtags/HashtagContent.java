
package com.atakmap.android.hashtags;

import com.atakmap.android.data.URIContent;

import java.util.Collection;

/**
 * Interface for content that can be tagged
 */
public interface HashtagContent extends URIContent {

    /**
     * Set tags associated with this content
     * @param tags List of tags or null to remove
     */
    void setHashtags(Collection<String> tags);

    /**
     * Get tags associated with this content
     * @return List of tags or null if N/A
     */
    Collection<String> getHashtags();
}
