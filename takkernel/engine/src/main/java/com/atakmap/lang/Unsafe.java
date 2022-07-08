
package com.atakmap.lang;

import android.os.Build;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import com.atakmap.coremap.log.Log;
import com.atakmap.map.EngineLibrary;

/**
 * Static utility functions for working with native memory. Many methods are
 * <I>unchecked</I> and may perform significantly faster than comparable methods
 * on direct {@link java.nio.Buffer} objects. Unless specified otherwise, byte
 * order should always be considered to be in native order.
 *  
 * <P>The methods in this class work directly with native memory and are
 * considered <I>unsafe</I>. Incorrect use of the methods in this class can
 * produce unpredictable results and may result in crashing the application.
 *      
 * @author Developer
 */
public final class Unsafe {
    static {
        EngineLibrary.initialize();
    }
    
    private final static String TAG = "Unsafe";

    private static DirectByteBufferApi[] apis =
    {
        new DirectByteBuffer18(),
        new DirectByteBuffer23(),
        new DirectByteBuffer31(),
    };

    private static DirectByteBufferApi[] _api = null;

    private static boolean directByteBufferInit = false;

    private static Map<PhantomReference<Object>, Long> takDirectBufferRefs = new IdentityHashMap<PhantomReference<Object>, Long>();
    private static Map<Long, PhantomReference<Object>> takDirectBufferPtrs = new HashMap<Long, PhantomReference<Object>>();
    private static ReferenceQueue<Object> takDirectBuffers = new ReferenceQueue<Object>();

    private static DirectByteBufferApi api() {
        if(_api == null) {
            _api = new DirectByteBufferApi[1];
            for(int i = 0; i < apis.length; i++) {
                if(apis[i].isSupported()) {
                    _api[0] = apis[i];
                    break;
                }
            }
            if(_api[0] != null)
                Log.i(TAG, "Unsafe.allocateDirect lifetime management supported by " + _api[0].api);
            else
                Log.w(TAG, "Unsafe.allocateDirect lifetime management not supported");
        }
        return _api[0];
    }

    private Unsafe() {
    }

    /**
     * Allocates a block of memory. Memory allocated using this method should
     * always be freed using {@link #free(long)}. 
     *  
     * @param size  The size to allocate, in bytes.
     * 
     * @return  The pointer to the allocated memory.
     */
    public static native long allocate(int size);

    /**
     * Frees the previously allocated block of memory. This method should only
     * be used to free memory allocated by {@link #allocate(int)}.
     * 
     * @param pointer   The pointer to the memory.
     */
    public static native void free(long pointer);
    
    private static void freePhantomDirectBuffersNoSync() {
        do {
            Reference<? extends Object> finalized = null;
            try {
                finalized = takDirectBuffers.poll();
                if(finalized == null)
                    break;
                final Long derefPtr = takDirectBufferRefs.remove(finalized);
                finalized.clear();
                if(derefPtr != null) {
                    free(derefPtr.longValue());
                    takDirectBufferPtrs.remove(derefPtr);
                } else {
                    Log.w(TAG, "Unable to find pointer for direct allocated ByteBuffer, memory may be leaked");
                }
            } finally {
                if(finalized != null)
                    finalized.clear();
            }
        } while(true);
    }

    /**
     * Allocates a new {@link ByteBuffer} that is backed by a block of native
     * memory. As opposed to {@link ByteBuffer#allocateDirect(int)}, this method
     * may be able to do its allocation in such a way that it does not count
     * towards the JVM heap space.
     *  
     * @param capacity  The desired capacity in bytes.
     * 
     * @return  The direct allocated <code>ByteBuffer</code>. There is no need
     *          to explicitly free the returned object.
     * 
     * @see ByteBuffer#allocateDirect(int)
     */
    public static ByteBuffer allocateDirect(int capacity) {
        do {
            // if the allocated buffer is sufficiently small, we won't overcome
            // the tracking overhead, or if we can't track the pointer using
            // phantom references use the VM heap
            if(api() == null || capacity < 256)
                return ByteBuffer.allocateDirect(capacity);
            
            final Long ptr = Long.valueOf(allocate(capacity));
            if(ptr.longValue() == 0L)
                throw new OutOfMemoryError("Failed to allocate " + capacity + " bytes");
            final ByteBuffer retval = newDirectBuffer(ptr.longValue(), capacity);
            synchronized(takDirectBufferRefs) {
                freePhantomDirectBuffersNoSync();
                Object referent = api().getTrackingField(retval);
                if(referent == null) {
                    Log.e(TAG, "Failed to obtain DirectByteBuffer tracking field");
                    free(ptr.longValue());
                    return ByteBuffer.allocateDirect(capacity);
                }
                final PhantomReference<Object> ref = new PhantomReference<Object>(referent, takDirectBuffers);
                takDirectBufferRefs.put(ref, ptr);
                takDirectBufferPtrs.put(ptr, ref);
            }
    
            return retval;        
        } while(true);
    }

