
package com.atakmap.map.layer.feature;

import com.atakmap.android.androidtest.util.RandomUtils;
import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.map.layer.feature.AttributeSet;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class AttributeSetTests extends ATAKInstrumentedTest {

    @Test
    public void AttributeSet_constructor() {
        AttributeSet a = new AttributeSet();
    }

    @Test
    public void AttributeSet_does_not_contain_key() {
        AttributeSet a = new AttributeSet();
        Assert.assertFalse(a.containsAttribute("a"));
    }

    @Test
    public void AttributeSet_contains_key() {
        AttributeSet a = new AttributeSet();
        final String key = UUID.randomUUID().toString();
        a.setAttribute(key, 0);
        Assert.assertTrue(a.containsAttribute(key));
    }

    @Test
    public void AttributeSet_value_types() {
        AttributeSet a = new AttributeSet();

        final String intKey = "test-int-key";
        final String longKey = "test-long-key";
        final String doubleKey = "test-double-key";
        final String stringKey = "test-string-key";
        final String binaryKey = "test-binary-key";
        final String intArrayKey = "test-int-array-key";
        final String longArrayKey = "test-long-array-key";
        final String doubleArrayKey = "test-double-array-key";
        final String stringArrayKey = "test-string-array-key";
        final String binaryArrayKey = "test-binary-array-key";
        final String attributeSetKey = "test-attribute-set-key";

        a.setAttribute(intKey, (int) 0);
        a.setAttribute(longKey, 0L);
        a.setAttribute(doubleKey, 0d);
        a.setAttribute(stringKey, "value");
        a.setAttribute(binaryKey, randomByteArray(1, 5));
        a.setAttribute(intArrayKey, randomIntArray(1, 5));
        a.setAttribute(longArrayKey, randomLongArray(1, 5));
        a.setAttribute(doubleArrayKey, randomDoubleArray(1, 5));
        a.setAttribute(stringArrayKey, randomStringArray(1, 5));
        a.setAttribute(binaryArrayKey, randomByteArray2(1, 5));
        a.setAttribute(attributeSetKey, randomAttributeSet());

        Assert.assertEquals(Integer.TYPE, a.getAttributeType(intKey));
        Assert.assertEquals(Long.TYPE, a.getAttributeType(longKey));
        Assert.assertEquals(Double.TYPE, a.getAttributeType(doubleKey));
        Assert.assertEquals(String.class, a.getAttributeType(stringKey));
        Assert.assertEquals(byte[].class, a.getAttributeType(binaryKey));
        Assert.assertEquals(int[].class, a.getAttributeType(intArrayKey));
        Assert.assertEquals(long[].class, a.getAttributeType(longArrayKey));
        Assert.assertEquals(double[].class,
                a.getAttributeType(doubleArrayKey));
        Assert.assertEquals(String[].class,
                a.getAttributeType(stringArrayKey));
    }

    @Test(expected = IllegalArgumentException.class)
    public void AttributeSet_invalid_key_throws() {
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.getIntAttribute(key);
    }

    @Test(expected = IllegalArgumentException.class)
    public void AttributeSet_type_mismatch_throws() {
        final int i = new java.util.Random().nextInt();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        attrs.getStringAttribute(key);
    }

    @Test
    public void AttributeSet_overwrite_value() {
        final int i1 = new java.util.Random().nextInt();
        final int i2 = ~i1;
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i1);
        attrs.setAttribute(key, i2);
        Assert.assertEquals(attrs.getIntAttribute(key), i2);
    }

    @Test
    public void AttributeSet_overwrite_change_type() {
        final Random r = RandomUtils.rng();
        final int v1 = r.nextInt();
        final double v2 = r.nextDouble();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, v1);
        attrs.setAttribute(key, v2);
        Assert.assertEquals(Double.TYPE, attrs.getAttributeType(key));
        Assert.assertTrue(attrs.getDoubleAttribute(key) == v2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void AttributeSet_insert_null_key_throws() {
        final int i = new java.util.Random().nextInt();
        final String key = null;
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_null_string_value_roundtrip() {
        final String i = null;
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertNull(attrs.getStringAttribute(key));
    }

    @Test
    public void AttributeSet_null_binary_value_roundtrip() {
        final byte[] i = null;
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertNull(attrs.getBinaryAttribute(key));
    }

    @Test
    public void AttributeSet_null_int_array_value_roundtrip() {
        final int[] i = null;
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertNull(attrs.getIntArrayAttribute(key));
    }

    @Test
    public void AttributeSet_null_long_array_value_roundtrip() {
        final long[] i = null;
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertNull(attrs.getLongArrayAttribute(key));
    }

    @Test
    public void AttributeSet_null_double_array_value_roundtrip() {
        final double[] i = null;
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertNull(attrs.getDoubleArrayAttribute(key));
    }

    @Test
    public void AttributeSet_null_string_array_value_roundtrip() {
        final String[] i = null;
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertNull(attrs.getStringArrayAttribute(key));
    }

    @Test
    public void AttributeSet_null_binary_array_value_roundtrip() {
        final byte[][] i = null;
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertNull(attrs.getBinaryArrayAttribute(key));
    }

    @Test
    public void AttributeSet_string_array_null_element_roundtrip() {
        final String[] i = new String[] {
                null
        };
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        String[] value = attrs.getStringArrayAttribute(key);
        Assert.assertNotNull(value);
        Assert.assertEquals(value.length, 1);
        Assert.assertNull(value[0]);
    }

    @Test
    public void AttributeSet_binary_array_null_element_roundtrip() {
        final byte[][] i = new byte[][] {
                null
        };
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        byte[][] value = attrs.getBinaryArrayAttribute(key);
        Assert.assertNotNull(value);
        Assert.assertEquals(value.length, 1);
        Assert.assertNull(value[0]);
    }

    @Test
    public void AttributeSet_int_set() {
        final int i = new java.util.Random().nextInt();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_int_roundtrip() {
        final int i = new java.util.Random().nextInt();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertEquals(attrs.getIntAttribute(key), i);
    }

    @Test
    public void AttributeSet_long_set() {
        final long i = new java.util.Random().nextLong();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_long_roundtrip() {
        final long i = new java.util.Random().nextLong();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertEquals(attrs.getLongAttribute(key), i);
    }

    @Test
    public void AttributeSet_double_set() {
        final double i = new java.util.Random().nextDouble();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_double_roundtrip() {
        final double i = new java.util.Random().nextDouble();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertTrue(attrs.getDoubleAttribute(key) == i);
    }

    @Test
    public void AttributeSet_string_set() {
        final String i = UUID.randomUUID().toString();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_string_roundtrip() {
        final String i = UUID.randomUUID().toString();
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertEquals(attrs.getStringAttribute(key), i);
    }

    @Test
    public void AttributeSet_binary_set() {
        final byte[] i = randomByteArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_binary_roundtrip() {
        final byte[] i = randomByteArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        Assert.assertTrue(Arrays.equals(attrs.getBinaryAttribute(key), i));
    }

    @Test
    public void AttributeSet_int_array_set() {
        final int[] i = randomIntArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_int_array_roundtrip() {
        final int[] i = randomIntArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        int[] value = attrs.getIntArrayAttribute(key);
        Assert.assertTrue(Arrays.equals(value, i));
    }

    @Test
    public void AttributeSet_long_array_set() {
        final long[] i = randomLongArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_long_array_roundtrip() {
        final long[] i = randomLongArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        long[] value = attrs.getLongArrayAttribute(key);
        Assert.assertTrue(Arrays.equals(value, i));
    }

    @Test
    public void AttributeSet_double_array_set() {
        final double[] i = randomDoubleArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_double_array_roundtrip() {
        final double[] i = randomDoubleArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        double[] value = attrs.getDoubleArrayAttribute(key);
        Assert.assertTrue(Arrays.equals(value, i));
    }

    @Test
    public void AttributeSet_string_array_set() {
        final String[] i = randomStringArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_string_array_roundtrip() {
        final String[] i = randomStringArray(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        String[] value = attrs.getStringArrayAttribute(key);
        Assert.assertTrue(Arrays.equals(value, i));
    }

    @Test
    public void AttributeSet_binary_array_set() {
        final byte[][] i = randomByteArray2(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
    }

    @Test
    public void AttributeSet_binary_array_roundtrip() {
        final byte[][] i = randomByteArray2(20, 50);
        final String key = UUID.randomUUID().toString();
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(key, i);
        byte[][] value = attrs.getBinaryArrayAttribute(key);
        Assert.assertNotNull(value);
        Assert.assertEquals(value.length, i.length);
        for (int j = 0; j < value.length; j++)
            Assert.assertTrue(Arrays.equals(value[j], i[j]));
    }

    @Test
    public void AttributeSet_keys() {
        AttributeSet attrs = new AttributeSet();
        final int count = RandomUtils.rng().nextInt(20) + 5;
        Set<String> keys = new HashSet<String>();
        for (int i = 0; i < count; i++)
            keys.add(UUID.randomUUID().toString());

        for (String key : keys)
            attrs.setAttribute(key, 1);

        Assert.assertTrue(keys.equals(attrs.getAttributeNames()));
    }

    @Test
    public void AttributeSet_attribute_set_set() {
        AttributeSet attrs = new AttributeSet();
        attrs.setAttribute(UUID.randomUUID().toString(),
                randomAttributeSet());
    }

    @Test
    public void AttributeSet_attribute_set_roundtrip() {
        AttributeSet attrs = new AttributeSet();
        AttributeSet child = randomAttributeSet();
        final String key = UUID.randomUUID().toString();
        attrs.setAttribute(key, child);
        AttributeSet rchild = attrs.getAttributeSetAttribute(key);
        Assert.assertNotNull(rchild != null);
        Assert.assertTrue(child.equals(rchild));
    }

    static byte[] randomByteArray(int minSize, int maxSize) {
        final Random r = RandomUtils.rng();
        final int size = Math.max(r.nextInt(maxSize - minSize), 1);
        final byte[] retval = new byte[size];
        r.nextBytes(retval);
        return retval;
    }

    static int[] randomIntArray(int minSize, int maxSize) {
        final Random r = RandomUtils.rng();
        final int size = Math.max(r.nextInt(maxSize - minSize), 1);
        final int[] retval = new int[size];
        for (int i = 0; i < size; i++)
            retval[i] = r.nextInt();
        return retval;
    }

    static long[] randomLongArray(int minSize, int maxSize) {
        final Random r = RandomUtils.rng();
        final int size = Math.max(r.nextInt(maxSize - minSize), 1);
        final long[] retval = new long[size];
        for (int i = 0; i < size; i++)
            retval[i] = r.nextLong();
        return retval;
    }

    static double[] randomDoubleArray(int minSize, int maxSize) {
        final Random r = RandomUtils.rng();
        final int size = Math.max(r.nextInt(maxSize - minSize), 1);
        final double[] retval = new double[size];
        for (int i = 0; i < size; i++)
            retval[i] = r.nextDouble();
        return retval;
    }

    static String[] randomStringArray(int minSize, int maxSize) {
        final Random r = RandomUtils.rng();
        final int size = Math.max(r.nextInt(maxSize - minSize), 1);
        final String[] retval = new String[size];
        for (int i = 0; i < size; i++)
            retval[i] = UUID.randomUUID().toString();
        return retval;
    }

    static byte[][] randomByteArray2(int minSize, int maxSize) {
        final Random r = RandomUtils.rng();
        final int size = Math.max(r.nextInt(maxSize - minSize), 1);
        final byte[][] retval = new byte[size][];
        for (int i = 0; i < size; i++)
            retval[i] = randomByteArray(minSize, maxSize);
        return retval;
    }

    static AttributeSet randomAttributeSet() {
        Random r = RandomUtils.rng();
        AttributeSet retval = new AttributeSet();
        retval.setAttribute(UUID.randomUUID().toString(), r.nextInt());
        retval.setAttribute(UUID.randomUUID().toString(), r.nextDouble());
        retval.setAttribute(UUID.randomUUID().toString(), r.nextLong());
        retval.setAttribute(UUID.randomUUID().toString(),
                UUID.randomUUID().toString());
        retval.setAttribute(UUID.randomUUID().toString(),
                randomByteArray(10, 20));
        retval.setAttribute(UUID.randomUUID().toString(),
                randomLongArray(10, 20));
        retval.setAttribute(UUID.randomUUID().toString(),
                randomDoubleArray(10, 20));
        retval.setAttribute(UUID.randomUUID().toString(),
                randomStringArray(10, 20));
        retval.setAttribute(UUID.randomUUID().toString(),
                randomByteArray2(10, 20));
        return retval;
    }
}
