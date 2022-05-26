package com.atakmap.map.formats.c3dt;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;

import com.atakmap.coremap.log.Log;
import com.atakmap.nio.Buffers;

import java.io.File;
import java.nio.ByteBuffer;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class GLTF {
    private final static String TAG = "GLTF";

    long pointer;
    long vao = GLES30.GL_NONE;

    private GLTF(long ptr) {
        this.pointer = ptr;
    }

    public void bind() {
        if(vao == GLES30.GL_NONE) {
            vao = bindModel(pointer);
            if(vao != GLES30.GL_NONE)
                finalize();
        }
    }

    public void draw(boolean useShader, int u_mvp, double[] mvp) {
        draw(vao, useShader, u_mvp, mvp);
    }

    public void release() {
        if(vao != GLES30.GL_NONE) {
            releaseModel(vao);
            vao = GLES30.GL_NONE;
        }
    }

    public synchronized void finalize() {
        if(this.pointer == 0L)
            return;
        destroy(this.pointer);
        this.pointer = 0L;
    }

    public static GLTF parse(File file) {
        long ptr = createFromFile(file.getAbsolutePath());
        if(ptr == 0L)
            return null;
        else
            return new GLTF(ptr);
    }

    public static GLTF parse(ByteBuffer buf) {
        return parse(buf, null, null);
    }

    public static GLTF parse(ByteBuffer buf, String baseUri, ContentSource handler) {
        long ptr;
        if(buf.hasArray()) {
            int magic = buf.getInt(buf.position());
            ptr = createFromBytes(buf.array(), buf.position(), buf.remaining(), baseUri, handler);
            Buffers.skip(buf, buf.remaining());
        } else {
            byte[] data = new byte[buf.remaining()];
            buf.get(data);
            ptr = createFromBytes(data, 0, data.length, baseUri, handler);
        }
        if(ptr == 0L)
            return null;
        else
            return new GLTF(ptr);
    }

    private static int GLTF_COMPONENT_TYPE_UNSIGNED_BYTE = 5121;
    private static int GLTF_COMPONENT_TYPE_UNSIGNED_SHORT = 5123;
    private static int GLTF_COMPONENT_TYPE_UNSIGNED_INT = 5125;

    static GLTFBitmap loadExternalTextureBitmap(ContentSource handler, String uri) {
        try {
            return decode(ContentSources.getData(handler, uri, null, false));
        } catch(Throwable t) {
            Log.w(TAG, "Failed to load external texture using preferred handler", t);
            return null;
        }
    }

    private static GLTFBitmap decode(byte[] stream) {
        if(stream == null)
            return null;

        Bitmap bitmap = null;
        try {
            bitmap = BitmapFactory.decodeByteArray(stream, 0, stream.length);

            GLTFBitmap result = new GLTFBitmap();
            result.width = bitmap.getWidth();
            result.height = bitmap.getHeight();
            switch (bitmap.getConfig()) {
                case ALPHA_8:
                    result.bits = 8;
                    result.component = 1;
                    result.pixelType = GLTF_COMPONENT_TYPE_UNSIGNED_BYTE;
                    break;
                case RGB_565:
                    result.bits = 16;
                    result.component = 3;
                    result.pixelType = GLTF_COMPONENT_TYPE_UNSIGNED_SHORT;
                    break;
                case ARGB_4444:
                    result.bits = 4;
                    result.component = 4;
                    result.pixelType = GLTF_COMPONENT_TYPE_UNSIGNED_INT;
                    break;
                case ARGB_8888:
                    result.bits = 8;
                    result.component = 4;
                    result.pixelType = GLTF_COMPONENT_TYPE_UNSIGNED_INT;
                    break;
            }
            int size = bitmap.getRowBytes() * bitmap.getHeight();
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(size);
            bitmap.copyPixelsToBuffer(byteBuffer);
            result.bytes = byteBuffer;

            return result;
        } finally {
            if(bitmap != null)
                bitmap.recycle();
        }
    }

    private static native long createFromFile(String path);
    private static native long createFromBytes(byte[] arr, int off, int len, String baseDir, ContentSource handler);
    private static native void destroy(long ptr);
    private static native void draw(long ptr, boolean useShader, int u_mvp, double[] mv);
    private static native long bindModel(long ptr);
    private static native void releaseModel(long vao);
}
