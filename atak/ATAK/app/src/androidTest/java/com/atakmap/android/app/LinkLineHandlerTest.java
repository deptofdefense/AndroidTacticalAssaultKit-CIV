
package com.atakmap.android.app;

import static org.junit.Assert.assertEquals;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.maps.DefaultMapGroup;
import com.atakmap.android.maps.MapEventDispatcher;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapGroupItemsChangedEventForwarder;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.Marker;
import com.atakmap.app.LinkLineHandler;
import com.atakmap.coremap.maps.coords.GeoPoint;

import org.junit.Assert;
import org.junit.Test;

public class LinkLineHandlerTest extends ATAKInstrumentedTest {

    String parentUid = "c39de798-43d7-4af4-a991-c3a5c2fb49a5";
    String childUid = "c39de798-43d7-4af4-a991-c3a5c2fb49a5.SPI";

    /**
     * it is successful when a white line is drawn on the map
     */
    @Test
    public void add_parent_and_childlink_simultaenously() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();

        MapItem parentItem = createParent(parentUid);
        MapItem childItem = createChild(childUid);
        boolean processResult = linkLineHandler.processLink(parentUid,
                parentItem, childUid, childItem);

        Assert.assertTrue(processResult);
    }

    @Test
    public void add_multiple_items() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        MapItem childItem = createChild(childUid);
        linkLineHandler.processLink(parentUid, null, childUid, childItem);
        int size = linkLineHandler.getNumDeferredLinks();
        assertEquals(1, size);

        itemListChangedListener.onItemAdded(childItem, linkGroup);
        size = linkLineHandler.getNumDeferredLinks();
        assertEquals(1, size);
        int linkSize = linkLineHandler.getNumLinks();
        assertEquals(0, linkSize);

        MapItem parentItem = createParent(parentUid);
        itemListChangedListener.onItemAdded(parentItem, linkGroup);
        //If succeeded, the list is now 0 in this example.
        size = linkLineHandler.getNumDeferredLinks();
        assertEquals(0, size);
        linkSize = linkLineHandler.getNumLinks();
        assertEquals(1, linkSize);
    }

    /**
     * Test to see if remove works correctly
     */
    @Test
    public void remove_link() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        linkLineTestObject.getLinkGroup();

        MapItem parentItem = createParent(parentUid);
        MapItem childItem = createChild(childUid);
        linkLineHandler.processLink(parentUid, parentItem, childUid, childItem);

        int size = linkLineHandler.getNumLinks();
        assertEquals(1, size);

        linkLineHandler.processLink(parentUid, parentItem, childUid, childItem);
        size = linkLineHandler.getNumLinks();
        assertEquals(0, size);
    }

    /**
     * it is successful when a white line is drawn on the map
     */
    @Test
    public void add_childlink_first() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        MapItem childItem = createChild(childUid);
        boolean processResult = linkLineHandler.processLink(parentUid, null,
                childUid, childItem);

        //beingMonitoredDeferred should have been called
        Assert.assertFalse(processResult);
        int size = linkLineHandler.getNumDeferredLinks();
        assertEquals(1, size);

        //add parent
        MapItem parentItem = createParent(parentUid);
        //trigger OnMapEvent in LinkLineHandler class
        itemListChangedListener.onItemAdded(parentItem, linkGroup);

        //If succeeded, the list is now 0 in this example.
        size = linkLineHandler.getNumDeferredLinks();
        assertEquals(0, size);
        int linkSize = linkLineHandler.getNumLinks();
        assertEquals(1, linkSize);
    }

    /**
     * it is successful when a white line is drawn on the map
     */
    @Test
    public void add_parentlink_first() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        MapItem parentItem = createParent(parentUid);
        boolean processResult = linkLineHandler.processLink(parentUid,
                parentItem, childUid, null);
        //beingMonitoredDeferred should have been called
        Assert.assertFalse(processResult);
        int size = linkLineHandler.getNumDeferredLinks();
        assertEquals(1, size);

        //add parent
        MapItem childItem = createChild(childUid);
        //trigger OnMapEvent in LinkLineHandler class
        itemListChangedListener.onItemAdded(childItem, linkGroup);

        //If succeeded, the list is now 0 in this example.
        size = linkLineHandler.getNumDeferredLinks();
        assertEquals(0, size);
        int linkSize = linkLineHandler.getNumLinks();
        assertEquals(1, linkSize);
    }

    /**
     * it is successful when a child with a link is removed before parent is drawn
     * (the child should no longer be tracked in deferred map)
     */
    @Test
    public void remove_childlink_without_parent() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        //add child first
        MapItem childItem = createChild(childUid);
        boolean processResult = linkLineHandler.processLink(parentUid, null,
                childUid, childItem);
        //beingMonitoredDeferred should have been called and listener added
        Assert.assertFalse(processResult);
        int size = linkLineHandler.getNumDeferredLinks();
        assertEquals(1, size);

        //remove child
        itemListChangedListener.onItemRemoved(childItem, linkGroup);

        //If succeeded, the list is now 0 in this example.
        size = linkLineHandler.getNumDeferredLinks();
        assertEquals(0, size);
    }

    /**
     * it is successful when a parent with a link is removed before child is drawn
     * (the parent should no longer be tracked in deferred map)
     */
    @Test
    public void remove_parentlink_without_child() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        //add parent first
        MapItem parentItem = createParent(parentUid);
        boolean processResult = linkLineHandler.processLink(parentUid,
                parentItem, childUid, null);
        //beingMonitoredDeferred should have been called
        Assert.assertFalse(processResult);
        int size = linkLineHandler.getNumDeferredLinks();
        assertEquals(1, size);

        //remove parent
        itemListChangedListener.onItemRemoved(parentItem, linkGroup);

        //If succeeded, the list is now 0 in this example.
        size = linkLineHandler.getNumDeferredLinks();
        assertEquals(0, size);
    }

    /**
     * it is successful when the number of items in list is expected
     * in the various steps of the process.
     */
    @Test
    public void add_childlink_first_one_to_many() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        //create children to main parent
        MapItem child1 = createChild(childUid);
        linkLineHandler.processLink(parentUid, null, childUid, child1);
        MapItem child2 = createChild("child2");
        boolean processResult = linkLineHandler.processLink(parentUid, null,
                "child2", child2);

        //add a completely different parent child relationship
        MapItem uniqueChild = createChild("uniquechild");
        linkLineHandler.processLink("uniqueparent", null, "uniquechild",
                uniqueChild);

        //beingMonitoredDeferred should have been called
        Assert.assertFalse(processResult);
        int size = linkLineHandler.getNumDeferredLinks();
        assertEquals(2, size);

        //add parent
        MapItem parentItem = createParent(parentUid);
        //trigger OnMapEvent in LinkLineHandler class
        itemListChangedListener.onItemAdded(parentItem, linkGroup);

        //If succeeded, the list is now 1 in this example.
        size = linkLineHandler.getNumDeferredLinks();
        assertEquals(1, size);

        int linkSize = linkLineHandler.getNumLinks();
        assertEquals(1, linkSize);
    }

    /**
     * it is successful when the number of items in deferred list is expected
     * in the various steps of the process.
     */
    @Test
    public void add_many_parentlinks_first() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        //add more than one parent
        MapItem parentItem = createParent(parentUid);
        linkLineHandler.processLink(parentUid, parentItem, childUid, null);
        MapItem parentItem2 = createParent("uniqueparent");
        boolean processResult = linkLineHandler.processLink("uniqueparent",
                parentItem2, "uniquechild", null);

        //beingMonitoredDeferred should have been called
        Assert.assertFalse(processResult);
        int size = linkLineHandler.getNumDeferredLinks();
        assertEquals(2, size);

        //add child
        MapItem childItem = createChild(childUid);
        //trigger OnMapEvent in LinkLineHandler class
        itemListChangedListener.onItemAdded(childItem, linkGroup);

        //If succeeded, the list is now 0 in this example.
        size = linkLineHandler.getNumDeferredLinks();
        assertEquals(1, size);
    }

    /**
     * it is successful when a child with a link is removed before parent is drawn
     * (the child should no longer be tracked in deferred map)
     */
    @Test
    public void remove_childlink_without_parent_one_to_many() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        //create children for the main parent
        MapItem child1 = createChild(childUid);
        linkLineHandler.processLink(parentUid, null, childUid, child1);
        MapItem child2 = createChild("child2");
        boolean processResult = linkLineHandler.processLink(parentUid, null,
                "child2", child2);

        //add a completely different parent child relationship
        MapItem uniqueChild = createChild("uniquechild");
        linkLineHandler.processLink("uniqueparent", null, "uniquechild",
                uniqueChild);

        //beingMonitoredDeferred should have been called
        Assert.assertFalse(processResult);
        int size = linkLineHandler.getNumDeferredLinks();
        assertEquals(2, size);

        //remove child
        itemListChangedListener.onItemRemoved(child2, linkGroup);

        //If succeeded, the list is now 2 in this example.
        size = linkLineHandler.getNumDeferredLinks();
        assertEquals(2, size);
    }

    /**
     * Child reference a different parent before parent exists
     */
    @Test
    public void child_changed_ref_to_parent_without_parent() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        MapItem childItem = createChild(childUid);
        linkLineHandler.processLink(parentUid, null, childUid, childItem);
        int size = linkLineHandler.getNumDeferredLinks();
        assertEquals(1, size);

        //change reference
        boolean processResult = linkLineHandler.processLink("newParent", null,
                childUid, childItem);
        //since it is still deferred.
        Assert.assertFalse(processResult);
        size = linkLineHandler.getNumDeferredLinks();
        //it should still be one
        assertEquals(1, size);
    }

    /**
     * Child references a new parent with parent already existing.
     */
    @Test
    public void child_changed_ref_to_parent_with_parent() {
        //setup
        LinkLineTestObject linkLineTestObject = new LinkLineTestObject();
        MapGroup.OnItemListChangedListener itemListChangedListener = linkLineTestObject
                .getItemListChangedListener();
        LinkLineHandler linkLineHandler = linkLineTestObject
                .getLinkLineHandler();
        MapGroup linkGroup = linkLineTestObject.getLinkGroup();

        MapItem parentItem = createParent(parentUid);
        MapItem childItem = createChild(childUid);
        linkLineHandler.processLink(parentUid, parentItem, childUid, childItem);

        //change reference.
        MapItem parentItem2 = createParent("parentitem2");
        boolean processResult2 = linkLineHandler.processLink("parentitem2",
                parentItem2, childUid, childItem);
        Assert.assertTrue(processResult2);
        int size = linkLineHandler.getNumLinks();
        assertEquals(1, size);
    }

    /**
     * Helper method to create parent map item
     * @param uid
     * @return
     */
    private MapItem createParent(String uid) {
        Marker parent = new Marker(
                new GeoPoint(52.86615067185124, -86.91452247074773), uid);
        MapItem parentItem = parent;
        parentItem.setType("a-f-A");
        return parent;
    }

    /**
     * helper method to create a child map item
     * @param uid
     * @return
     */
    private MapItem createChild(String uid) {
        Marker child = new Marker(
                new GeoPoint(36.94641520167652, -91.06088856070912), uid);
        MapItem childItem = child;
        childItem.setType("b-m-p-s-p-i");
        return childItem;
    }

    private static class LinkLineTestObject {
        private final MapEventDispatcher mapEventDispatcher = new MapEventDispatcher();
        MapGroupItemsChangedEventForwarder _mapGroupItemsChangedEventForwarder = new MapGroupItemsChangedEventForwarder(
                mapEventDispatcher);

        MapGroup linkGroup = new DefaultMapGroup("Link Lines");
        LinkLineHandler linkLineHandler = new LinkLineHandler(
                mapEventDispatcher, linkGroup);

        LinkLineTestObject() {
            linkGroup.addOnItemListChangedListener(
                    _mapGroupItemsChangedEventForwarder);
        }

        private MapGroup.OnItemListChangedListener getItemListChangedListener() {
            return _mapGroupItemsChangedEventForwarder;
        }

        private LinkLineHandler getLinkLineHandler() {
            return linkLineHandler;
        }

        private MapGroup getLinkGroup() {
            return linkGroup;
        }
    }
}
