package android.graphics;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.SampleModel;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public final class Bitmap {
    public enum Config {
        ARGB_8888,
        RGB_565,
        ARGB_4444,
        ALPHA_8,
    }

    public enum CompressFormat {
        PNG,
        JPEG,
    }

    private final static Map<CompressFormat, String> COMPRESS_FORMAT_NAMES = new HashMap<>();
    static {
        COMPRESS_FORMAT_NAMES.put(CompressFormat.PNG, "PNG");
        COMPRESS_FORMAT_NAMES.put(CompressFormat.JPEG, "JPG");
    }

    BufferedImage image;

    Bitmap(BufferedImage image) {
        this.image = makeCompatible(image);
    }

    public int getWidth() {
        return this.image.getWidth();
    }

    public int getHeight() {
        return this.image.getHeight();
    }

    public void setHasAlpha(boolean alpha) {
        if(hasAlpha() == alpha)
            return;
        final int type = alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_USHORT_565_RGB;
        BufferedImage swap = new BufferedImage(image.getWidth(), image.getHeight(),type);
        Graphics2D g2d = null;
        try {
            g2d = swap.createGraphics();
            g2d.drawImage(image, 0, 0, null);
        } finally {
            if(g2d != null)
                g2d.dispose();
        }
        image = swap;
    }

    public boolean hasAlpha() {
        return image.getColorModel().hasAlpha();
    }

    public void setPremultiplied(boolean b) {
    }

    public int getRowBytes() {
        final SampleModel sm = image.getSampleModel();
        final int transferType = sm.getTransferType();
        final int numDataElements = sm.getNumDataElements();
        switch(transferType) {
            case DataBuffer.TYPE_BYTE :
                return image.getWidth()*numDataElements;
            case DataBuffer.TYPE_SHORT :
            case DataBuffer.TYPE_USHORT :
                return image.getWidth()*numDataElements*2;
            case DataBuffer.TYPE_INT :
                return image.getWidth()*4;
            default :
                throw new IllegalStateException();
        }
    }

    public int getPixel(int x, int y) {
        return image.getRGB(x, y);
    }
    public void setPixel(int x, int y, int argb) {
        image.setRGB(x, y, argb);
    }

    public void getPixels(int[] argb, int offset, int stride, int x, int y, int width, int height) {
        image.getRGB(x, y, width, height, argb, offset, stride);
    }

    public void copyPixelsToBuffer(ByteBuffer buffer) {
        final SampleModel sm = image.getSampleModel();
        final int transferType = sm.getTransferType();
        final int numDataElements = sm.getNumDataElements();
        final int transferElements = image.getWidth()*image.getHeight()*numDataElements;
        switch(transferType) {
            case DataBuffer.TYPE_BYTE :
                byte[] bdata = new byte[transferElements];
                sm.getDataElements(0, 0, getWidth(), getHeight(), bdata, image.getData().getDataBuffer());
                buffer.duplicate().put(bdata);
                break;
            case DataBuffer.TYPE_SHORT :
            case DataBuffer.TYPE_USHORT :
                short[] sdata = new short[transferElements];
                sm.getDataElements(0, 0, getWidth(), getHeight(), sdata, image.getData().getDataBuffer());
                buffer.asShortBuffer().put(sdata);
                break;
            case DataBuffer.TYPE_INT :
                // XXX - actual pack order for android impl is RGBA, endian dependent, must swizzle
                // https://stackoverflow.com/questions/44500726/is-androids-argb-8888-bitmap-internal-format-always-rgba
                int[] idata = new int[transferElements];
                sm.getDataElements(0, 0, getWidth(), getHeight(), idata, image.getData().getDataBuffer());
                if(buffer.order() == ByteOrder.BIG_ENDIAN ) {
                    for (int i = 0; i < transferElements; i++)
                        idata[i] = (idata[i] << 8) | ((idata[i] >>> 24) & 0xFF);
                } else {
                    for (int i = 0; i < transferElements; i++) {
                        final int argb = idata[i];
                        idata[i] = (argb&0xFF000000)|((argb&0x00FF0000)>>16)|(argb&0x0000FF00)|((argb&0xFF)<<16);
                    }
                }

                buffer.asIntBuffer().put(idata);
                break;
            default :
                throw new IllegalStateException();
        }
    }

    public Config getConfig() {
        switch(image.getType()) {
            case BufferedImage.TYPE_INT_ARGB :
                return Config.ARGB_8888;
            case BufferedImage.TYPE_USHORT_565_RGB :
                return Config.RGB_565;
            default :
                return null;
        }

    }
    public void recycle() {
        image = null;
    }
    public boolean isRecycled() {
        return (image == null);
    }

    public boolean compress(CompressFormat format, int quality, OutputStream stream) {
        try {
           return ImageIO.write(this.image, COMPRESS_FORMAT_NAMES.get(format), stream);
        } catch(Throwable t) {
            return false;
        }
    }

    public static Bitmap createBitmap(int width, int height, Bitmap.Config config) {
        int type;
        switch(config) {
            case ARGB_8888:
                type = BufferedImage.TYPE_INT_ARGB;
                break;
            case RGB_565:
                type = BufferedImage.TYPE_USHORT_565_RGB;
                break;
            case ARGB_4444:
                type = BufferedImage.TYPE_INT_ARGB;
                break;
            default :
                throw new IllegalArgumentException();
        }
        return new Bitmap(new BufferedImage(width, height, type));
    }

    public static Bitmap createBitmap(int[] argb, int width, int height, Bitmap.Config config) {
        int type;
        switch(config) {
            case ARGB_8888:
                type = BufferedImage.TYPE_INT_ARGB;
                break;
            case RGB_565:
                type = BufferedImage.TYPE_USHORT_565_RGB;
                break;
            default :
                throw new IllegalArgumentException();
        }
        BufferedImage retval = new BufferedImage(width, height, type);
        retval.setRGB(0, 0, width, height, argb, 0, width);
        return new Bitmap(retval);
    }

    public static Bitmap createBitmap(Bitmap src, int srcx, int srcy, int width, int height, Matrix matrix, boolean filter) {
        if(matrix == null)
            return new Bitmap(src.image.getSubimage(srcx, srcy, width, height));

        RectF bnds = new RectF(srcx, srcy, srcx+width, srcy+height);
        matrix.mapRect(bnds);

        // XXX - documentation doesn't really indicate what resulting bitmap
        //       size will be, nor what it's origin is. assume that it remains
        //       the same, but the "vertical flip" case appears to change the
        //       origin to the min coord of the transformed space...

        final int dstOriginX = (int)bnds.left;
        final int dstOriginY = (int)bnds.top;
        final int dstWidth = (int)Math.abs(Math.ceil(bnds.right-bnds.left));
        final int dstHeight = (int)Math.abs(Math.ceil(bnds.top-bnds.bottom));

        BufferedImage image = new BufferedImage(dstWidth, dstHeight, src.image.getType());
        if(isVerticalFlip(matrix)) {
            int[] scan = new int[src.getWidth()];
            for(int y = 0; y < src.getHeight(); y++) {
                src.getPixels(scan, 0, src.getWidth(), 0, y, src.getWidth(), 1);
                image.setRGB(0, src.getHeight()-y-1, src.getWidth(), 1, scan, 0, src.getWidth());
            }
        } else if(matrix.isAffine()) {
            AffineTransform xform = new AffineTransform();
            xform.translate(-dstOriginX, -dstOriginY);
            float[] mx = new float[9];
            matrix.getValues(mx);
            xform.concatenate(new AffineTransform(mx[0], mx[1], mx[2], mx[3], mx[4], mx[5]));

            Graphics2D g2d = image.createGraphics();
            g2d.drawImage(src.image, xform, null);
            g2d.dispose();
        } else {
            float[] p = new float[2];
            for(int y = 0; y < src.getHeight(); y++) {
                for(int x = 0; x < src.getWidth(); x++) {
                    p[0] = x;
                    p[1] = y;
                    matrix.mapPoints(p);
                    p[0] -= dstOriginX;
                    p[1] -= dstOriginY;
                    if(p[0] < 0 || p[1] < 0 || p[0] >= dstWidth || p[1] >= dstHeight)
                        continue;
                    image.setRGB((int)p[0], (int)p[1], src.getPixel(x, y));
                }
            }
        }
        return new Bitmap(image);
    }

    public static Bitmap createScaledBitmap(Bitmap src, int width, int height, boolean filter) {
        BufferedImage img = new BufferedImage(width, height, src.image.getType());
        Graphics2D g2d = img.createGraphics();
        g2d.drawImage(src.image, 0, 0, width, height, 0, 0, src.getWidth(), src.getHeight(), null);
        g2d.dispose();
        return new Bitmap(img);
    }

    static BufferedImage makeCompatible(BufferedImage img) {
        return makeCompatible(img, img.getColorModel().hasAlpha() ? Config.ARGB_8888 : Config.RGB_565);
    }

    static BufferedImage makeCompatible(BufferedImage img, Bitmap.Config config) {
        if(config == null)
            throw new IllegalArgumentException();
        switch(config) {
            case ARGB_8888:
            case RGB_565:
                break;
            default :
                throw new IllegalArgumentException();
        }
        if(!(img.getColorModel() instanceof IndexColorModel)) {
            switch (img.getType()) {
                case BufferedImage.TYPE_INT_ARGB:
                    if(config != Config.ARGB_8888)
                        break;
                case BufferedImage.TYPE_USHORT_565_RGB:
                    if(config != Config.RGB_565)
                        break;
                    return img;
                default:
                    break;
            }
        }

        final int dstType = (config == Config.ARGB_8888) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_USHORT_565_RGB;
        BufferedImage dst = new BufferedImage(img.getWidth(), img.getHeight(), dstType);
        final Graphics2D g2d = dst.createGraphics();
        g2d.drawImage(img, 0, 0, null);
        g2d.dispose();
        return dst;
    }

    private static boolean isVerticalFlip(Matrix m) {
        float[] mx = new float[9];
        m.getValues(mx);
        return
            mx[0] == 1f && mx[1] == 0f && mx[2] == 0f &&
            mx[3] == 0f && mx[4] == -1f && mx[5] == 0f &&
            mx[6] == 0f && mx[7] == 0f && mx[8] == 1f;
    }
}
