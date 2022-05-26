package gov.tak.api.engine.map;

import com.atakmap.interop.Pointer;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class RenderContextInterop {
    private RenderContextInterop() {}

    static long getPointer(RenderContext object) {
        return 0L;
    }
    static native Pointer wrap(RenderContext object);
    static boolean hasPointer(RenderContext object) {
        return false;
    }
    //static RenderContext create(Pointer pointer, Object ownerReference);
    static native boolean hasObject(long pointer);
    static native RenderContext getObject(long pointer);
    //static Pointer clone(long otherRawPointer);
    static native void destruct(Pointer pointer);

}
