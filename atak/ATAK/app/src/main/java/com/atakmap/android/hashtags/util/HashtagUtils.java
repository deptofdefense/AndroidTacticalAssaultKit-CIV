
package com.atakmap.android.hashtags.util;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import com.atakmap.android.hashtags.HashtagContent;
import com.atakmap.android.hashtags.HashtagSearch;
import com.atakmap.coremap.filesystem.FileSystemUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper method relating to hashtags
 */
public class HashtagUtils {

    private static final Object[] STYLE_SPANS = {
            new ForegroundColorSpan(0xFF33B5E5),
            new UnderlineSpan()
    };

    /**
     * Extract hashtags from a message
     * @param msg Message text
     * @return List of hashtags
     */
    public static List<String> extractTags(String msg) {
        final List<String> ret = new ArrayList<>();
        if (msg != null) {
            parseTaggedMessage(msg, new ParseCallback() {
                @Override
                public void onHashtag(String tag, String fullMessage) {
                    ret.add(tag);
                }
            });
        }
        return ret;
    }

    /**
     * Get stylized message containing highlighted tags
     * @param msg Message text
     * @return Stylized message
     */
    public static Spanned getStylizedMessage(String msg) {
        final int[] lastIdx = {
                0
        };
        final SpannableStringBuilder sb = new SpannableStringBuilder();
        parseTaggedMessage(msg, new ParseCallback() {
            @Override
            public void onHashtag(String tag, String msg) {
                int idx = msg.indexOf(tag, lastIdx[0]);
                if (idx > -1) {
                    sb.append(msg, lastIdx[0], idx);
                    lastIdx[0] = idx + tag.length();
                }
                int start = sb.length();
                int end = start + tag.length();
                sb.append(tag);
                for (Object style : STYLE_SPANS)
                    sb.setSpan(style, start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new URLSpan(tag), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        });
        sb.append(msg, lastIdx[0], msg.length());
        return sb;
    }

    /**
     * Interface called when a hashtag is found in a message
     */
    public interface ParseCallback {
        void onHashtag(String tag, String fullMessage);
    }

    /**
     * Parse a message for hashtags
     * @param msg Message to parse
     * @param cb Callback fired when tag is found
     */
    public static void parseTaggedMessage(String msg, ParseCallback cb) {
        int startHash, startSpace = 0;
        while ((startHash = msg.indexOf('#', startSpace)) > -1) {
            startSpace = msg.indexOf(' ', startHash);
            if (startSpace == -1)
                startSpace = msg.length();
            String ht = msg.substring(startHash, startSpace);
            if (ht.length() > 1 && cb != null)
                cb.onHashtag(ht, msg);
        }
    }

    /**
     * Validate a string so that it follows the hashtag format
     * No spaces and must begin with a "#"
     * @param tag Tag string
     * @return Validated tag or empty if null
     */
    public static String validateTag(String tag) {
        if (FileSystemUtils.isEmpty(tag) || tag.equals("#"))
            return "";
        if (!tag.startsWith("#"))
            tag = "#" + tag;
        return tag.trim().replace(" ", "");
    }

    /**
     * Search an object by tags
     * @param content Content to search
     * @param searchTags Tags to search for
     * @return True if tags match
     */
    public static boolean search(HashtagContent content,
            Collection<String> searchTags) {
        Collection<String> tags = content.getHashtags();
        if (tags != null && !tags.isEmpty()) {
            for (String st : searchTags) {
                if (tags.contains(st))
                    return true;
            }
        }
        return false;
    }

    /**
     * Search an object by tags
     * The object itself may be tagged or implement the search function
     * @param toSearch Object to search
     * @param searchTags Tags to search for
     * @return List of content that matches the search
     */
    public static Collection<HashtagContent> search(Object toSearch,
            Collection<String> searchTags) {
        if (toSearch == null || searchTags == null || searchTags.isEmpty())
            return Collections.emptyList();

        Set<HashtagContent> ret = new HashSet<>();

        // Check if this object has associated tags that match
        if (toSearch instanceof HashtagContent) {
            HashtagContent content = (HashtagContent) toSearch;
            if (search(content, searchTags))
                ret.add(content);
        }

        // Perform search on the object itself
        if (toSearch instanceof HashtagSearch)
            ret.addAll(((HashtagSearch) toSearch).search(searchTags));

        return ret;
    }
}
