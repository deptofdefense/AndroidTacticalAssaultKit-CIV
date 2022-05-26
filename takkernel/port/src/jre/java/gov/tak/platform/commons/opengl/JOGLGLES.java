package gov.tak.platform.commons.opengl;

import javax.media.opengl.*;

import java.nio.*;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class JOGLGLES {
    private final static int NUM_CACHED_CONTEXTS = 4;

    private final static Set<InitializationHook> initHooks = Collections.newSetFromMap(new IdentityHashMap<>());

    final static class Context {
        Thread thread = Thread.currentThread();
        GLAutoDrawable source = null;
        GL2 gl2 = null;
        GL3bc gl3 = null;
        GL4bc gl4 = null;
    }

    final static ThreadLocal<Context> glThreadLocal = new ThreadLocal<Context>() {
        @Override
        protected Context initialValue() {
            return new Context();
        }
    };

    final static Context[] gl = new Context[NUM_CACHED_CONTEXTS];

    static Context context() {
        final Thread current = Thread.currentThread();
        for(int i = 0; i < NUM_CACHED_CONTEXTS; i++) {
            if(gl[i] == null)
                break;
            if(gl[i].thread == current)
                return gl[i];
        }
        return glThreadLocal.get();
    }

    public static GL2 gl2() {
        return context().gl2;
    }

    public static GL3bc gl3() {
        return context().gl3;
    }

    public static GL4bc gl4() {
        return context().gl4;
    }

    public synchronized static void init(GLAutoDrawable drawable) {
        final Context context = context();
        // cache in array
        for (int i = 0; i < gl.length; i++) {
            if(gl[i] == context)    break;
            if (gl[i] == null) {
                gl[i] = context;
                break;
            }
        }

        // run through reinit even if the drawable has not changed. JOGL will swap the `GL` backend
        // on context invalidation
        context.source = drawable;
        context.gl2 = null;
        context.gl3 = null;
        context.gl4 = null;

        GL gl = drawable.getGL();
        if(gl instanceof GL4bc)
            context.gl4 = (GL4bc) gl;
        else
            try {
                context.gl4 = gl.getGL4bc();
            } catch(Throwable t) {}
        if(gl instanceof GL3bc)
            context.gl3 = (GL3bc) gl;
        else
            try {
                context.gl3 = gl.getGL3bc();
            } catch(Throwable t) {}
        if(gl instanceof GL2)
            context.gl2 = (GL2) gl;
        else
            try {
                context.gl2 = gl.getGL2();
            } catch(Throwable t) {}

        if(context.gl4 != null) {
            // CPU source data is not allowed with `GL4`, so we need to proxy
            // any calls that attempt to do so until this usage is completely
            // removed from the codebase

            if(context.gl3 == null)
                context.gl3 = context.gl4;
        }

        // derive `gl2` from `gl3`
        if(context.gl2 == null)
            context.gl2 = context.gl3;
        for(InitializationHook hook : initHooks)
            hook.onInit(context.gl2);

        android.opengl.JOGLGLES.init(drawable);

        // clear references
        initHooks.clear();
    }

    public synchronized static void addInitializationHook(InitializationHook hook) {
        if(context().gl2 != null)   hook.onInit(context().gl2);
        else                        initHooks.add(hook);
    }

    static String glsl120Compatible(String src) {
        src = src.replaceAll("\\#version\\s+100", "#version 120");
        src = src.replaceAll("precision\\s+(medium|high|low)p\\s+(int|float)\\s*;", "");
        return src;
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

    static int offset(Buffer data) {
        if(data instanceof ByteBuffer) {
            return data.position();
        } else if(data instanceof ShortBuffer) {
            return data.position()*2;
        } else if(data instanceof IntBuffer) {
            return data.position()*4;
        } else if(data instanceof FloatBuffer) {
            return data.position()*4;
        } else {
            return 0;
        }
    }

    static int limit(Buffer data) {
        if(data instanceof ByteBuffer) {
            return data.limit();
        } else if(data instanceof ShortBuffer) {
            return data.limit()*2;
        } else if(data instanceof IntBuffer) {
            return data.limit()*4;
        } else if(data instanceof FloatBuffer) {
            return data.limit()*4;
        } else {
            return 0;
        }
    }

    public interface InitializationHook {
        void onInit(GL gl);
    }
}
