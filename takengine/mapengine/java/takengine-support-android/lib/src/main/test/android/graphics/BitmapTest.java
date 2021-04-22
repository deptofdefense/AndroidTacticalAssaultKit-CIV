package android.graphics;

import android.content.Context;
import android.content.res.Resources;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import static org.junit.Assert.*;

public class BitmapTest {

    @Test
    public void getWidth() {
        final int width = 100;
        final int height = 200;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Assert.assertEquals(width, bitmap.getWidth());
    }

    @Test
    public void getHeight() {
        final int width = 100;
        final int height = 200;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Assert.assertEquals(height, bitmap.getHeight());
    }

    @Test
    public void setHasAlpha() {
        final int width = 100;
        final int height = 200;
        Bitmap alpha = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        Assert.assertTrue(alpha.hasAlpha());
        alpha.setHasAlpha(false);
        Assert.assertFalse(alpha.hasAlpha());

        Bitmap noalpha = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);
        Assert.assertFalse(alpha.hasAlpha());
        alpha.setHasAlpha(true);
        Assert.assertTrue(alpha.hasAlpha());
    }

    @Test
    public void hasAlpha() {
        final int width = 100;
        final int height = 200;
        Bitmap alpha = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        Assert.assertTrue(alpha.hasAlpha());

        Bitmap noalpha = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);
        Assert.assertFalse(noalpha.hasAlpha());
    }

    @Test
    public void setPixel() {
        final int width = 100;
        final int height = 200;
        Bitmap bitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);

        final int pixel0 = bitmap.getPixel(bitmap.getWidth()/2, bitmap.getHeight()/2);
        assertEquals(0, pixel0);
        bitmap.setPixel(bitmap.getWidth()/2, bitmap.getHeight()/2, 0x1234567);
        final int pixel1 = bitmap.getPixel(bitmap.getWidth()/2, bitmap.getHeight()/2);
        assertEquals(0x1234567, pixel1);
    }

    @Test
    public void getPixels() {
        Bitmap b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
        for(int i = 0; i < (b.getWidth()*b.getHeight()); i++) {
            b.setPixel(i%b.getWidth(), i/b.getWidth(), i);
        }
        int[] pixels = new int[b.getWidth()*b.getHeight()];
        b.getPixels(pixels, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        for(int i = 0; i < (b.getWidth()*b.getHeight()); i++)
            assertEquals(i, pixels[i]);
    }

    @Test
    public void copyPixelsToBuffer() {
        int[] data = new int[] {
                0xFFFF0000, 0xFF00FF00,
                0xFF0000FF, 0xFFFFFFFF,
        };

        // ARGB_8888
        {
            Bitmap bitmap_argb8888 = Bitmap.createBitmap(data, 2, 2, Bitmap.Config.ARGB_8888);
            Assert.assertNotNull(bitmap_argb8888);

            ByteBuffer ibuffer = ByteBuffer.allocate(16);
            ibuffer.order(ByteOrder.BIG_ENDIAN);
            bitmap_argb8888.copyPixelsToBuffer(ibuffer);
            for (int i = 0; i < 4; i++)
                Assert.assertEquals(data[i], rgba2argb(ibuffer.getInt(i * 4)));
        }
        // RGB_565
        {
            Bitmap bitmap_rgb565 = Bitmap.createBitmap(data, 2, 2, Bitmap.Config.RGB_565);
            Assert.assertNotNull(bitmap_rgb565);

            ByteBuffer sbuffer = ByteBuffer.allocate(8);
            sbuffer.order(ByteOrder.BIG_ENDIAN);
            bitmap_rgb565.copyPixelsToBuffer(sbuffer);
            for (int i = 0; i < 4; i++) {
                int red = Color.red(data[i]);
                int green = Color.green(data[i]);
                int blue = Color.blue(data[i]);
                short packed = (short) (((red >> 3) << 11) | ((green >> 2) << 5) | (blue >> 3));
                Assert.assertEquals(packed, sbuffer.getShort(i * 2));
            }
        }
    }

    @Test
    public void copyPixelsToBuffer_roundtrip() throws IOException {
        Bitmap src = loadWorldMapBitmap();
        Assert.assertNotNull(src);

        ByteBuffer ibuffer = ByteBuffer.allocate(src.getWidth()*src.getHeight()*4);
        ibuffer.order(ByteOrder.LITTLE_ENDIAN);
        src.copyPixelsToBuffer(ibuffer);

        int[] argb = new int[src.getWidth()*src.getHeight()];
        ibuffer.order(ByteOrder.BIG_ENDIAN);
        ibuffer.asIntBuffer().get(argb);
        for(int i = 0; i < argb.length; i++)
            argb[i] = rgba2argb(argb[i]);

        Bitmap result = Bitmap.createBitmap(argb, src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        for(int y = 0; y < src.getHeight(); y++)
            for(int x = 0; x < src.getWidth(); x++)
                assertEquals(src.getPixel(x, y), result.getPixel(x, y));
    }

    @Test
    public void getConfig() {
        assertSame(Bitmap.Config.ARGB_8888, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).getConfig());
        assertSame(Bitmap.Config.RGB_565, Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565).getConfig());
    }

    @Test
    public void recycle() {
        final int width = 100;
        final int height = 200;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Assert.assertEquals(width, bitmap.getWidth());
    }

    @Test
    public void isRecycled() {
        final int width = 100;
        final int height = 200;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.recycle();
        Assert.assertTrue(bitmap.isRecycled());
    }

    @Test
    public void compress() throws IOException{
        Bitmap bitmap = loadWorldMapBitmap();

        compress_test_impl(bitmap, Bitmap.CompressFormat.JPEG, true);
        compress_test_impl(bitmap, Bitmap.CompressFormat.PNG, true);
        compress_test_impl(bitmap, null, false);
    }

    private void compress_test_impl(Bitmap bitmap, Bitmap.CompressFormat fmt, boolean expectCompress) throws IOException {
        File file = null;
        try {
            file = File.createTempFile("compress_test_impl", ".data");
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                final boolean wasCompressed = bitmap.compress(fmt, 100, fos);
                Assert.assertEquals(expectCompress, wasCompressed);
            } finally {
                if(fos != null)
                    fos.close();
            }
            if(expectCompress) {
                Bitmap uncompressed = BitmapFactory.decodeFile(file.getAbsolutePath(), null);
                Assert.assertNotNull(uncompressed);
                Assert.assertEquals(bitmap.getWidth(), uncompressed.getWidth());
                Assert.assertEquals(bitmap.getHeight(), uncompressed.getHeight());
            }
        } finally {
            if(file != null)
                file.delete();
        }
    }

    @Test
    public void createBitmap_empty() {
        final int width = 100;
        final int height = 200;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Assert.assertNotNull(bitmap);
        Assert.assertEquals(width, bitmap.getWidth());
        Assert.assertEquals(height, bitmap.getHeight());
    }

    @Test
    public void createBitmap_from_data() {
        int[] data = new int[] {
                0xFFFF0000, 0xFF00FF00,
                0xFF0000FF, 0xFFFFFFFF,
        };

        Bitmap bitmap = Bitmap.createBitmap(data, 2, 2, Bitmap.Config.ARGB_8888);
        Assert.assertNotNull(bitmap);
        for(int i = 0; i < 4; i++)
            Assert.assertEquals(data[i], bitmap.getPixel(i%2, i/2));
    }

    @Test
    public void createBitmap_from_source_with_transform() throws IOException {
        Bitmap bitmap = loadWorldMapBitmap();

        Matrix flipMatrix = new Matrix();
        flipMatrix.reset();
        flipMatrix.setScale(1f, -1f);

        Bitmap flippedBitmap = Bitmap.createBitmap(bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                flipMatrix,
                false);

        Assert.assertNotNull(flippedBitmap);
    }

    @Test
    public void createScaledBitmap() throws IOException {
        Bitmap bitmap = loadWorldMapBitmap();
        Assert.assertNotNull(bitmap);


        final int scaledw = bitmap.getWidth()/2;
        final int scaledh = bitmap.getHeight()/2;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, scaledw, scaledh, true);
        Assert.assertNotNull(scaled);
        Assert.assertEquals(scaledw, scaled.getWidth());
        Assert.assertEquals(scaledh, scaled.getHeight());
    }

    private static Bitmap loadWorldMapBitmap() throws IOException {
        Context context = new Context();
        Resources res = context.getResources();
        Assert.assertNotNull(res);

        int id = res.getIdentifier("worldmap_4326", "drawable", context.getPackageName());
        Assert.assertTrue(id > 0);

        Bitmap bitmap = null;
        InputStream strm = null;
        try {
            strm = res.openRawResource(id);
            Assert.assertNotNull(strm);

            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;

            bitmap = BitmapFactory.decodeStream(strm, null, opts);
        } finally {
            if(strm != null)
                strm.close();
        }

        Assert.assertNotNull(bitmap);
        return bitmap;
    }

    static int argb2rgba(int argb) {
        return (argb<<8)|((argb&0xFF000000)>>>24);
    }
    static int rgba2argb(int rgba) {
        return (rgba>>>8)|((rgba&0xFF)<<24);
    }
}