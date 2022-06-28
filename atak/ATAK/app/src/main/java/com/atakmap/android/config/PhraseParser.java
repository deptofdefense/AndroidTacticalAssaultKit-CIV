
package com.atakmap.android.config;

import org.w3c.dom.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.atakmap.android.maps.MetaDataHolder;

public class PhraseParser {
    /**
     * Resolves a phrase element
     * 
     * 
     */
    public interface Resolver {
        String resolvePhraseKey(char specialChar, String key);
    }

    /**
     * Resolves the element to Bundle.get(key).toString()
     */
    public static class BundleResolver implements Resolver {

        private final MetaDataHolder _bundle;
        private final Map<String, Object> _defaults;

        public BundleResolver(final MetaDataHolder bundle) {
            _bundle = bundle;
            _defaults = new HashMap<>();
        }

        /**
         * Set the default value for a given parameter
         * @param key Parameter key
         * @param defValue Default value
         */
        public void setDefault(String key, Object defValue) {
            _defaults.put(key, defValue);
        }

        @Override
        public String resolvePhraseKey(char specialChar, String key) {
            String r = "";
            Object obj = _bundle.get(key);
            if (obj == null)
                obj = _defaults.get(key);
            if (obj != null)
                r = obj.toString();
            return r;
        }
    }

    /**
     * 
     */
    public static class NotEmptyResolver implements Resolver {
        @Override
        public String resolvePhraseKey(char specialChar, String key) {
            return Boolean.toString(!"".equals(key));
        }
    }

    public static class IsEmptyResolver implements Resolver {
        @Override
        public String resolvePhraseKey(char specialChar, String key) {
            return Boolean.toString("".equals(key));
        }
    }

    /**
     * Check if the key is a boolean value and return the inverse of that
     * value, otherwise return true if the key is empty
     */
    public static class InvertBooleanResolver implements Resolver {
        @Override
        public String resolvePhraseKey(char specialChar, String key) {
            if (key.equalsIgnoreCase("true") || key.equalsIgnoreCase("false"))
                return Boolean.toString(!Boolean.parseBoolean(key));
            else
                return Boolean.toString("".equals(key));
        }
    }

    /**
     * 
     */
    public static class Parameters {

        public Parameters(final Parameters params) {
            _resolvers = new TreeMap<>(params._resolvers);
        }

        public Parameters() {
            _resolvers = new TreeMap<>();
        }

        public void setResolver(char specialChar, Resolver resolver) {
            _resolvers.put(specialChar, resolver);
        }

        public Resolver getResolver(char specialChar) {
            return _resolvers.get(specialChar);
        }

        private final Map<Character, Resolver> _resolvers;
    }

    public static String expandPhraseText(Node textNode, Parameters parms) {
        String r = "";
        if (textNode != null) {
            r = expandPhrase(textNode.getNodeValue(), parms);
        }
        return r;
    }

    public static String expandPhraseElem(Node elemNode, Parameters parms) {
        String r = "";
        if (elemNode != null) {
            r = expandPhraseText(elemNode.getFirstChild(), parms);
        }
        return r;
    }

    /**
     * Expand a phrase to a resulting string
     * 
     * @param phraseSource the source phrase
     * @param params the parameters to be used
     * @return the expanded string
     */
    public static String expandPhrase(final String phraseSource,
            final Parameters params) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < phraseSource.length(); ++i) {
            char c = phraseSource.charAt(i);
            Resolver resolver;
            if (_isNext(phraseSource, i, '{')
                    && (resolver = params.getResolver(c)) != null) {
                StringBuilder key = new StringBuilder();
                i = _buildKey(key, phraseSource, i + 2, params);
                sb.append(resolver.resolvePhraseKey(c, key.toString()));
            } else if (i < phraseSource.length()) {
                sb.append(phraseSource.charAt(i));
            }
        }
        return sb.toString();
    }

    private static int _buildKey(StringBuilder sb, String phraseSource,
            int index, Parameters params) {
        int end = index;
        for (int i = index; i < phraseSource.length(); ++i) {
            char c = phraseSource.charAt(i);
            Resolver resolver;
            if (_isNext(phraseSource, i, '{')
                    && (resolver = params.getResolver(c)) != null) {
                StringBuilder key = new StringBuilder();
                i = _buildKey(key, phraseSource, i + 2, params);
                sb.append(resolver.resolvePhraseKey(c, key.toString()));
            } else if (c == '}') {
                end = i;
                break;
            } else {
                sb.append(c);
            }
        }
        return end;
    }

    private static boolean _isNext(String source, int index, char next) {
        return index + 1 < source.length() && source.charAt(index + 1) == next;
    }
}
