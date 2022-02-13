package gov.tak.api.util;

import com.atakmap.coremap.log.Log;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Test {@link AttributeSet} functionality.
 *
 * @since 0.17.0
 */
public class AttributeSetTest {
    private static final String BOOL = "boolean";
    private static final String INT = "int";
    private static final String LONG = "long";
    private static final String DOUBLE = "double";
    private static final String STRING = "string";
    private static final String BLOB = "blob";
    private static final String INT_ARRAY = "int_array";
    private static final String LONG_ARRAY = "long_array";
    private static final String DOUBLE_ARRAY = "double_array";
    private static final String STRING_ARRAY = "string_array";
    private static final String BLOB_ARRAY = "blob_array";
    private static final String NESTED = "nested";

    private static final Set<String> BASIC_KEY_NAMES = new HashSet<>(Arrays.asList(
            BOOL, INT, LONG, DOUBLE, STRING, BLOB, INT_ARRAY, LONG_ARRAY, DOUBLE_ARRAY, STRING_ARRAY, BLOB_ARRAY
    ));

    private static final boolean BOOL_VALUE = true;
    private static final int INT_VALUE = 42;
    private static final long LONG_VALUE = Long.MAX_VALUE - 1;
    private static final long[] LONG_ARRAY_VALUE = { LONG_VALUE, Long.MAX_VALUE - 2, Long.MAX_VALUE - 3 };
    private static final double DOUBLE_VALUE = 42.0001d;
    private static final String STRING_VALUE = "I'm a teapot";
    private static final byte[] BLOB_VALUE = { 1, 2, 3, 4 };
    private static final int[] INT_ARRAY_VALUE = { 1, 2, 3, 4 };
    private static final double[] DOUBLE_ARRAY_VALUE = { 1, 2, 3, 4 };
    private static final String[] STRING_ARRAY_VALUE = { "fetch", "fido", "fetch" };
    private static final byte[][] BLOB_ARRAY_VALUE = { { 1, 2, 3 }, { 4, 5, 6 } };

    @BeforeClass
    public static void init() {
        com.atakmap.coremap.log.Log.registerLogListener(new Log.LogListener() {

            @Override
            public void write(String tag, String msg, Throwable tr) {
                System.out.println(tag + ": " + msg);
                if (tr != null) {
                    tr.printStackTrace(System.out);
                }
            }
        });
    }

    @Test
    public void test_all_basic_types() {
        checkBasicTypes(makeAttributeSet());
    }

    @Test
    public void test_copy_constructor() {
        checkBasicTypes(new AttributeSet(makeAttributeSet()));
    }

    @Test
    public void test_nested_attribute_set() {
        final AttributeSet as = new AttributeSet();
        final AttributeSet nested = makeAttributeSet();

        as.setAttribute(NESTED, nested);

        // Using the copy constructor validates the JSON serialization/deserialization
        final AttributeSet as2 = new AttributeSet(as);

        checkBasicTypes(as2.getAttributeSetAttribute(NESTED));
    }

    @Test
    public void test_default_values() {
        final AttributeSet as = new AttributeSet();

        assertThat(as.getAttributeNames()).isEmpty();

        assertThat(as.getBooleanAttribute(BOOL, BOOL_VALUE)).isEqualTo(BOOL_VALUE);
        assertThat(as.getIntAttribute(INT, INT_VALUE)).isEqualTo(INT_VALUE);
        assertThat(as.getLongAttribute(LONG, LONG_VALUE)).isEqualTo(LONG_VALUE);
        assertThat(as.getDoubleAttribute(DOUBLE, DOUBLE_VALUE)).isEqualTo(DOUBLE_VALUE);
        assertThat(as.getStringAttribute(STRING, STRING_VALUE)).isEqualTo(STRING_VALUE);
        assertThat(as.getBinaryAttribute(BLOB, BLOB_VALUE)).isEqualTo(BLOB_VALUE);

        assertThat(as.getIntArrayAttribute(INT_ARRAY, INT_ARRAY_VALUE)).isEqualTo(INT_ARRAY_VALUE);
        assertThat(as.getLongArrayAttribute(LONG_ARRAY, LONG_ARRAY_VALUE)).isEqualTo(LONG_ARRAY_VALUE);
        assertThat(as.getDoubleArrayAttribute(DOUBLE_ARRAY, DOUBLE_ARRAY_VALUE)).isEqualTo(DOUBLE_ARRAY_VALUE);
        assertThat(as.getStringArrayAttribute(STRING_ARRAY, STRING_ARRAY_VALUE)).isEqualTo(STRING_ARRAY_VALUE);
        assertThat(as.getBinaryArrayAttribute(BLOB_ARRAY, BLOB_ARRAY_VALUE)).isEqualTo(BLOB_ARRAY_VALUE);
    }

