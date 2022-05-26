package com.atakmap.map.layer.feature.wfs;

import java.util.LinkedList;
import com.atakmap.coremap.locale.LocaleUtil;

import com.atakmap.map.layer.feature.AttributeSet;

public abstract class StringValueAttributeSetFilter implements AttributeSetFilter {
    private final String key;
    protected final String test;
    private final boolean ignoreCase;
    
    private StringValueAttributeSetFilter(String key, String test, boolean ignoreCase) {
        this.key = key;
        this.test = ignoreCase ? test.toLowerCase(LocaleUtil.getCurrent()) : test;
        this.ignoreCase = ignoreCase;
    }

    @Override
    public boolean matches(AttributeSet metadata) {
        if(!metadata.containsAttribute(key))
            return false;
        String value;
        if(String.class.equals(metadata.getAttributeType(key)))
            value = metadata.getStringAttribute(key);
        else if(Double.class.equals(metadata.getAttributeType(key)))
            value = String.valueOf(metadata.getDoubleAttribute(key));
        else if(Integer.class.equals(metadata.getAttributeType(key)))
            value = String.valueOf(metadata.getIntAttribute(key));
        else if(Long.class.equals(metadata.getAttributeType(key)))
            value = String.valueOf(metadata.getLongAttribute(key));
        else
            return false;
        if(this.ignoreCase)
            value = value.toLowerCase(LocaleUtil.getCurrent());
        return testValue(value);
    }

    protected abstract boolean testValue(String value);
    
    /**************************************************************************/
    
    public static AttributeSetFilter createFilter(String key, String value, char wildcardChar) {
        return createFilter(key, value, wildcardChar, false);
    }

    public static AttributeSetFilter createFilter(String key, String value, char wildcardChar, boolean ignoreCase) {
        // empty string
        if(value.length() == 0)
            return new Equals(key, value, ignoreCase);

        LinkedList<AttributeSetFilter> filters = new LinkedList<AttributeSetFilter>();
        int lastWildcardIdx;
        int nextWildcardIdx;

        lastWildcardIdx = -1;
        nextWildcardIdx = -1;
        do {
            do {
                lastWildcardIdx = nextWildcardIdx;
                nextWildcardIdx = value.indexOf(wildcardChar, lastWildcardIdx+1);
            } while((nextWildcardIdx-lastWildcardIdx) == 1);
            if(value.length()-lastWildcardIdx > 1)
                filters.add(createFilter(key, value, lastWildcardIdx, nextWildcardIdx, ignoreCase));
        } while(nextWildcardIdx > lastWildcardIdx);
        
        if(filters.isEmpty())
            return new Wildcard(key, value, ignoreCase);
        else
            return new CompoundAttributeSetFilter(filters);
    }
    
    private static AttributeSetFilter createFilter(String key, String value, int lastWildcardIdx, int nextWildcardIdx, boolean ignoreCase) {
        if(lastWildcardIdx < 0 && nextWildcardIdx < 0)
            return new Equals(key, value, ignoreCase);
        else if(lastWildcardIdx >= 0 && nextWildcardIdx < 0)
            return new EndsWith(key, value.substring(lastWildcardIdx+1), ignoreCase);
        else if(lastWildcardIdx >= 0 && nextWildcardIdx >= 0)
            return new Contains(key, value.substring(lastWildcardIdx+1, nextWildcardIdx), ignoreCase);
        else if(lastWildcardIdx < 0 && nextWildcardIdx >= 0)
            return new StartsWith(key, value.substring(0, nextWildcardIdx), ignoreCase);
        else
            throw new IllegalStateException();
    }
    
    /**************************************************************************/
    
    private static class Equals extends StringValueAttributeSetFilter {
        public Equals(String key, String test, boolean ignoreCase) {
            super(key, test, ignoreCase);
        }
        @Override
        protected boolean testValue(String value) {
            return value.equals(test);
        }
    }
    
    private static class Contains extends StringValueAttributeSetFilter {
        public Contains(String key, String test, boolean ignoreCase) {
            super(key, test, ignoreCase);
        }
        @Override
        protected boolean testValue(String value) {
            return value.contains(test);
        }
    }
    
    private static class StartsWith extends StringValueAttributeSetFilter {
        public StartsWith(String key, String test, boolean ignoreCase) {
            super(key, test, ignoreCase);
        }
        @Override
        protected boolean testValue(String value) {
            return value.startsWith(test);
        }
    }
    
    private static class EndsWith extends StringValueAttributeSetFilter {
        public EndsWith(String key, String test, boolean ignoreCase) {
            super(key, test, ignoreCase);
        }
        @Override
        protected boolean testValue(String value) {
            return value.endsWith(test);
        }
    }
    
    private static class Wildcard extends StringValueAttributeSetFilter {
        public Wildcard(String key, String test, boolean ignoreCase) {
            super(key, test, ignoreCase);
        }
        @Override
        protected boolean testValue(String value) {
            return true;
        }
    }
}