    public static <T extends Buffer> T allocateDirect(int capacity, Class<T> buftype) {
        if(buftype == DoubleBuffer.class) {
            ByteBuffer retval = allocateDirect(capacity*8);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asDoubleBuffer());
        } else if(buftype == FloatBuffer.class) {
            ByteBuffer retval = allocateDirect(capacity*4);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asFloatBuffer());
        } else if(buftype == ShortBuffer.class) {
            ByteBuffer retval = allocateDirect(capacity*2);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asShortBuffer());
        } else if(buftype == IntBuffer.class) {
            ByteBuffer retval = allocateDirect(capacity*4);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asIntBuffer());
        } else if(buftype == LongBuffer.class) {
            ByteBuffer retval = allocateDirect(capacity*8);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asLongBuffer());
        } else if(buftype == CharBuffer.class) {
            ByteBuffer retval = allocateDirect(capacity*2);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval.asCharBuffer());
        } else if(buftype == ByteBuffer.class) {
            ByteBuffer retval = allocateDirect(capacity);
            retval.order(ByteOrder.nativeOrder());
            return buftype.cast(retval);
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static native ByteBuffer newDirectBuffer(long pointer, int capacity);

    /**
     * Attempts to free the native memory associated with an NIO
     * {@link Buffer} allocated via or derived from a {@link ByteBuffer}
     * allocated  via {@link ByteBuffer#allocateDirect(int)}. While there is no
     * guarantee that this method will succeed, the caller should take care not
     * to invoke any methods on the provided buffer after the method returns.
     *  
     * @param buffer    A {@link Buffer} previously allocated via or derived
     *                  from a {@link ByteBuffer} allocated via
     *                  {@link ByteBuffer#allocateDirect(int)}.
     */
    public static void free(Buffer buffer) {
        if(buffer == null)
            return;
        
        synchronized(takDirectBufferRefs) {
            // take the opportunity to clean out any old references
            freePhantomDirectBuffersNoSync();

            // obtain the pointer
            final long ptr = getBufferPointer(buffer);
            if(ptr != 0L) {
                // if it's tracked, free it and return
                PhantomReference<Object> ref = null;
                try {
                    ref = takDirectBufferPtrs.remove(Long.valueOf(ptr));
                    if(ref != null) {
                        takDirectBufferRefs.remove(ref);
                        free(ptr);
                        return;
                    }
                } finally {
                    if(ref != null)
                        ref.clear();
                }
            }
        }

        if(api() == null)
            return;

        api().free(buffer);
    }
    
    //static Map<Class<?>, Field> byteBufferField = new HashMap<Class<?>, Field>();

    /**
     * Performs the <code>memset</code> C function on the specified pointers.
     * 
     * @param pointer   A pointer
     * @param value     The value
     * @param len       The number of bytes to be set to the specified value.
     */
    public static native void memset(long pointer, byte value, int len);

    /**
     * Performs the <code>memcpy</code> C function on the specified pointers.
     * 
     * @param dst   The destination pointer (copied to)
     * @param src   The source pointer (copied from)
     * @param len   The number of bytes to be copied.
     */
    public static native void memcpy(long dst, long src, int len);
    
    /**
     * Performs the <code>memcpy</code> C function on the specified pointers.
     * 
     * @param dst   The destination pointer (copied to)
     * @param src   The source pointer (copied from)
     * @param len   The number of bytes to be copied.
     */
    public static native void memmove(long dst, long src, int len);

    /**
     * Copies bytes from the specified pointer into the specified buffer. The
     * buffer must be direct and must have enough bytes remaining to accommodate
     * <code>len</code>.  The buffer's position will be updated before this
     * method returns to reflect the number of bytes copied.
     * 
     * @param dst   The destination buffer
     * @param src   The source pointer
     * @param len   The number of bytes to be copied
     */
    public static void memcpy(ByteBuffer dst, long src, int len) {
        if (!dst.isDirect())
            throw new IllegalArgumentException();
        if (dst.remaining() < len)
            throw new IllegalArgumentException();

        final int pos = dst.position();
        dst.position(pos+len);
        memcpy(dst, pos, src, len);
    }

    private static native void memcpy(ByteBuffer dst, int dstPos, long src, int len);

    /**
     * Copies bytes from the specified buffer into the specified pointer. The
     * buffer must be direct and must have enough bytes remaining to accommodate
     * <code>len</code>.  The buffer's position will be updated before this
     * method returns to reflect the number of bytes copied.
     * 
     * @param dst   The destination pointer
     * @param src   The source buffer
     * @param len   The number of bytes to be copied
     */
    public static void memcpy(long src, ByteBuffer dst, int len) {
        if (!dst.isDirect())
            throw new IllegalArgumentException();

        final int pos = dst.position();
        dst.position(pos+len);
        memcpy(src, dst, pos, len);
    }

    private static native void memcpy(long src, ByteBuffer dst, int dstPos, int len);
    
    /**
     * Returns the pointer for the specified buffer. The buffer must be direct.
     * This method will not return a pointer adjusted for the buffer's current
     * position.
     * 
     * @param directBuffer  The buffer
     * 
     * @return  The pointer to the native memory for the buffer.
     */
    public static native long getBufferPointer(Buffer directBuffer);

    /**
     * Returns the pointer to the relative position for the specified buffer.
     * The buffer must be direct.
     *
     * <B>WARNING:</B> This method may not produce the correct value for
     * {@link java.nio.Buffer Buffer} objects that are the result of
     * {@link java.nio.Buffer#slice() Buffer.slice()}.
     * 
     * @param buffer  The direct buffer
     * 
     * @return  The pointer to the native memory for the buffer, adjusted for
     *          the buffer's position.
     */
    public static long getRelativeBufferPointer(ByteBuffer buffer) {
        return getRelativeBufferPointer(buffer, 1);
    }
    
    /**
     * Returns the pointer to the relative position for the specified buffer.
     * The buffer must be direct.
     *
     * <B>WARNING:</B> This method may not produce the correct value for
     * {@link java.nio.Buffer Buffer} objects that are the result of
     * {@link java.nio.Buffer#slice() Buffer.slice()}.
     *
     * @param buffer  The direct buffer
     * 
     * @return  The pointer to the native memory for the buffer, adjusted for
     *          the buffer's position.
     */
    public static long getRelativeBufferPointer(ShortBuffer buffer) {
        return getRelativeBufferPointer(buffer, 2);
    }
    
    /**
     * Returns the pointer to the relative position for the specified buffer.
     * The buffer must be direct.
     *
     * <B>WARNING:</B> This method may not produce the correct value for
     * {@link java.nio.Buffer Buffer} objects that are the result of
     * {@link java.nio.Buffer#slice() Buffer.slice()}.
     *
     * @param buffer  The direct buffer
     * 
     * @return  The pointer to the native memory for the buffer, adjusted for
     *          the buffer's position.
     */
    public static long getRelativeBufferPointer(IntBuffer buffer) {
        return getRelativeBufferPointer(buffer, 4);
    }
    
    /**
     * Returns the pointer to the relative position for the specified buffer.
     * The buffer must be direct.
     *
     * <B>WARNING:</B> This method may not produce the correct value for
     * {@link java.nio.Buffer Buffer} objects that are the result of
     * {@link java.nio.Buffer#slice() Buffer.slice()}.
     *
     * @param buffer  The direct buffer
     * 
     * @return  The pointer to the native memory for the buffer, adjusted for
     *          the buffer's position.
     */
    public static long getRelativeBufferPointer(FloatBuffer buffer) {
        return getRelativeBufferPointer(buffer, 4);
    }
    
    /**
     * Returns the pointer to the relative position for the specified buffer.
     * The buffer must be direct.
     *
     * <B>WARNING:</B> This method may not produce the correct value for
     * {@link java.nio.Buffer Buffer} objects that are the result of
     * {@link java.nio.Buffer#slice() Buffer.slice()}.
     *
     * @param buffer  The direct buffer
     * 
     * @return  The pointer to the native memory for the buffer, adjusted for
     *          the buffer's position.
     */
    public static long getRelativeBufferPointer(LongBuffer buffer) {
        return getRelativeBufferPointer(buffer, 8);
    }

    /**
     * Returns the pointer to the relative position for the specified buffer.
     * The buffer must be direct.
     *
     * <B>WARNING:</B> This method may not produce the correct value for
     * {@link java.nio.Buffer Buffer} objects that are the result of
     * {@link java.nio.Buffer#slice() Buffer.slice()}.
     *
     * @param buffer  The direct buffer
     * 
     * @return  The pointer to the native memory for the buffer, adjusted for
     *          the buffer's position.
     */
    public static long getRelativeBufferPointer(DoubleBuffer buffer) {
        return getRelativeBufferPointer(buffer, 8);
    }
    
    private static long getRelativeBufferPointer(Buffer buffer, int elemSize) {
        return getBufferPointer(buffer)+(buffer.position()*elemSize);
    }

    public static ByteBuffer put(ByteBuffer buffer, byte v0) {
        final int pos = buffer.position();
        buffer.position(pos+1);
        setByteNio(buffer, pos, v0);
        return buffer;
    }
    
    public static ByteBuffer put(ByteBuffer buffer, byte v0, byte v1) {
        final int pos = buffer.position();
        buffer.position(pos+2);
        setBytesNio2(buffer, pos, v0, v1);
        return buffer;
    }
    
    public static ByteBuffer put(ByteBuffer buffer, byte v0, byte v1, byte v2) {
        final int pos = buffer.position();
        buffer.position(pos+3);
        setBytesNio3(buffer, pos, v0, v1, v2);
        return buffer;
    }
    
    public static ByteBuffer put(ByteBuffer buffer, byte v0, byte v1, byte v2, byte v3) {
        final int pos = buffer.position();
        buffer.position(pos+4);
        setBytesNio4(buffer, pos, v0, v1, v2, v3);
        return buffer;
    }
    
    public static ByteBuffer put(ByteBuffer buffer, byte[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length);
        setBytesNioArray(buffer, pos, arr, arr.length);
        return buffer;
    }

    public static ByteBuffer put(ByteBuffer buffer, byte[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len);
        setBytesNioArrayRegion(buffer, pos, arr, off, len);
        return buffer;
    }
    
    // short access

    public static ByteBuffer putShort(ByteBuffer buffer, short v0) {
        final int pos = buffer.position();
        buffer.position(pos+1*2);
        setShortNio(buffer, pos, v0);
        return buffer;
    }
    
    public static ByteBuffer putShorts(ByteBuffer buffer, short v0, short v1) {
        final int pos = buffer.position();
        buffer.position(pos+2*2);
        setShortsNio2(buffer, pos, v0, v1);
        return buffer;
    }
    
    public static ByteBuffer putShorts(ByteBuffer buffer, short v0, short v1, short v2) {
        final int pos = buffer.position();
        buffer.position(pos+3*2);
        setShortsNio3(buffer, pos, v0, v1, v2);
        return buffer;
    }
    
    public static ByteBuffer putShorts(ByteBuffer buffer, short v0, short v1, short v2, short v3) {
        final int pos = buffer.position();
        buffer.position(pos+4*2);
        setShortsNio4(buffer, pos, v0, v1, v2, v3);
        return buffer;
    }
    
    public static ByteBuffer putShorts(ByteBuffer buffer, short[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length*2);
        setShortsNioArray(buffer, pos, arr, arr.length);
        return buffer;
    }

    public static ByteBuffer putShorts(ByteBuffer buffer, short[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len*2);
        setShortsNioArrayRegion(buffer, pos, arr, off, len);
        return buffer;
    }
    
    public static ShortBuffer put(ShortBuffer buffer, short v0) {
        final int pos = buffer.position();
        buffer.position(pos+1);
        setShortNio(buffer, pos*2, v0);
        return buffer;
    }
    
    public static ShortBuffer put(ShortBuffer buffer, short v0, short v1) {
        final int pos = buffer.position();
        buffer.position(pos+2);
        setShortsNio2(buffer, pos*2, v0, v1);
        return buffer;
    }
    
    public static ShortBuffer put(ShortBuffer buffer, short v0, short v1, short v2) {
        final int pos = buffer.position();
        buffer.position(pos+3);
        setShortsNio3(buffer, pos*2, v0, v1, v2);
        return buffer;
    }
    
    public static ShortBuffer put(ShortBuffer buffer, short v0, short v1, short v2, short v3) {
        final int pos = buffer.position();
        buffer.position(pos+4);
        setShortsNio4(buffer, pos*2, v0, v1, v2, v3);
        return buffer;
    }
        
    public static ShortBuffer put(ShortBuffer buffer, short[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length);
        return put(buffer, pos, arr);
    }
    
    public static ShortBuffer put(ShortBuffer buffer, int pos, short[] arr) {
        setShortsNioArray(buffer, pos*2, arr, arr.length);
        return buffer;
    }

    public static ShortBuffer put(ShortBuffer buffer, short[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len);
        setShortsNioArrayRegion(buffer, pos*2, arr, off, len);
        return buffer;
    }
    
    
    // int access
    
    public static ByteBuffer putInt(ByteBuffer buffer, int v0) {
        final int pos = buffer.position();
        buffer.position(pos+1*4);
        setIntNio(buffer, pos, v0);
        return buffer;
    }
    
    public static ByteBuffer putInts(ByteBuffer buffer, int v0, int v1) {
        final int pos = buffer.position();
        buffer.position(pos+2*4);
        setIntsNio2(buffer, pos, v0, v1);
        return buffer;
    }
    
    public static ByteBuffer putInts(ByteBuffer buffer, int v0, int v1, int v2) {
        final int pos = buffer.position();
        buffer.position(pos+3*4);
        setIntsNio3(buffer, pos, v0, v1, v2);
        return buffer;
    }
    
    public static ByteBuffer putInts(ByteBuffer buffer, int v0, int v1, int v2, int v3) {
        final int pos = buffer.position();
        buffer.position(pos+4*4);
        setIntsNio4(buffer, pos, v0, v1, v2, v3);
        return buffer;
    }
    
    public static ByteBuffer putInts(ByteBuffer buffer, int[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length*4);
        setIntsNioArray(buffer, pos, arr, arr.length);
        return buffer;
    }

    public static ByteBuffer putInts(ByteBuffer buffer, int[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len*4);
        setIntsNioArrayRegion(buffer, pos, arr, off, len);
        return buffer;
    }
    
    public static IntBuffer put(IntBuffer buffer, int v0) {
        final int pos = buffer.position();
        buffer.position(pos+1);
        setIntNio(buffer, pos*4, v0);
        return buffer;
    }
    
    public static IntBuffer put(IntBuffer buffer, int v0, int v1) {
        final int pos = buffer.position();
        buffer.position(pos+2);
        setIntsNio2(buffer, pos*4, v0, v1);
        return buffer;
    }
    
    public static IntBuffer put(IntBuffer buffer, int v0, int v1, int v2) {
        final int pos = buffer.position();
        buffer.position(pos+3);
        setIntsNio3(buffer, pos*4, v0, v1, v2);
        return buffer;
    }
    
    public static IntBuffer put(IntBuffer buffer, int v0, int v1, int v2, int v3) {
        final int pos = buffer.position();
        buffer.position(pos+4);
        setIntsNio4(buffer, pos*4, v0, v1, v2, v3);
        return buffer;
    }
    
    public static IntBuffer put(IntBuffer buffer, int[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length);
        setIntsNioArray(buffer, pos*4, arr, arr.length);
        return buffer;
    }

    public static IntBuffer put(IntBuffer buffer, int[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len);
        setIntsNioArrayRegion(buffer, pos*4, arr, off, len);
        return buffer;
    }

    // long access
    
    public static ByteBuffer putLong(ByteBuffer buffer, long v0) {
        final int pos = buffer.position();
        buffer.position(pos+1*8);
        setLongNio(buffer, pos, v0);
        return buffer;
    }
    
    public static ByteBuffer putLongs(ByteBuffer buffer, long v0, long v1) {
        final int pos = buffer.position();
        buffer.position(pos+2*8);
        setLongsNio2(buffer, pos, v0, v1);
        return buffer;
    }
    
    public static ByteBuffer putLongs(ByteBuffer buffer, long v0, long v1, long v2) {
        final int pos = buffer.position();
        buffer.position(pos+3*8);
        setLongsNio3(buffer, pos, v0, v1, v2);
        return buffer;
    }
    
    public static ByteBuffer putLongs(ByteBuffer buffer, long v0, long v1, long v2, long v3) {
        final int pos = buffer.position();
        buffer.position(pos+4*8);
        setLongsNio4(buffer, pos, v0, v1, v2, v3);
        return buffer;
    }
    
    public static ByteBuffer putLongs(ByteBuffer buffer, long[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length*8);
        setLongsNioArray(buffer, pos, arr, arr.length);
        return buffer;
    }

    public static ByteBuffer putLongs(ByteBuffer buffer, long[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len*8);
        setLongsNioArrayRegion(buffer, pos, arr, off, len);
        return buffer;
    }
    
    public static LongBuffer put(LongBuffer buffer, long v0) {
        final int pos = buffer.position();
        buffer.position(pos+1);
        setLongNio(buffer, pos*8, v0);
        return buffer;
    }
    
    public static LongBuffer put(LongBuffer buffer, long v0, long v1) {
        final int pos = buffer.position();
        buffer.position(pos+2);
        setLongsNio2(buffer, pos*8, v0, v1);
        return buffer;
    }
    
    public static LongBuffer put(LongBuffer buffer, long v0, long v1, long v2) {
        final int pos = buffer.position();
        buffer.position(pos+3);
        setLongsNio3(buffer, pos*8, v0, v1, v2);
        return buffer;
    }
    
    public static LongBuffer put(LongBuffer buffer, long v0, long v1, long v2, long v3) {
        final int pos = buffer.position();
        buffer.position(pos+4);
        setLongsNio4(buffer, pos*8, v0, v1, v2, v3);
        return buffer;
    }
    
    public static LongBuffer put(LongBuffer buffer, long[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length);
        setLongsNioArray(buffer, pos*8, arr, arr.length);
        return buffer;
    }

    public static LongBuffer put(LongBuffer buffer, long[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len);
        setLongsNioArrayRegion(buffer, pos*8, arr, off, len);
        return buffer;
    }

    // float

    public static ByteBuffer putFloat(ByteBuffer buffer, float v0) {
        final int pos = buffer.position();
        buffer.position(pos+1*4);
        setFloatNio(buffer, pos, v0);
        return buffer;
    }
    
    public static ByteBuffer putFloats(ByteBuffer buffer, float v0, float v1) {
        final int pos = buffer.position();
        buffer.position(pos+2*4);
        setFloatsNio2(buffer, pos, v0, v1);
        return buffer;
    }
    
    public static ByteBuffer putFloats(ByteBuffer buffer, float v0, float v1, float v2) {
        final int pos = buffer.position();
        buffer.position(pos+3*4);
        setFloatsNio3(buffer, pos, v0, v1, v2);
        return buffer;
    }
    
    public static ByteBuffer putFloats(ByteBuffer buffer, float v0, float v1, float v2, float v3) {
        final int pos = buffer.position();
        buffer.position(pos+4*4);
        setFloatsNio4(buffer, pos, v0, v1, v2, v3);
        return buffer;
    }
    
    public static ByteBuffer putFloats(ByteBuffer buffer, float[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length*4);
        setFloatsNioArray(buffer, pos, arr, arr.length);
        return buffer;
    }

    public static ByteBuffer putFloats(ByteBuffer buffer, float[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len*4);
        setFloatsNioArrayRegion(buffer, pos, arr, off, len);
        return buffer;
    }

    public static FloatBuffer put(FloatBuffer buffer, float v0) {
        final int pos = buffer.position();
        buffer.position(pos+1);
        setFloatNio(buffer, pos*4, v0);
        return buffer;
    }
    
    public static FloatBuffer put(FloatBuffer buffer, float v0, float v1) {
        final int pos = buffer.position();
        buffer.position(pos+2);
        setFloatsNio2(buffer, pos*4, v0, v1);
        return buffer;
    }
    
    public static FloatBuffer put(FloatBuffer buffer, float v0, float v1, float v2) {
        final int pos = buffer.position();
        buffer.position(pos+3);
        setFloatsNio3(buffer, pos*4, v0, v1, v2);
        return buffer;
    }
    
    public static FloatBuffer put(FloatBuffer buffer, float v0, float v1, float v2, float v3) {
        final int pos = buffer.position();
        buffer.position(pos+4);
        setFloatsNio4(buffer, pos*4, v0, v1, v2, v3);
        return buffer;
    }
    
    public static FloatBuffer put(FloatBuffer buffer, float[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length);
        return put(buffer, pos, arr);
    }

    public static FloatBuffer put(FloatBuffer buffer, int pos, float[] arr) {
        setFloatsNioArray(buffer, pos*4, arr, arr.length);
        return buffer;
    }

    public static FloatBuffer put(FloatBuffer buffer, float[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len);
        setFloatsNioArrayRegion(buffer, pos*4, arr, off, len);
        return buffer;
    }
    
    // double access
    
    public static ByteBuffer putDouble(ByteBuffer buffer, double v0) {
        final int pos = buffer.position();
        buffer.position(pos+1*8);
        setDoubleNio(buffer, pos, v0);
        return buffer;
    }
    
    public static ByteBuffer putDoubles(ByteBuffer buffer, double v0, double v1) {
        final int pos = buffer.position();
        buffer.position(pos+2*8);
        setDoublesNio2(buffer, pos, v0, v1);
        return buffer;
    }
    
    public static ByteBuffer putDoubles(ByteBuffer buffer, double v0, double v1, double v2) {
        final int pos = buffer.position();
        buffer.position(pos+3*8);
        setDoublesNio3(buffer, pos, v0, v1, v2);
        return buffer;
    }
    
    public static ByteBuffer putDoubles(ByteBuffer buffer, double v0, double v1, double v2, double v3) {
        final int pos = buffer.position();
        buffer.position(pos+4*8);
        setDoublesNio4(buffer, pos, v0, v1, v2, v3);
        return buffer;
    }
    
    public static ByteBuffer putDoubles(ByteBuffer buffer, double[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length*8);
        setDoublesNioArray(buffer, pos, arr, arr.length);
        return buffer;
    }

    public static ByteBuffer putDoubles(ByteBuffer buffer, double[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len*8);
        setDoublesNioArrayRegion(buffer, pos, arr, off, len);
        return buffer;
    }
    
    public static DoubleBuffer put(DoubleBuffer buffer, double v0) {
        final int pos = buffer.position();
        buffer.position(pos+1);
        setDoubleNio(buffer, pos*8, v0);
        return buffer;
    }
    
    public static DoubleBuffer put(DoubleBuffer buffer, double v0, double v1) {
        final int pos = buffer.position();
        buffer.position(pos+2);
        setDoublesNio2(buffer, pos*8, v0, v1);
        return buffer;
    }
    
    public static DoubleBuffer put(DoubleBuffer buffer, double v0, double v1, double v2) {
        final int pos = buffer.position();
        buffer.position(pos+3);
        setDoublesNio3(buffer, pos*8, v0, v1, v2);
        return buffer;
    }
    
    public static DoubleBuffer put(DoubleBuffer buffer, double v0, double v1, double v2, double v3) {
        final int pos = buffer.position();
        buffer.position(pos+4);
        setDoublesNio4(buffer, pos*8, v0, v1, v2, v3);
        return buffer;
    }
    
    public static DoubleBuffer put(DoubleBuffer buffer, double[] arr) {
        final int pos = buffer.position();
        buffer.position(pos+arr.length);
        setDoublesNioArray(buffer, pos*8, arr, arr.length);
        return buffer;
    }

    public static DoubleBuffer put(DoubleBuffer buffer, double[] arr, int off, int len) {
        final int pos = buffer.position();
        buffer.position(pos+len);
        setDoublesNioArrayRegion(buffer, pos*8, arr, off, len);
        return buffer;
    }
    
    // NOTE: methods are named as-is to take advantage of macros

    private static native void setByteNio(Buffer buffer, int position, byte v0);
    private static native void setBytesNio2(Buffer buffer, int position, byte v0, byte v1);
    private static native void setBytesNio3(Buffer buffer, int position, byte v0, byte v1, byte v2);
    private static native void setBytesNio4(Buffer buffer, int position, byte v0, byte v1, byte v2, byte v3);
    private static native void setBytesNioArray(Buffer buffer, int position, byte[] arr, int len);
    private static native void setBytesNioArrayRegion(Buffer buffer, int position, byte[] arr, int off, int len);
    
    private static native void setShortNio(Buffer buffer, int position, short v0);
    private static native void setShortsNio2(Buffer buffer, int position, short v0, short v1);
    private static native void setShortsNio3(Buffer buffer, int position, short v0, short v1, short v2);
    private static native void setShortsNio4(Buffer buffer, int position, short v0, short v1, short v2, short v3);
    private static native void setShortsNioArray(Buffer buffer, int position, short[] arr, int len);
    private static native void setShortsNioArrayRegion(Buffer buffer, int position, short[] arr, int off, int len);
    
    private static native void setIntNio(Buffer buffer, int position, int v0);
    private static native void setIntsNio2(Buffer buffer, int position, int v0, int v1);
    private static native void setIntsNio3(Buffer buffer, int position, int v0, int v1, int v2);
    private static native void setIntsNio4(Buffer buffer, int position, int v0, int v1, int v2, int v3);
    private static native void setIntsNioArray(Buffer buffer, int position, int[] arr, int len);
    private static native void setIntsNioArrayRegion(Buffer buffer, int position, int[] arr, int off, int len);
    
    private static native void setLongNio(Buffer buffer, int position, long v0);
    private static native void setLongsNio2(Buffer buffer, int position, long v0, long v1);
    private static native void setLongsNio3(Buffer buffer, int position, long v0, long v1, long v2);
    private static native void setLongsNio4(Buffer buffer, int position, long v0, long v1, long v2, long v3);
    private static native void setLongsNioArray(Buffer buffer, int position, long[] arr, int len);
    private static native void setLongsNioArrayRegion(Buffer buffer, int position, long[] arr, int off, int len);
    
    private static native void setFloatNio(Buffer buffer, int position, float v0);
    private static native void setFloatsNio2(Buffer buffer, int position, float v0, float v1);
    private static native void setFloatsNio3(Buffer buffer, int position, float v0, float v1, float v2);
    private static native void setFloatsNio4(Buffer buffer, int position, float v0, float v1, float v2, float v3);
    private static native void setFloatsNioArray(Buffer buffer, int position, float[] arr, int len);
    private static native void setFloatsNioArrayRegion(Buffer buffer, int position, float[] arr, int off, int len);
    
    private static native void setDoubleNio(Buffer buffer, int position, double v0);
    private static native void setDoublesNio2(Buffer buffer, int position, double v0, double v1);
    private static native void setDoublesNio3(Buffer buffer, int position, double v0, double v1, double v2);
    private static native void setDoublesNio4(Buffer buffer, int position, double v0, double v1, double v2, double v3);
    private static native void setDoublesNioArray(Buffer buffer, int position, double[] arr, int len);
    private static native void setDoublesNioArrayRegion(Buffer buffer, int position, double[] arr, int off, int len);
    
    /**************************************************************************/
    // native pointer element access

    public static native byte getByte(long pointer);
    public static native short getShort(long pointer);
    public static native int getInt(long pointer);
    public static native long getLong(long pointer);
    
    public static native float getFloat(long pointer);
    public static native double getDouble(long pointer);
    
    public static native void setByte(long pointer, byte v);
    public static native void setBytes(long pointer, byte v0, byte v1);
    public static native void setBytes(long pointer, byte v0, byte v1, byte v3);
    public static native void setBytes(long pointer, byte v0, byte v1, byte v3, byte v4);
    
    public static native void setShort(long pointer, short v);
    public static native void setShorts(long pointer, short v0, short v1);
    public static native void setShorts(long pointer, short v0, short v1, short v3);
    public static native void setShorts(long pointer, short v0, short v1, short v3, short v4);
    
    public static native void setInt(long pointer, int v);
    public static native void setInts(long pointer, int v0, int v1);
    public static native void setInts(long pointer, int v0, int v1, int v3);
    public static native void setInts(long pointer, int v0, int v1, int v3, int v4);
    
    public static native void setLong(long pointer, long v);
    public static native void setLongs(long pointer, long v0, long v1);
    public static native void setLongs(long pointer, long v0, long v1, long v3);
    public static native void setLongs(long pointer, long v0, long v1, long v3, long v4);
    
    public static native void setFloat(long pointer, float v);
    public static native void setFloats(long pointer, float v0, float v1);
    public static native void setFloats(long pointer, float v0, float v1, float v2);
    public static native void setFloats(long pointer, float v0, float v1, float v2, float v3);

    public static native void setDouble(long pointer, double v);
    public static native void setDoubles(long pointer, double v0, double v1);
    public static native void setDoubles(long pointer, double v0, double v1, double v2);
    public static native void setDoubles(long pointer, double v0, double v1, double v2, double v3);

    private static ClassLoader getClassLoader(Class<?> c) {
        final ClassLoader classLoader = c.getClassLoader();
        return (classLoader != null) ? classLoader : ClassLoader.getSystemClassLoader();
    }
    private static abstract class DirectByteBufferApi {
        Boolean supported = null;
        Map<Class<?>, Field> byteBufferField = new HashMap<Class<?>, Field>();
        Class<?> DirectByteBuffer_class = null;
        final String api;


        protected DirectByteBufferApi(String api) {
            this.api = api;
        }

        public boolean isSupported() {
            if(supported == null) {
                // XXX - look up java.nio.DirectByteBuffer
                // XXX - look up java.nio.DirectByteBuffer$MemoryRef
                supported = Boolean.FALSE;
                try {
                    do {
                        DirectByteBuffer_class = getClassLoader(ByteBuffer.class).loadClass("java/nio/DirectByteBuffer");
                        if(DirectByteBuffer_class == null)
                            break;
                        supported = isSupportedImpl();
                    } while(false);
                } catch(Throwable ignored) {}
            }
            return supported;
        }
        public abstract Object getTrackingField(ByteBuffer buffer);
        public void free(Buffer buffer) {
            do {
                if(DirectByteBuffer_class.isAssignableFrom(buffer.getClass())) {
                    try {
                        this.freeImpl((ByteBuffer)buffer);
                    } catch(Throwable t) {
                        Log.w(TAG, "Failed to free DirectByteBuffer", t);
                    }
                } else {
                    Field byteBuffer = byteBufferField.get(buffer.getClass());
                    if(byteBuffer == null) {
                        try {
                            byteBuffer = this.getByteBufferField(buffer);
                            if(byteBuffer != null) {
                                byteBuffer.setAccessible(true);
                                byteBufferField.put(buffer.getClass(), byteBuffer);
                            }
                        } catch(Throwable ignored) {}
                    }

                    if(byteBuffer != null) {
                        try {
                            buffer = (Buffer)byteBuffer.get(buffer);
                            if(buffer != null)
                                continue;
                        } catch(Throwable ignored) {}
                    }

                }
                break;
            } while(true);
        }

        protected final static boolean checkField(Field f) throws IllegalAccessException {
            long octet = 0L;
            try {
                octet = allocate(1);
                ByteBuffer test = newDirectBuffer(octet, 1);
                final Object val = f.get(test);
                return (val != null);
            } finally {
                if(octet != 0L)
                    Unsafe.free(octet);
            }
        }

        protected abstract boolean isSupportedImpl() throws Throwable;

        /**
         * Executes the memory release function associated with the specified
         * <code>DirectByteBuffer</code> instance.
         *
         * @param buffer    An instance of <code>DirectByteBuffer</code>
         *
         * @throws Throwable    Any throwable that arises
         */
        protected abstract void freeImpl(ByteBuffer buffer) throws Throwable;
        protected abstract Field getByteBufferField(Buffer buffer) throws Throwable;
    }


    /**
     * DirectByteBuffer implementation detail for Android 12 (api 31).
     * (should be good back to pie, but an alternate/proven method already exists for pie - 11).
     * In these versions, DirectByteBuffer built using JNI NewDirectByteBuffer() (as we do in 
     * outer allocator) ties cleanup of the underlying buffer to the original
     * DirectByteBuffer instance, regardless of how many times said original Buffer is
     * sliced or otherwise used as a window into its internal buffer via other ByteBuffer instances.
     * The internal shared tracking holds open a strong reference to the original DirectByteBuffer
     * and clears it once the child/windowed instances have all ceased utilization.
     * This is in contrast to earlier versions, where no strong ref was held and instead it required
     * reflection acrobatics to get to the internal object (memory ref) that was shared across
     * the various views into the direct buffer memory so that cleanup could be held off until
     * all users were finished.
     */
    private static class DirectByteBuffer31 extends DirectByteBufferApi {

        DirectByteBuffer31() {
            super("Android 31");
        }

        @Override
        protected boolean isSupportedImpl() throws Throwable {
            if (Build.VERSION.SDK_INT < 31) 
                return false;

            return true;
        }

        @Override
        public Object getTrackingField(ByteBuffer buffer) {
            if (!supported || !DirectByteBuffer_class.isInstance(buffer))
                return null;
            return buffer;
        }

        @Override
        protected void freeImpl(ByteBuffer buffer) throws Throwable {
        }

        @Override
        protected Field getByteBufferField(Buffer buffer) throws Throwable {
            return null;
        }
    }




    /**
     * DirectByteBuffer implementation detail for Android 23
     */
    private static class DirectByteBuffer23 extends DirectByteBufferApi {
        Field DirectByteBuffer_memoryRef = null;
        Method DirectByteBuffer_cleaner = null;
        Class Cleaner_class = null;
        Method Cleaner_clean = null;

        DirectByteBuffer23() {
            super("Android 23");
        }

        @Override
        protected boolean isSupportedImpl() throws Throwable {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                DirectByteBuffer_memoryRef = DirectByteBuffer_class.getDeclaredField("memoryRef");
            } else {
                // the blacklist only checks to see the calling function and in this case
                // the reflection a second time makes the calling function is from the
                // system and not from this application.   Warn users for future SDK's
                // that this might not work when running debug versions - so it can be
                // checked.
                final Method xgetDeclaredField = Class.class
                        .getDeclaredMethod("getDeclaredField",
                                String.class);
                DirectByteBuffer_memoryRef = (Field) xgetDeclaredField.invoke(DirectByteBuffer_class,
                        "memoryRef");
            }
            if(DirectByteBuffer_memoryRef == null)
                return false;
            DirectByteBuffer_memoryRef.setAccessible(true);
            if(!checkField(DirectByteBuffer_memoryRef))
                return false;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                DirectByteBuffer_cleaner = DirectByteBuffer_class.getDeclaredMethod("cleaner");
            } else {
                // the blacklist only checks to see the calling function and in this case
                // the reflection a second time makes the calling function is from the
                // system and not from this application.   Warn users for future SDK's
                // that this might not work when running debug versions - so it can be
                // checked.
                final Method xgetDeclaredMethod = Class.class
                        .getDeclaredMethod("getDeclaredMethod",
                                String.class);
                DirectByteBuffer_cleaner = (Method) xgetDeclaredMethod.invoke(DirectByteBuffer_class,
                        "cleaner");
            }
            if(DirectByteBuffer_cleaner == null)
                return false;
            Cleaner_class = getClassLoader(ByteBuffer.class).loadClass("sun/misc/Cleaner");
            if(Cleaner_class == null)
                return false;
            Cleaner_clean = Cleaner_class.getDeclaredMethod("clean");
            if(Cleaner_clean == null)
                return false;

            return true;
        }

        @Override
        public Object getTrackingField(ByteBuffer buffer) {
            if(!supported)
                return null;
            try {
                return DirectByteBuffer_memoryRef.get(buffer);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Unexpected illegal access on tracking field, memoryRef", e);
                supported = Boolean.FALSE;

                return null;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unexpected illegal argument on tracking field, memoryRef", e);
                supported = Boolean.FALSE;

                return null;
            }
        }

        @Override
        protected void freeImpl(ByteBuffer buffer) throws Throwable {
            Object cleaner = DirectByteBuffer_cleaner.invoke(buffer);
            if(cleaner == null)
                return;
            Cleaner_clean.invoke(cleaner);
        }
        @Override
        protected Field getByteBufferField(Buffer buffer) throws Throwable {
            return buffer.getClass().getDeclaredField("bb");
        }
    }

    private static class DirectByteBuffer18 extends DirectByteBufferApi {
        private Class<?> MappedByteBuffer_class = null;
        private Field MappedByteBuffer_block = null;
        private Method DirectByteBuffer_free = null;

        DirectByteBuffer18() {
            super("Android 18");
        }

        @Override
        protected boolean isSupportedImpl() throws Throwable {
            MappedByteBuffer_class = getClassLoader(ByteBuffer.class).loadClass("java/nio/MappedByteBuffer");
            if(MappedByteBuffer_class == null)
                return false;
            MappedByteBuffer_block = MappedByteBuffer_class.getDeclaredField("block");
            if(MappedByteBuffer_block == null)
                return false;
            MappedByteBuffer_block.setAccessible(true);

            if(!checkField(MappedByteBuffer_block))
                return false;

            DirectByteBuffer_free = DirectByteBuffer_class.getDeclaredMethod("free");
            if(DirectByteBuffer_free == null)
                return false;

            return true;
        }

        @Override
        public Object getTrackingField(ByteBuffer buffer) {
            if(!supported)
                return null;
            try {
                return MappedByteBuffer_block.get(buffer);
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Unexpected illegal access on tracking field, memoryRef", e);
                supported = Boolean.FALSE;

                return null;
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Unexpected illegal argument on tracking field, memoryRef", e);
                supported = Boolean.FALSE;

                return null;
            }
        }

        @Override
        protected void freeImpl(ByteBuffer buffer) throws Throwable {
            DirectByteBuffer_free.invoke(buffer);
        }
        @Override
        protected Field getByteBufferField(Buffer buffer) throws Throwable {
            return buffer.getClass().getDeclaredField("byteBuffer");
        }
    }
}
