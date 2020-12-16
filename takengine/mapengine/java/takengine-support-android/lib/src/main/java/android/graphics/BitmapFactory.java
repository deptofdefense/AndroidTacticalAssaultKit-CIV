package android.graphics;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;

public final class BitmapFactory {
    public final static class Options {
        public Bitmap.Config inPreferredConfig;
        public int outWidth;
        public int outHeight;
        public boolean inJustDecodeBounds;
        public float inDensity;
        public boolean inDither;
        public boolean inMutable;
        public boolean inInputShareable;
        public boolean inPreferQualityOverSpeed;
        public boolean inPurgeable;
        public boolean inScaled;
        public int inSampleSize;
        public int inScreenDensity;
        public int inTargetDensity;
        public boolean mCancel;
        public byte[] inTempStorage;
        public Bitmap inBitmap;

        public void requestCancelDecode() {
            throw new UnsupportedOperationException();
        }
    }

    private BitmapFactory() {}

    public static Bitmap decodeByteArray(byte[] data, int off, int len, BitmapFactory.Options opts) {
        try {
            return decodeStream(new ByteArrayInputStream(data, off, len), null, opts);
        } catch(IOException e) {
            return null;
        }
    }

    public static Bitmap decodeByteArray(byte[] data, int off, int len) {
        return decodeByteArray(data, off, len, null);
    }

    public static Bitmap decodeStream(InputStream stream) throws IOException {
        return decodeStream(stream, null, null);
    }

    public static Bitmap decodeStream(InputStream stream, Object ignored, BitmapFactory.Options opts) throws IOException {
        if(opts == null)
            opts = new BitmapFactory.Options();
        try {
            return onDecoded(ImageIO.read(stream), opts);
        } catch(Throwable t) {
            return null;
        }
    }

    public static Bitmap decodeFile(String file) throws IOException {
        return decodeFile(file, null);
    }

    public static Bitmap decodeFile(String file, BitmapFactory.Options opts) throws IOException {
        if(opts == null)
            opts = new BitmapFactory.Options();
        try {
            return onDecoded(ImageIO.read(new File(file)), opts);
        } catch(Throwable t) {
            return null;
        }
    }

    static Bitmap onDecoded(BufferedImage img, Options opts) {
        if(img == null)
            return null;
        if(opts != null) {
            if (opts.inScaled && opts.inSampleSize > 1) {
                Image scaled = img.getScaledInstance(Math.max(1, img.getWidth() >> (opts.inSampleSize - 1)), Math.max(1, img.getHeight() >> (opts.inSampleSize - 1)), opts.inDither ? BufferedImage.SCALE_SMOOTH : BufferedImage.SCALE_FAST);
                if (!(scaled instanceof BufferedImage)) {
                    BufferedImage cp = new BufferedImage(scaled.getWidth(null), scaled.getHeight(null), img.getType());
                    Graphics2D g2d = cp.createGraphics();
                    g2d.drawImage(scaled, 0, 0, null);
                    g2d.dispose();

                    scaled = cp;
                }

                img = (BufferedImage) scaled;
            }
            if (opts.inJustDecodeBounds) {
                opts.outWidth = img.getWidth();
                opts.outHeight = img.getHeight();
                return null;
            }
            // XXX - work around for `TileClient` not accepting
            //       `BitmapFactory.Options` so ARGB preferred config isn't
            //       coming through from AbstractTilePyramidTileReader
            if (opts.inPreferredConfig != null)
                img = Bitmap.makeCompatible(img, opts.inPreferredConfig);
            else
                img = Bitmap.makeCompatible(img, Bitmap.Config.ARGB_8888);
        }
        return new Bitmap(img);
    }
}
