
package com.atakmap.map;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.interop.Pointer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public abstract class AInteropTest<T> extends ATAKInstrumentedTest {
    private final Class<T> interopClass;
    private final boolean cloneSupported;
    private final boolean createdSupported;
    private final boolean wrapSupported;

    protected AInteropTest(Class<T> clazz, boolean expectSupportsClone,
            boolean expectSupportsCreate, boolean expectSupportsWrap) {
        interopClass = clazz;
        cloneSupported = expectSupportsClone;
        createdSupported = expectSupportsCreate;
        wrapSupported = expectSupportsWrap;
    }

    protected final Class<T> getInteropClass() {
        return interopClass;
    }

    protected final boolean expectSupportsClone() {
        return cloneSupported;
    }

    protected final boolean expectSupportsCreate() {
        return createdSupported;
    }

    protected final boolean expectSupportsWrap() {
        return wrapSupported;
    }

    protected abstract T createMockInstance();

    @Test
    public void interop_exists() {
        Interop<T> interop = Interop
                .findInterop(getInteropClass());
        assertNotNull(interop);
    }

    @Test
    public void validate_capabilities() {
        Interop<T> interop = Interop
                .findInterop(getInteropClass());
        assertEquals(expectSupportsClone(), interop.supportsClone());
        assertEquals(expectSupportsCreate(), interop.supportsCreate());
        assertEquals(expectSupportsWrap(), interop.supportsWrap());
    }

    // native wraps managed
    @Test
    public void managed_wrap_result_is_not_null() {
        Interop<T> interop = Interop
                .findInterop(getInteropClass());
        if (!interop.supportsWrap())
            return;

        Pointer pointer = null;
        try {
            pointer = interop.wrap(createMockInstance());
            assertNotNull(pointer);
        } finally {
            interop.destruct(pointer);
        }
    }

    @Test
    public void managed_wrap_has_pointer() {
        Interop<T> interop = Interop
                .findInterop(getInteropClass());
        if (!interop.supportsWrap())
            return;

        Pointer pointer = null;
        try {
            final T obj = createMockInstance();
            pointer = interop.wrap(obj);
            assertTrue(interop.hasObject(pointer.raw));
        } finally {
            interop.destruct(pointer);
        }
    }

    @Test
    public void managed_wrap_roundtrip_is_identity() {
        Interop<T> interop = Interop
                .findInterop(getInteropClass());
        if (!interop.supportsWrap())
            return;

        Pointer pointer = null;
        try {
            final T obj = createMockInstance();
            pointer = interop.wrap(obj);
            final T accessed = interop.getObject(pointer.raw);
            assertSame(obj, accessed);
        } finally {
            interop.destruct(pointer);
        }
    }

    // managed wraps native
    @Test
    public void native_wrap_result_is_not_null() {
        Interop<T> interop = Interop
                .findInterop(getInteropClass());
        if (!interop.supportsWrap())
            return;
        if (!interop.supportsCreate())
            return;

        // create the mock Object
        final T obj = createMockInstance();
        // wrap mock Object with native
        final Pointer pointer = interop.wrap(obj);
        // create an Object wrapper for the native object (ownership transferred)
        final T created = interop.create(pointer);
        assertNotNull(created);
    }

    @Test
    public void native_wrap_has_pointer() {
        Interop<T> interop = Interop
                .findInterop(getInteropClass());
        if (!interop.supportsWrap())
            return;
        if (!interop.supportsCreate())
            return;

        // create the mock Object
        final T obj = createMockInstance();
        // wrap mock Object with native
        final Pointer pointer = interop.wrap(obj);
        // create an Object wrapper for the native object (ownership transferred)
        final T created = interop.create(pointer);
        assertTrue(interop.hasPointer(created));
    }

    @Test
    public void native_wrap_roundtrip_is_identity() {
        Interop<T> interop = Interop
                .findInterop(getInteropClass());
        if (!interop.supportsWrap())
            return;
        if (!interop.supportsCreate())
            return;

        // create the mock Object
        final T obj = createMockInstance();
        // wrap mock Object with native
        final Pointer pointer = interop.wrap(obj);
        final long rawPointer = pointer.raw;
        // create an Object wrapper for the native object (ownership transferred)
        final T created = interop.create(pointer);

        // compare the original pointer
        assertEquals(rawPointer, interop.getPointer(created));
    }
}
