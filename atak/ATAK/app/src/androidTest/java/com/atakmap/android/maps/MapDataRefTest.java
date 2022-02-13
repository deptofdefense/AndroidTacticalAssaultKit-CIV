
package com.atakmap.android.maps;

import org.junit.Assert;
import org.junit.Test;

public class MapDataRefTest {

    @Test
    public void testAssetMapDataRef() {
        final String threeSlashUri = "asset:///icons/compass.png";
        final String twoSlashUri = threeSlashUri.replace("asset:///",
                "asset://");
        final MapDataRef mapDataRefThree = MapDataRef.parseUri(threeSlashUri);
        final MapDataRef mapDataRefTwo = MapDataRef.parseUri(twoSlashUri);
        Assert.assertNotNull(mapDataRefTwo);
        Assert.assertNotNull(mapDataRefThree);
        Assert.assertEquals(mapDataRefTwo.toUri(), mapDataRefThree.toUri());
        Assert.assertEquals(mapDataRefTwo.toUri(), twoSlashUri);
    }

    @Test
    public void testBase64MapDataRef() {
        final String threeSlashUri = "base64:///icons/compass.png";
        final String twoSlashUri = threeSlashUri.replace("base64:///",
                "base64://");
        final MapDataRef mapDataRefThree = MapDataRef.parseUri(threeSlashUri);
        final MapDataRef mapDataRefTwo = MapDataRef.parseUri(twoSlashUri);
        Assert.assertNotNull(mapDataRefTwo);
        Assert.assertNotNull(mapDataRefThree);
        Assert.assertEquals(mapDataRefTwo.toUri(), mapDataRefThree.toUri());
        Assert.assertEquals(mapDataRefTwo.toUri(), twoSlashUri);
    }
}