    @Test
    public void test_attribute_type_value_types() {
        assertThat(AttributeSet.AttributeType.BOOLEAN.getValueType()).isEqualTo(Boolean.class);
        assertThat(AttributeSet.AttributeType.INTEGER.getValueType()).isEqualTo(Integer.class);
        assertThat(AttributeSet.AttributeType.LONG.getValueType()).isEqualTo(Long.class);
        assertThat(AttributeSet.AttributeType.DOUBLE.getValueType()).isEqualTo(Double.class);
        assertThat(AttributeSet.AttributeType.STRING.getValueType()).isEqualTo(String.class);
        assertThat(AttributeSet.AttributeType.BLOB.getValueType()).isEqualTo(byte[].class);
        assertThat(AttributeSet.AttributeType.ATTRIBUTE_SET.getValueType()).isEqualTo(AttributeSet.class);

        assertThat(AttributeSet.AttributeType.INTEGER_ARRAY.getValueType()).isEqualTo(int[].class);
        assertThat(AttributeSet.AttributeType.LONG_ARRAY.getValueType()).isEqualTo(long[].class);
        assertThat(AttributeSet.AttributeType.DOUBLE_ARRAY.getValueType()).isEqualTo(double[].class);
        assertThat(AttributeSet.AttributeType.STRING_ARRAY.getValueType()).isEqualTo(String[].class);
        assertThat(AttributeSet.AttributeType.BLOB_ARRAY.getValueType()).isEqualTo(byte[][].class);
    }

    @Test
    public void test_attribute_types() {
        final AttributeSet as = makeAttributeSet();
        final AttributeSet nested = new AttributeSet();

        as.setAttribute(NESTED, nested);

        assertThat(as.getAttributeType(BOOL)).isEqualTo(AttributeSet.AttributeType.BOOLEAN);
        assertThat(as.getAttributeType(INT)).isEqualTo(AttributeSet.AttributeType.INTEGER);
        assertThat(as.getAttributeType(LONG)).isEqualTo(AttributeSet.AttributeType.LONG);
        assertThat(as.getAttributeType(DOUBLE)).isEqualTo(AttributeSet.AttributeType.DOUBLE);
        assertThat(as.getAttributeType(STRING)).isEqualTo(AttributeSet.AttributeType.STRING);
        assertThat(as.getAttributeType(BLOB)).isEqualTo(AttributeSet.AttributeType.BLOB);
        assertThat(as.getAttributeType(NESTED)).isEqualTo(AttributeSet.AttributeType.ATTRIBUTE_SET);

        assertThat(as.getAttributeType(INT_ARRAY)).isEqualTo(AttributeSet.AttributeType.INTEGER_ARRAY);
        assertThat(as.getAttributeType(LONG_ARRAY)).isEqualTo(AttributeSet.AttributeType.LONG_ARRAY);
        assertThat(as.getAttributeType(DOUBLE_ARRAY)).isEqualTo(AttributeSet.AttributeType.DOUBLE_ARRAY);
        assertThat(as.getAttributeType(STRING_ARRAY)).isEqualTo(AttributeSet.AttributeType.STRING_ARRAY);
        assertThat(as.getAttributeType(BLOB_ARRAY)).isEqualTo(AttributeSet.AttributeType.BLOB_ARRAY);
    }

    @Test
    public void test_attribute_value_types() {
        final AttributeSet as = makeAttributeSet();
        final AttributeSet nested = new AttributeSet();

        as.setAttribute(NESTED, nested);

        assertThat(as.getAttributeValueType(BOOL)).isEqualTo(Boolean.class);
        assertThat(as.getAttributeValueType(INT)).isEqualTo(Integer.class);
        assertThat(as.getAttributeValueType(LONG)).isEqualTo(Long.class);
        assertThat(as.getAttributeValueType(DOUBLE)).isEqualTo(Double.class);
        assertThat(as.getAttributeValueType(STRING)).isEqualTo(String.class);
        assertThat(as.getAttributeValueType(BLOB)).isEqualTo(byte[].class);
        assertThat(as.getAttributeValueType(NESTED)).isEqualTo(AttributeSet.class);

        assertThat(as.getAttributeValueType(INT_ARRAY)).isEqualTo(int[].class);
        assertThat(as.getAttributeValueType(LONG_ARRAY)).isEqualTo(long[].class);
        assertThat(as.getAttributeValueType(DOUBLE_ARRAY)).isEqualTo(double[].class);
        assertThat(as.getAttributeValueType(STRING_ARRAY)).isEqualTo(String[].class);
        assertThat(as.getAttributeValueType(BLOB_ARRAY)).isEqualTo(byte[][].class);
    }

