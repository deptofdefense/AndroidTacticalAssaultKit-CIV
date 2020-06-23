package com.atakmap.map.layer.feature.wfs;

import com.atakmap.map.layer.feature.AttributeSet;

public abstract class NumberValueAttributeSetFilter implements AttributeSetFilter {
    private final String key;
    protected final double test;
    
    private NumberValueAttributeSetFilter(String key, double test) {
        this.key = key;
        this.test = test;
    }

    @Override
    public boolean matches(AttributeSet metadata) {
        if(!metadata.containsAttribute(key))
            return false;
        double value;
        if(Double.class.equals(metadata.getAttributeType(key)))
            value = metadata.getDoubleAttribute(key);
        else if(Integer.class.equals(metadata.getAttributeType(key)))
            value = metadata.getIntAttribute(key);
        else if(Long.class.equals(metadata.getAttributeType(key)))
            value = metadata.getLongAttribute(key);
        else if(String.class.equals(metadata.getAttributeType(key)))
            try {
                value = Double.parseDouble(metadata.getStringAttribute(key));
            } catch(NumberFormatException e) {
                return false;
            }
        else
            return false;
        return testValue(value);
    }
    
    protected abstract boolean testValue(double value);

    /**************************************************************************/
    
    public static AttributeSetFilter lessThan(String key, double value) {
        return new LT(key, value);
    }
    
    public static AttributeSetFilter greaterThan(String key, double value) {
        return new GT(key, value);
    }
    
    public static AttributeSetFilter equalTo(String key, double value) {
        return new EQ(key, value);
    }
    
    /**************************************************************************/
    
    private static class LT extends NumberValueAttributeSetFilter {
        public LT(String key, double test) {
            super(key, test);
        }
        @Override
        public boolean testValue(double value) {
            return value < test;
        }
    }
    
    private static class GT extends NumberValueAttributeSetFilter {
        public GT(String key, double test) {
            super(key, test);
        }
        @Override
        public boolean testValue(double value) {
            return value > test;
        }
    }
    
    private static class EQ extends NumberValueAttributeSetFilter {
        public EQ(String key, double test) {
            super(key, test);
        }
        @Override
        public boolean testValue(double value) {
            return value > test;
        }
    }
}
