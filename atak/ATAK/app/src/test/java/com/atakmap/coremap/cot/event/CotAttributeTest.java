
package com.atakmap.coremap.cot.event;

import org.junit.Test;
import static org.junit.Assert.*;

import com.atakmap.coremap.cot.event.CotAttribute;

/*
 Contract Questions:
 1) Should name constructor argument ever be null?
 */

public class CotAttributeTest {

    @Test
    public void test_cot_attribute_name() {

        CotAttribute attr = new CotAttribute("test_name", "test_value");

        assertNotNull(attr.getName());
        assertTrue(attr.getName().equals("test_name"));
    }

    @Test
    public void test_cot_attribute_value() {

        CotAttribute attr = new CotAttribute("test_name", "test_value");

        assertNotNull(attr.getValue());
        assertTrue(attr.getValue().equals("test_value"));
    }

    @Test
    public void test_cot_attribute_never_null_value() {

        CotAttribute attr = new CotAttribute("test_name", null);

        assertNotNull(attr.getValue());
        assertTrue(attr.getValue().equals(""));
    }
}