    @Test
    public void test_overwrite() {
        final String key = "key";
        final AttributeSet as = new AttributeSet();
        as.setAttribute(key, "string");
        as.setAttribute(key, "different");

        assertThat(as.getStringAttribute(key)).isEqualTo("different");
    }

    @Test
    public void test_overwrite_changes_type() {
        final String key = "key";
        final AttributeSet as = new AttributeSet();
        as.setAttribute(key, "string");
        as.setAttribute(key, 20d);

        assertThat(as.getAttributeValueType(key)).isEqualTo(Double.class);
        assertThat(as.getDoubleAttribute(key)).isEqualTo(20d);
    }

    @Test
    public void test_contains_attribute() {
        final AttributeSet as = makeAttributeSet();

        BASIC_KEY_NAMES.forEach(key -> assertThat(as.containsAttribute(key)).isTrue());

        assertThat(as.containsAttribute("missing")).isFalse();
    }

    @Test
    public void test_get_attribute_names() {
        final AttributeSet as = makeAttributeSet();

        assertThat(as.getAttributeNames()).isEqualTo(BASIC_KEY_NAMES);
    }

    @Test
    public void test_clear() {
        final AttributeSet as = makeAttributeSet();
        as.clear();
        assertThat(as.getAttributeNames()).isEmpty();
    }

    @Test
    public void test_remove_attribute() {
        final AttributeSet as = makeAttributeSet();

        as.removeAttribute(LONG);
        assertThat(as.containsAttribute(LONG)).isFalse();

        final Set<String> keysWithoutLong = new HashSet<>(BASIC_KEY_NAMES);
        keysWithoutLong.remove(LONG);

        assertThat(as.getAttributeNames()).isEqualTo(keysWithoutLong);
    }

    @Test
    public void test_property_change_events() {
        final AttributeSet as = new AttributeSet();
        final AtomicInteger count = new AtomicInteger(0);

        as.addPropertyChangeListener(pce -> count.incrementAndGet());

        // Adding new attributes should fire PCEs
        fillAttributeSet(as);
        assertThat(count.get()).isEqualTo(BASIC_KEY_NAMES.size());

        count.set(0);

        // Setting equal values should not fire PCEs
        fillAttributeSet(as);
        assertThat(count.get()).isZero();

        count.set(0);

        // Changing the value should fire PCEs
        as.setAttribute(BOOL, !BOOL_VALUE);
        as.setAttribute(INT, INT_VALUE + 1);
        as.setAttribute(LONG, LONG_VALUE + 1);
        as.setAttribute(DOUBLE, DOUBLE_VALUE + 1);
        as.setAttribute(STRING, STRING_VALUE + 1);

        final byte[] newBlob = BLOB_VALUE.clone();
        newBlob[0] = -1;
        as.setAttribute(BLOB, newBlob);

        final int[] newIntArray = INT_ARRAY_VALUE.clone();
        newIntArray[0] = -1;
        as.setAttribute(INT_ARRAY, newIntArray);

        final long[] newLongArray = LONG_ARRAY_VALUE.clone();
        newLongArray[0] = -1;
        as.setAttribute(LONG_ARRAY, newLongArray);

        final double[] newDoubleArray = DOUBLE_ARRAY_VALUE.clone();
        newDoubleArray[0] = -1;
        as.setAttribute(DOUBLE_ARRAY, newDoubleArray);

        final String[] newStringArray = STRING_ARRAY_VALUE.clone();
        newStringArray[0] = "different";
        as.setAttribute(STRING_ARRAY, newStringArray);

        byte[][] newBlobArray = { BLOB_ARRAY_VALUE[0].clone(), BLOB_ARRAY_VALUE[1].clone() };
        newBlobArray[0][0] = -1;
        as.setAttribute(BLOB_ARRAY, newBlobArray);

        assertThat(count.get()).isEqualTo(BASIC_KEY_NAMES.size());

        count.set(0);

        // Removing attributes should fire PCEs
        BASIC_KEY_NAMES.forEach(as::removeAttribute);
        assertThat(count.get()).isEqualTo(BASIC_KEY_NAMES.size());
    }

