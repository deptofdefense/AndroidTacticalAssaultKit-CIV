package com.atakmap.map.layer.feature;

import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.Objects;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import gov.tak.api.annotation.DontObfuscate;

/**
 * A strongly typed key-value pairing of metadata. Keys must be text and values
 * are restricted to text, primitives, binary blobs, arrays of those types and
 * nested <code>AttributeSet</code> instances.
 * 
 * @author Developer
 */
@DontObfuscate
public final class AttributeSet implements Disposable {

    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            destruct(pointer);
        }
    };

    static Types types = null;
    private final Cleaner cleaner;

    Pointer pointer;
    final ReadWriteLock rwlock = new ReadWriteLock();
    Object owner;

    public AttributeSet() {
        this(create(), null);
    }

    public AttributeSet(AttributeSet other) {
        this(create(other.pointer), null);
    }

    AttributeSet(Pointer pointer, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.owner = owner;
    }
    /**
     * Returns the specified <code>int</code> attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The <code>int</code> value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>int</code>.
     */
    public int getIntAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getIntAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified <code>long</code> attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The <code>long</code> value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>long</code>.
     */
    public long getLongAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getLongAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified <code>double</code> attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The <code>double</code> value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>double</code>.
     */
    public double getDoubleAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getDoubleAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified {@link String} attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The {@link String} value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  {@link String}.
     */
    public String getStringAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getStringAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified binary attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The binary value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>byte[]</code>.
     */
    public byte[] getBinaryAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getBinaryAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified <code>int[]</code> attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The <code>int[]</code> value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>int[]</code>.
     */
    public int[] getIntArrayAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getIntArrayAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified <code>long[]</code> attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The <code>long[]</code> value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>long[]</code>.
     */
    public long[] getLongArrayAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getLongArrayAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified <code>double[]</code> attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The <code>double[]</code> value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>double[]</code>.
     */
    public double[] getDoubleArrayAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getDoubleArrayAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified {@link String} array attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The {@link String} array value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>String[]</code>.
     */
    public String[] getStringArrayAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getStringArrayAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified binary array attribute.
     *  
     * @param name  The name of the attribute
     * 
     * @return  The binary array value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>byte[][]</code>.
     */
    public byte[][] getBinaryArrayAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return getBinaryArrayAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Returns the specified <code>AttributeSet</code> attribute.
     * 
     * @param name  The name of the attribute
     * 
     * @return  The <code>AttributeSet</code> value of the attribute
     * 
     * @throws IllegalArgumentException If the <code>AttributeSet</code> does
     *                                  not contain the specified attribute or
     *                                  if the attribute is not of type
     *                                  <code>AttributeSet</code>.
     */
    public AttributeSet getAttributeSetAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            final Pointer retval = getAttributeSetAttribute(this.pointer, name);
            if(retval == null)
                return null;
            return new AttributeSet(retval, this);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns the type of the specified attribute. Supported values include:
     * 
     * <UL>
     *   <LI>{@link Integer#TYPE}</LI>
     *   <LI>{@link Long#TYPE}</LI>
     *   <LI>{@link Double#TYPE}</LI>
     *   <LI><code>String.class</code></LI>
     *   <LI><code>byte[].class</code></LI>
     *   <LI><code>int[].class</code></LI>
     *   <LI><code>long[].class</code></LI>
     *   <LI><code>double[].class</code></LI>
     *   <LI><code>String[].class</code></LI>
     *   <LI><code>byte[][].class</code></LI>
     *   <LI><code>AttributeSet.class</code></LI>
     * </UL>
     * 
     * @param name  The name of the attribute
     * 
     * @return  The type of the attribute or <code>null</code> if there is no
     *          attribute with that name.
     */
    public Class<?> getAttributeType(String name) {
        final int type;
        this.rwlock.acquireRead();
        try {
            if(!containsAttribute(this.pointer, name))
                return null;
            type = getAttributeType(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }

        // race condition here, but resource is inexpensive
        if(types == null)
            types = new Types();

        // XXX - re-order based on likely hotspot
        if(type == types.INT)
            return Integer.TYPE;
        else if(type == types.LONG)
            return Long.TYPE;
        else if(type == types.DOUBLE)
            return Double.TYPE;
        else if(type == types.STRING)
            return String.class;
        else if(type == types.BLOB)
            return byte[].class;
        else if(type == types.INT_ARRAY)
            return int[].class;
        else if(type == types.LONG_ARRAY)
            return long[].class;
        else if(type == types.DOUBLE_ARRAY)
            return double[].class;
        else if(type == types.STRING_ARRAY)
            return String[].class;
        else if(type == types.BLOB_ARRAY)
            return byte[][].class;
        else if(type == types.ATTRIBUTE_SET)
            return AttributeSet.class;
        else
            throw new IllegalStateException();
    }
    
    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, int value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, long value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, double value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, String value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, byte[] value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, int[] value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, long[] value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, double[] value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, String[] value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, byte[][] value) {
        this.rwlock.acquireRead();
        try {
            setAttribute(this.pointer, name, value);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Sets the value of the specified attribute. Overwrites any existing value.
     * 
     * @param name  The attribute name
     * @param value The value
     */
    public void setAttribute(String name, AttributeSet value) {
        this.rwlock.acquireRead();
        try {
            if (value != null) 
                setAttribute(this.pointer, name, value.pointer);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    
    /**
     * Removes the specified attribute.
     * 
     * @param name  The name of the attribute to be removed.
     */
    public void removeAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            removeAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    /**
     * Returns a {@link java.util.Set Set} of the names of attributes contained
     * in this <code>AttributeSet</code>.
     * 
     * @return  A {@link java.util.Set Set} of the names of attributes contained
     *          in this <code>AttributeSet</code>.
     */
    public Set<String> getAttributeNames() {
        this.rwlock.acquireRead();
        try {
            return new HashSet<String>(Arrays.asList(getAttributeNames(this.pointer)));
        } finally {
            this.rwlock.releaseRead();
        }
    }

    
    /**
     * Returns <code>true</code> if this <code>AttributeSet</code> contains an
     * attribute with the specified name, <code>false</code> otherwise.
     * 
     * @return  <code>true</code> if this <code>AttributeSet</code> contains an
     *          attribute with the specified name, <code>false</code> otherwise.
     */
    public boolean containsAttribute(String name) {
        this.rwlock.acquireRead();
        try {
            return containsAttribute(this.pointer, name);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    /**
     * Clears all attributes.
     */
    public void clear() {
        this.rwlock.acquireRead();
        try {
            clear(this.pointer);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    @Override
    public void dispose() {
        if(cleaner != null)
            cleaner.clean();
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(pointer, rwlock, owner);
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof AttributeSet))
            return false;

        AttributeSet other = (AttributeSet)o;
        Set<String> tkeys = this.getAttributeNames();
        Set<String> okeys = other.getAttributeNames();

        if(!tkeys.equals(okeys))
            return false;

        if(tkeys.isEmpty())
            return true;

        if(types == null)
            types = new Types();

        for(String key : tkeys) {
            final int ttype = getAttributeType(this.pointer, key);
            final int otype = getAttributeType(other.pointer, key);
            if (otype != ttype)
                return false;

            if (ttype == types.INT) {
                if(this.getIntAttribute(key) != other.getIntAttribute(key))
                    return false;
            } else if (ttype == types.LONG) {
                if(this.getLongAttribute(key) != other.getLongAttribute(key))
                    return false;
            } else if (ttype == types.DOUBLE) {
                if(this.getDoubleAttribute(key) != other.getDoubleAttribute(key))
                    return false;
            } else if (ttype == types.STRING) {
                if(!Objects.equals(this.getStringAttribute(key), other.getStringAttribute(key)))
                    return false;
            } else if (ttype == types.BLOB) {
                final byte[] tvalue = this.getBinaryAttribute(key);
                final byte[] ovalue = other.getBinaryAttribute(key);
                if(tvalue == null && ovalue != null)
                    return false;
                else if(tvalue != null && ovalue == null)
                    return false;
                else if(tvalue != null && ovalue != null && !Arrays.equals(tvalue, ovalue))
                    return false;
            } else if (ttype == types.INT_ARRAY) {
                final int[] tvalue = this.getIntArrayAttribute(key);
                final int[] ovalue = other.getIntArrayAttribute(key);
                if(tvalue == null && ovalue != null)
                    return false;
                else if(tvalue != null && ovalue == null)
                    return false;
                else if(tvalue != null && ovalue != null && !Arrays.equals(tvalue, ovalue))
                    return false;
            } else if(ttype == types.LONG_ARRAY) {
                final long[] tvalue = this.getLongArrayAttribute(key);
                final long[] ovalue = other.getLongArrayAttribute(key);
                if(tvalue == null && ovalue != null)
                    return false;
                else if(tvalue != null && ovalue == null)
                    return false;
                else if(tvalue != null && ovalue != null && !Arrays.equals(tvalue, ovalue))
                    return false;
            } else if(ttype == types.DOUBLE_ARRAY) {
                final double[] tvalue = this.getDoubleArrayAttribute(key);
                final double[] ovalue = other.getDoubleArrayAttribute(key);
                if(tvalue == null && ovalue != null)
                    return false;
                else if(tvalue != null && ovalue == null)
                    return false;
                else if(tvalue != null && ovalue != null && !Arrays.equals(tvalue, ovalue))
                    return false;
            } else if(ttype == types.STRING_ARRAY) {
                final String[] tvalue = this.getStringArrayAttribute(key);
                final String[] ovalue = other.getStringArrayAttribute(key);
                if(tvalue == null && ovalue != null)
                    return false;
                else if(tvalue != null && ovalue == null)
                    return false;
                else if(tvalue == null && ovalue == null)
                    continue;
                else if(tvalue.length != ovalue.length)
                    return false;

                for(int i = 0; i < tvalue.length; i++) {
                    if(!Objects.equals(tvalue[i], ovalue[i]))
                        return false;
                }
            } else if(ttype == types.BLOB_ARRAY) {
                final byte[][] tvalue = this.getBinaryArrayAttribute(key);
                final byte[][] ovalue = other.getBinaryArrayAttribute(key);
                if(tvalue == null && ovalue != null)
                    return false;
                else if(tvalue != null && ovalue == null)
                    return false;
                else if(tvalue == null && ovalue == null)
                    continue;
                else if(tvalue.length != ovalue.length)
                    return false;

                for(int i = 0; i < tvalue.length; i++) {
                    if(tvalue[i] == null && ovalue[i] != null)
                        return false;
                    else if(tvalue[i] != null && ovalue[i] == null)
                        return false;
                    else if(tvalue[i] != null && ovalue[i] != null && !Arrays.equals(tvalue[i], ovalue[i]))
                        return false;
                }
            } else if(ttype == types.ATTRIBUTE_SET) {
                final AttributeSet tvalue = this.getAttributeSetAttribute(key);
                final AttributeSet ovalue = other.getAttributeSetAttribute(key);
                if(tvalue == null && ovalue != null)
                    return false;
                else if(tvalue != null && ovalue == null)
                    return false;
                else if(tvalue != null && ovalue != null && !tvalue.equals(ovalue))
                    return false;
            }
        }

        return true;
    }

    static class Types {
        int INT;
        int LONG;
        int DOUBLE;
        int STRING;
        int BLOB;
        int INT_ARRAY;
        int LONG_ARRAY;
        int DOUBLE_ARRAY;
        int STRING_ARRAY;
        int BLOB_ARRAY;
        int ATTRIBUTE_SET;

        Types() {
            INT = getINT();
            LONG = getLONG();
            DOUBLE = getDOUBLE();
            STRING = getSTRING();
            BLOB = getBLOB();
            INT_ARRAY = getINT_ARRAY();
            LONG_ARRAY = getLONG_ARRAY();
            DOUBLE_ARRAY = getDOUBLE_ARRAY();
            STRING_ARRAY = getSTRING_ARRAY();
            BLOB_ARRAY = getBLOB_ARRAY();
            ATTRIBUTE_SET = getATTRIBUTE_SET();
        }
    }

    static native Pointer create();
    static native Pointer create(Pointer other);
    static native void destruct(Pointer pointer);

    static native int getIntAttribute(Pointer pointer, String name);
    static native long getLongAttribute(Pointer pointer, String name);
    static native double getDoubleAttribute(Pointer pointer, String name);
    static native String getStringAttribute(Pointer pointer, String name);
    static native byte[] getBinaryAttribute(Pointer pointer, String name);
    static native int[] getIntArrayAttribute(Pointer pointer, String name);
    static native long[] getLongArrayAttribute(Pointer pointer, String name);
    static native double[] getDoubleArrayAttribute(Pointer pointer, String name);
    static native String[] getStringArrayAttribute(Pointer pointer, String name);
    static native byte[][] getBinaryArrayAttribute(Pointer pointer, String name);
    static native Pointer getAttributeSetAttribute(Pointer pointer, String name);
    static native int getAttributeType(Pointer pointer, String name);
    static native void setAttribute(Pointer pointer, String name, int value);
    static native void setAttribute(Pointer pointer, String name, long value);
    static native void setAttribute(Pointer pointer, String name, double value);
    static native void setAttribute(Pointer pointer, String name, String value);
    static native void setAttribute(Pointer pointer, String name, byte[] value);
    static native void setAttribute(Pointer pointer, String name, int[] value);
    static native void setAttribute(Pointer pointer, String name, long[] value);
    static native void setAttribute(Pointer pointer, String name, double[] value);
    static native void setAttribute(Pointer pointer, String name, String[] value);
    static native void setAttribute(Pointer pointer, String name, byte[][] value);
    static native void setAttribute(Pointer pointer, String name, Pointer value);
    static native void removeAttribute(Pointer pointer, String name);
    static native String[] getAttributeNames(Pointer pointer);
    static native boolean containsAttribute(Pointer pointer, String name);
    static native void clear(Pointer pointer);

    static native int getINT();
    static native int getLONG();
    static native int getDOUBLE();
    static native int getSTRING();
    static native int getBLOB();
    static native int getATTRIBUTE_SET();
    static native int getINT_ARRAY();
    static native int getLONG_ARRAY();
    static native int getDOUBLE_ARRAY();
    static native int getBLOB_ARRAY();
    static native int getSTRING_ARRAY();

} // AttributeSet
