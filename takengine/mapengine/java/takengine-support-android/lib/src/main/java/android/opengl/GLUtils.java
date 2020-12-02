package android.opengl;

import android.graphics.Bitmap;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class GLUtils {
    private GLUtils() {}

    public static void texSubImage2D(int target, int level, int x, int y, Bitmap bitmap) {
        int format;
        int type;
        ByteBuffer data;
        switch(bitmap.getConfig()) {
            case RGB_565:
                format = GLES30.GL_RGB;
                type = GLES30.GL_UNSIGNED_SHORT_5_6_5;
                data = ByteBuffer.allocateDirect(bitmap.getWidth()*bitmap.getHeight()*2);
                data.order(ByteOrder.nativeOrder());
                bitmap.copyPixelsToBuffer(data);
                break;
            case ARGB_8888:
                format = GLES30.GL_RGBA;
                type = GLES30.GL_UNSIGNED_BYTE;
                data = ByteBuffer.allocateDirect(bitmap.getWidth()*bitmap.getHeight()*4);
                data.order(ByteOrder.nativeOrder());
                bitmap.copyPixelsToBuffer(data);
                break;
            default :
                Log.w("GLUtils", "texSubImage2D Bitmap.Config " + bitmap.getConfig() + " not supported.");
                return;
        }
        GLES30.glTexSubImage2D(target, level, x, y, bitmap.getWidth(), bitmap.getHeight(), format, type, data);
    }

    public static void texImage2D(int target, int level, int format, Bitmap bitmap, int type, int border) {
        ByteBuffer data;
        switch(bitmap.getConfig()) {
            case RGB_565:
                data = ByteBuffer.allocateDirect(bitmap.getWidth()*bitmap.getHeight()*2);
                data.order(ByteOrder.nativeOrder());
                bitmap.copyPixelsToBuffer(data);
                break;
            case ARGB_8888:
                data = ByteBuffer.allocateDirect(bitmap.getWidth()*bitmap.getHeight()*4);
                data.order(ByteOrder.nativeOrder());
                bitmap.copyPixelsToBuffer(data);
                break;
            default :
                Log.w("GLUtils", "texSubImage2D Bitmap.Config " + bitmap.getConfig() + " not supported.");
                return;
        }
        GLES30.glTexImage2D(target, level, format, bitmap.getWidth(), bitmap.getHeight(), border, format, type, data);
    }
}
