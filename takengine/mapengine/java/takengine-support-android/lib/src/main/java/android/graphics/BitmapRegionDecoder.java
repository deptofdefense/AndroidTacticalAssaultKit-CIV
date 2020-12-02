package android.graphics;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public final class BitmapRegionDecoder {

    private BufferedImage image;

    private BitmapRegionDecoder(BufferedImage image) {
        this.image = image;
    }

    public Bitmap decodeRegion(Rect rect, BitmapFactory.Options opts) {
        BufferedImage result = this.image.getSubimage(rect.left, rect.top, (rect.right-rect.left), (rect.bottom-rect.top));
        return BitmapFactory.onDecoded(result, opts);
    }

    public void recycle() {
        this.image = null;
    }

    public static BitmapRegionDecoder newInstance(InputStream stream, boolean isShareable) throws IOException {
        BufferedImage image = ImageIO.read(stream);
        if(image == null)
            return null;
        return new BitmapRegionDecoder(image);
    }

    public static BitmapRegionDecoder newInstance(String path, boolean isShareable) throws IOException {
        BufferedImage image = ImageIO.read(new File(path));
        if(image == null)
            return null;
        return new BitmapRegionDecoder(image);
    }
}