    @Test
    public void test_removed_property_changed_listener_does_not_receive_event() {
        final AttributeSet attributeSet = new AttributeSet();
        final PropertyChangeListener listener = Mockito.mock(PropertyChangeListener.class);

        // register listener
        attributeSet.addPropertyChangeListener(listener);
        attributeSet.removePropertyChangeListener(listener);

        // modify attributes
        attributeSet.setAttribute("key", "value");

        Mockito.verify(listener, Mockito.never()).propertyChange(Mockito.any(PropertyChangeEvent.class));
    }

    @Test
    public void test_key_associated_property_changed() {
        final AttributeSet attributeSet = new AttributeSet();
        final PropertyChangeListener aListener = Mockito.mock(PropertyChangeListener.class);
        final PropertyChangeListener bListener = Mockito.mock(PropertyChangeListener.class);

        // register listeners
        attributeSet.addPropertyChangeListener("a", aListener);
        attributeSet.addPropertyChangeListener("b", bListener);

        // modify attributes
        attributeSet.setAttribute("a", "1");

        // unregister listeners
        attributeSet.removePropertyChangeListener("a", aListener);
        attributeSet.removePropertyChangeListener("b", bListener);

        // modify attributes
        attributeSet.setAttribute("a", "2");

        Mockito.verify(aListener, Mockito.times(1)).propertyChange(Mockito.any(PropertyChangeEvent.class));
        Mockito.verify(bListener, Mockito.never()).propertyChange(Mockito.any(PropertyChangeEvent.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_missing_attribute_throws_exception() {
        new AttributeSet().getIntAttribute("missing");
    }

    @Test(expected = NullPointerException.class)
    public void test_null_key_throws_exception() {
        new AttributeSet().setAttribute(null, 10);
    }

    @Test(expected = NullPointerException.class)
    public void test_null_string_value_throws_exception() {
        new AttributeSet().setAttribute(STRING, (String) null);
    }

    @Test(expected = NullPointerException.class)
    public void test_null_array_value_throws_exception() {
        new AttributeSet().setAttribute(BLOB, (byte[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void test_wrong_type_throws_exception() {
        final AttributeSet as = new AttributeSet();
        as.setAttribute(INT, INT_VALUE);
        as.getDoubleAttribute(INT);
    }

    /**
     * @return A new AttributeSet with basic attributes populated
     */
    private AttributeSet makeAttributeSet() {
        return fillAttributeSet(new AttributeSet());
    }

    /**
     * Fill the attribute set with standard values.
     */
    private AttributeSet fillAttributeSet(AttributeSet as) {
        as.setAttribute(BOOL, BOOL_VALUE);
        as.setAttribute(INT, INT_VALUE);
        as.setAttribute(LONG, LONG_VALUE);
        as.setAttribute(DOUBLE, DOUBLE_VALUE);
        as.setAttribute(STRING, STRING_VALUE);
        as.setAttribute(BLOB, BLOB_VALUE);
        as.setAttribute(INT_ARRAY, INT_ARRAY_VALUE);
        as.setAttribute(LONG_ARRAY, LONG_ARRAY_VALUE);
        as.setAttribute(DOUBLE_ARRAY, DOUBLE_ARRAY_VALUE);
        as.setAttribute(STRING_ARRAY, STRING_ARRAY_VALUE);
        as.setAttribute(BLOB_ARRAY, BLOB_ARRAY_VALUE);

        return as;
    }

    /**
     * Validate the attribute values against what is set in makeAttributeSet.
     */
    private void checkBasicTypes(AttributeSet as) {
        assertThat(as.getBooleanAttribute(BOOL)).isEqualTo(BOOL_VALUE);
        assertThat(as.getIntAttribute(INT)).isEqualTo(INT_VALUE);
        assertThat(as.getLongAttribute(LONG)).isEqualTo(LONG_VALUE);
        assertThat(as.getDoubleAttribute(DOUBLE)).isEqualTo(DOUBLE_VALUE);
        assertThat(as.getStringAttribute(STRING)).isEqualTo(STRING_VALUE);
        assertThat(as.getBinaryAttribute(BLOB)).isEqualTo(BLOB_VALUE);

        assertThat(as.getIntArrayAttribute(INT_ARRAY)).isEqualTo(INT_ARRAY_VALUE);
        assertThat(as.getLongArrayAttribute(LONG_ARRAY)).isEqualTo(LONG_ARRAY_VALUE);
        assertThat(as.getDoubleArrayAttribute(DOUBLE_ARRAY)).isEqualTo(DOUBLE_ARRAY_VALUE);
        assertThat(as.getStringArrayAttribute(STRING_ARRAY)).isEqualTo(STRING_ARRAY_VALUE);
        assertThat(as.getBinaryArrayAttribute(BLOB_ARRAY)).isEqualTo(BLOB_ARRAY_VALUE);
    }
}
