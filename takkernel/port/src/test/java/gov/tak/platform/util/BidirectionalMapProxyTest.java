package gov.tak.platform.util;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class BidirectionalMapProxyTest {
    @Test(expected = RuntimeException.class)
    public void nullKeyToValueMapThrows() {
        final Map<String, Integer> k2v = null;
        final Map<Integer, String> v2k = new HashMap<>();

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(k2v, v2k);
    }

    @Test(expected = RuntimeException.class)
    public void nonEmptyKeyToValueMapThrows() {
        final Map<String, Integer> k2v = new HashMap<>();
        k2v.put("1", new Integer(1));
        final Map<Integer, String> v2k = new HashMap<>();

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(k2v, v2k);
    }

    @Test(expected = RuntimeException.class)
    public void nullValueToKeyMapThrows() {
        final Map<String, Integer> k2v = new HashMap<>();
        final Map<Integer, String> v2k = null;

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(k2v, v2k);
    }

    @Test(expected = RuntimeException.class)
    public void nonEmptyValueToKeyMapThrows() {
        final Map<String, Integer> k2v = new HashMap<>();
        final Map<Integer, String> v2k = new HashMap<>();
        v2k.put(new Integer(1), "1");

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(k2v, v2k);
    }

    @Test
    public void emptyOnConstruct() {
        final String a = "1";
        final Integer b = new Integer(2);

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(new HashMap<>(), new HashMap<>());
        Assert.assertTrue(bmp.isEmpty());
        Assert.assertEquals(0, bmp.size());
        Assert.assertTrue(bmp.keySet().isEmpty());
        Assert.assertTrue(bmp.values().isEmpty());
        Assert.assertTrue(bmp.keyToValue().isEmpty());
        Assert.assertTrue(bmp.valueToKey().isEmpty());
    }

    @Test
    public void emptyMapPutNoCollide() {
        final String a = "1";
        final Integer b = new Integer(2);

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(new HashMap<>(), new HashMap<>());
        Assert.assertNull(bmp.put(a, b));
    }

    @Test
    public void putIsProxied() {
        final String a = "1";
        final Integer b = new Integer(2);

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(new HashMap<>(), new HashMap<>());
        bmp.put(a, b);
        Assert.assertFalse(bmp.isEmpty());
        Assert.assertEquals(1, bmp.size());

        Assert.assertSame(b, bmp.get(a));
        Assert.assertSame(b, bmp.keyToValue().get(a));

        Assert.assertSame(a, bmp.valueToKey().get(b));

        Assert.assertEquals(1, bmp.keyToValue().size());
        Assert.assertEquals(1, bmp.valueToKey().size());

        Assert.assertTrue(bmp.containsKey(a));
        Assert.assertTrue(bmp.keyToValue().containsKey(a));
        Assert.assertTrue(bmp.keySet().contains(a));

        Assert.assertTrue(bmp.containsValue(b));
        Assert.assertTrue(bmp.valueToKey().containsKey(b));
        Assert.assertTrue(bmp.values().contains(b));
    }

    @Test
    public void removeIsProxied() {
        final String a = "1";
        final Integer b = new Integer(2);

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(new HashMap<>(), new HashMap<>());
        bmp.put(a, b);
        Assert.assertSame(b, bmp.remove(a));
        Assert.assertTrue(bmp.isEmpty());
        Assert.assertEquals(0, bmp.size());

        Assert.assertTrue(bmp.keySet().isEmpty());
        Assert.assertTrue(bmp.keyToValue().isEmpty());
        Assert.assertEquals(0, bmp.keyToValue().size());
        Assert.assertTrue(bmp.values().isEmpty());
        Assert.assertTrue(bmp.valueToKey().isEmpty());
        Assert.assertEquals(0, bmp.valueToKey().size());
    }

    @Test
    public void clearIsProxied() {
        final String a = "1";
        final Integer b = new Integer(2);

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(new HashMap<>(), new HashMap<>());
        bmp.put(a, b);
        bmp.clear();

        Assert.assertTrue(bmp.isEmpty());
        Assert.assertEquals(0, bmp.size());

        Assert.assertTrue(bmp.keySet().isEmpty());
        Assert.assertTrue(bmp.keyToValue().isEmpty());
        Assert.assertEquals(0, bmp.keyToValue().size());
        Assert.assertTrue(bmp.values().isEmpty());
        Assert.assertTrue(bmp.valueToKey().isEmpty());
        Assert.assertEquals(0, bmp.valueToKey().size());
    }

    @Test
    public void putAllIsProxied() {
        Map<String, Integer> content = new HashMap<String, Integer>();
        content.put("1", Integer.valueOf(2));
        content.put("3", Integer.valueOf(4));

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(new HashMap<>(), new HashMap<>());
        bmp.putAll(content);
        Assert.assertFalse(bmp.isEmpty());
        Assert.assertEquals(content.size(), bmp.size());

        for(Map.Entry<String, Integer> entry : content.entrySet()) {
            Assert.assertTrue(bmp.containsKey(entry.getKey()));
            Assert.assertTrue(bmp.keyToValue().containsKey(entry.getKey()));
            Assert.assertSame(entry.getValue(), bmp.get(entry.getKey()));
            Assert.assertSame(entry.getValue(), bmp.keyToValue().get(entry.getKey()));

            Assert.assertTrue(bmp.containsValue(entry.getValue()));
            Assert.assertTrue(bmp.valueToKey().containsKey(entry.getValue()));
            Assert.assertSame(entry.getKey(), bmp.valueToKey().get(entry.getValue()));
        }
    }

    @Test
    public void entrySetRoundtrip() {
        Map<String, Integer> content = new HashMap<String, Integer>();
        content.put("1", Integer.valueOf(2));
        content.put("3", Integer.valueOf(4));

        BidirectionalMapProxy<String, Integer> bmp = new BidirectionalMapProxy<>(new HashMap<>(), new HashMap<>());
        bmp.putAll(content);
        Assert.assertFalse(bmp.entrySet().isEmpty());
        Assert.assertEquals(content.size(), bmp.entrySet().size());

        for(Map.Entry<String, Integer> entry : bmp.entrySet()) {
            Assert.assertSame(content.get(entry.getKey()), entry.getValue());
        }
    }
}
