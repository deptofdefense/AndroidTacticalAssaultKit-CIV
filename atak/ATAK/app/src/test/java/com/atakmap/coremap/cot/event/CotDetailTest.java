
package com.atakmap.coremap.cot.event;

import org.junit.Test;

import static org.junit.Assert.*;

/*
Contract Questions:
1) setAttribute() does not check for name being valid XML (or value for that matter) though
   javadoc says IllegalArgumentException should throw
 */

public class CotDetailTest {

    @Test
    public void test_cot_detail_default_state() {

        CotDetail detail = new CotDetail();

        // fields
        assertNotNull(detail.getElementName());
        assertTrue(detail.getElementName().equals("detail"));
        assertNull(detail.getInnerText());

        // attrs
        assertEquals(detail.getAttributeCount(), 0);
        assertNotNull(detail.getAttributes());
        assertEquals(detail.getAttributes().length, 0);
        assertNull(detail.getAttribute("bogus"));

        // children
        assertEquals(detail.childCount(), 0);
        assertNotNull(detail.getChildren());
        assertEquals(detail.getChildren().size(), 0);
        assertNull(detail.getChild(-1));
        assertEquals(detail.getChildrenByName("bogus").size(), 0);
        assertNull(detail.getFirstChildByName(-1, "bogus"));
    }

    @Test
    public void test_set_legal_attribute() {
        CotDetail detail = new CotDetail();
        detail.setAttribute("test_name", "test_value");

        assertEquals(detail.getAttributeCount(), 1);
        assertNotNull(detail.getAttributes());
        assertNotNull(detail.getAttribute("test_name"));
        assertNull(detail.getAttribute("bogus"));
        assertEquals(detail.getAttributes().length, 1);
        assertEquals(detail.getAttributes()[0].getName(), "test_name");
        assertEquals(detail.getAttributes()[0].getValue(), "test_value");
    }

    /*
    Contract violation reported in ATAK-12804
    @Test(expected = IllegalArgumentException.class)
    public void test_set_illegal_named_attribute() {
        CotDetail detail = new CotDetail();
        detail.setAttribute("\"", "test_value");
    }*/

}
