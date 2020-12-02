package android.graphics;

import android.content.Context;
import android.content.res.Resources;
import org.junit.Assert;
import org.junit.Test;

import java.io.InputStream;

public class BitmapDecoderTest {
    @Test
    public void decode_tiles()  throws Throwable {
        Context context = new Context();
        Resources res = context.getResources();
        Assert.assertNotNull(res);

        int id = res.getIdentifier("worldmap_4326", "drawable", context.getPackageName());
        Assert.assertTrue(id > 0);

        BitmapRegionDecoder decoder = null;
        InputStream strm = null;
        try {
            strm = res.openRawResource(id);
            Assert.assertNotNull(strm);

            decoder = BitmapRegionDecoder.newInstance(strm, false);
        } finally {
            if(strm != null)
                strm.close();
        }

        Assert.assertNotNull(decoder);
        Bitmap west = decoder.decodeRegion(new Rect(0, 0, 256, 256), null);
        Assert.assertNotNull(west);
        Assert.assertEquals(256, west.getWidth());
        Assert.assertEquals(256, west.getHeight());

        Assert.assertNotNull(decoder);
        Bitmap east = decoder.decodeRegion(new Rect(0, 0, 256, 256), null);
        Assert.assertNotNull(east);
        Assert.assertEquals(256, east.getWidth());
        Assert.assertEquals(256, east.getHeight());
    }
}