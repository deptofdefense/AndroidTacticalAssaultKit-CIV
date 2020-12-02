package android.opengl;

import com.jogamp.opengl.*;

import java.nio.*;

public final class JOGLGLES {
    public static GL2 gl2 = null;
    public static GL3bc gl3 = null;

    public static void init(GLAutoDrawable drawable) {
        GL gl = drawable.getGL();
        gl2 = drawable.getGL().getGL2();
        gl3 = drawable.getGL().getGL3bc();
    }

    static <T extends Buffer> T allocateDirect(int capacity, Class<T> buftype) {
        if(buftype == DoubleBuffer.class) {
            ByteBuffer retval = ByteBuffer.allocateDirect(capacity*8);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asDoubleBuffer());
        } else if(buftype == FloatBuffer.class) {
            ByteBuffer retval = ByteBuffer.allocateDirect(capacity*4);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asFloatBuffer());
        } else if(buftype == ShortBuffer.class) {
            ByteBuffer retval = ByteBuffer.allocateDirect(capacity*2);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asShortBuffer());
        } else if(buftype == IntBuffer.class) {
            ByteBuffer retval = ByteBuffer.allocateDirect(capacity*4);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asIntBuffer());
        } else if(buftype == LongBuffer.class) {
            ByteBuffer retval = ByteBuffer.allocateDirect(capacity*8);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asLongBuffer());
        } else if(buftype == CharBuffer.class) {
            ByteBuffer retval = ByteBuffer.allocateDirect(capacity*2);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asCharBuffer());
        } else if(buftype == ByteBuffer.class) {
            ByteBuffer retval = ByteBuffer.allocateDirect(capacity);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval);
        } else {
            throw new IllegalArgumentException();
        }
    }
}
