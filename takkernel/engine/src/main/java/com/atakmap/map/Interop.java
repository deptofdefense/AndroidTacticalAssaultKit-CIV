package com.atakmap.map;

import com.atakmap.coremap.log.Log;
import com.atakmap.interop.Pointer;
import com.atakmap.math.MathUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import gov.tak.api.annotation.DeprecatedApi;
import gov.tak.api.marshal.IMarshal;
import gov.tak.platform.marshal.MarshalManager;

/** @deprecated use {@link com.atakmap.interop.Interop} */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public abstract class Interop<T> {
    static {
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)
                    return null;
                else if(in instanceof BackwardAdapter)
                    return (T)((BackwardAdapter)in)._impl;
                else
                    return (T)new ForwardAdapter((Interop)in);
            }
        }, Interop.class, com.atakmap.interop.Interop.class);
        MarshalManager.registerMarshal(new IMarshal() {
            @Override
            public <T, V> T marshal(V in) {
                if(in == null)
                    return null;
                else if(in instanceof ForwardAdapter)
                    return (T)((ForwardAdapter)in)._impl;
                else
                    return (T)new BackwardAdapter((com.atakmap.interop.Interop)in, null);
            }
        }, com.atakmap.interop.Interop.class, Interop.class);
    }

    /**
     * Obtains the pointer associated with the specified object. If the
     * implementation is pure Java, <code>0L</code> shall be returned.
     *
     * @param obj   An object
     *
     * @return  The pointer associated with the specified object, or
     *          <code>0L</code> if the object is a pure Java implementation.
     */
    public abstract long getPointer(T obj);
    public final T create(Pointer pointer) {
        return this.create(pointer, null);
    }
    public abstract T create(Pointer pointer, Object owner);
    public abstract Pointer clone(long pointer);

    /**
     * Wraps the specified instance of the base interface/class with a native
     * implementation and returns the allocated {@link Pointer}. It is likely
     * that the returned {@link Pointer} will be a <I>unique</I> pointer.
     *
     * @param object    The object
     *
     * @return  A {@link Pointer} to a native implementation of the
     *          corresponding base interface/class whose calls are serviced
     *          via the specified Java object.
     */
    public abstract Pointer wrap(T object);

    /**
     * Destructs the pointer memory for a {@link Pointer} instance returned by
     * {@link #clone(long)} or {@link #wrap(Object)}.
     *
     * <P>Results will be undefined (application will likely crash) if this
     * method is invoked on a {@link Pointer} that was returned from any
     * methods other than {@link #clone(long)} or {@link #wrap(Object)} on
     * the <code>Interop</code> instance for a given base interface/class.
     *
     * @param pointer   The pointer
     */
    public abstract void destruct(Pointer pointer);

    /**
     * Returns <code>true</code> if the specified pointer is wrapping a Java
     * Object, <code>false</code> otherwise.
     * @param pointer
     * @return
     */
    public abstract boolean hasObject(long pointer);

    /**
     * If the pointer is wrapping a Java Object, returns the wrapped Object.
     * @param pointer
     * @return
     */
    public abstract T getObject(long pointer);

    /**
     * Returns <code>true</code>  if the specified Object is wrapping a native
     * pointer, <code>false</code> otherwise.
     * @param object
     * @return
     */
    public abstract boolean hasPointer(T object);


    public abstract boolean supportsWrap();
    public abstract boolean supportsClone();
    public abstract boolean supportsCreate();

    public abstract Class<?> getInteropClass();

    /**
     * Performs a native cast of the pointer wrapped by <code>obj</code>.
     *
     * @param as    The managed type for the corresponding native pointer
     * @param obj   A managed object wrapping a native pointer
     * @return  A pointer to the native type corresponding to the managed class <code>as</code> or
     *          <code>0L</code> if the source object either 1) does not have a pointer or 2) the
     *          native type cannot be cast as desired.
     */
    public long castToPointer(Class<?> as, T obj) {
        return 0L;
    }

    /**
     * <P>The {@link Class} specified should be the base class or interface for the object hierarchy.
     * @param clazz
     * @param <T>
     * @return
     */
    public synchronized static <T> Interop<T> findInterop(Class<T> clazz) {
        final com.atakmap.interop.Interop<T> interop = com.atakmap.interop.Interop.findInterop(clazz);
        if(interop instanceof ForwardAdapter)
            return ((ForwardAdapter<T>)interop)._impl;
        else if(interop != null)
            return new BackwardAdapter<>(interop, clazz);
        else
            return null;
    }

    public synchronized static <T> void registerInterop(Class<T> clazz, Interop<T> interop) {
        com.atakmap.interop.Interop.registerInterop(clazz, new ForwardAdapter<>(interop));
    }

    final static class BackwardAdapter<T> extends Interop<T> {
        final com.atakmap.interop.Interop<T> _impl;
        final Class<?> _interopClass;

        BackwardAdapter(com.atakmap.interop.Interop<T> impl, Class<?> interopClass) {
            _impl = impl;
            _interopClass = interopClass;
        }

        @Override
        public long getPointer(T obj) {
            return _impl.getPointer(obj);
        }

        @Override
        public T create(Pointer pointer, Object owner) {
            return _impl.create(pointer, owner);
        }

        @Override
        public Pointer clone(long pointer) {
            return _impl.clone(pointer);
        }

        @Override
        public Pointer wrap(T object) {
            return _impl.wrap(object);
        }

        @Override
        public void destruct(Pointer pointer) {
            _impl.destruct(pointer);
        }

        @Override
        public boolean hasObject(long pointer) {
            return _impl.hasObject(pointer);
        }

        @Override
        public T getObject(long pointer) {
            return _impl.getObject(pointer);
        }

        @Override
        public boolean hasPointer(T object) {
            return _impl.hasPointer(object);
        }

        @Override
        public boolean supportsWrap() {
            return _impl.supportsWrap();
        }

        @Override
        public boolean supportsClone() {
            return _impl.supportsClone();
        }

        @Override
        public boolean supportsCreate() {
            return _impl.supportsCreate();
        }

        @Override
        public Class<?> getInteropClass() {
            return _interopClass;
        }

        @Override
        public long castToPointer(Class<?> as, T obj) {
            return _impl.castToPointer(as, obj);
        }
    }

    final static class ForwardAdapter<T> extends com.atakmap.interop.Interop<T> {
        final Interop<T> _impl;

        ForwardAdapter(Interop<T> impl) {
            _impl = impl;
        }

        @Override
        public long getPointer(T obj) {
            return _impl.getPointer(obj);
        }

        @Override
        public T create(Pointer pointer, Object owner) {
            return _impl.create(pointer, owner);
        }

        @Override
        public Pointer clone(long pointer) {
            return _impl.clone(pointer);
        }

        @Override
        public Pointer wrap(T object) {
            return _impl.wrap(object);
        }

        @Override
        public void destruct(Pointer pointer) {
            _impl.destruct(pointer);
        }

        @Override
        public boolean hasObject(long pointer) {
            return _impl.hasObject(pointer);
        }

        @Override
        public T getObject(long pointer) {
            return _impl.getObject(pointer);
        }

        @Override
        public boolean hasPointer(T object) {
            return _impl.hasPointer(object);
        }

        @Override
        public boolean supportsWrap() {
            return _impl.supportsWrap();
        }

        @Override
        public boolean supportsClone() {
            return _impl.supportsClone();
        }

        @Override
        public boolean supportsCreate() {
            return _impl.supportsCreate();
        }

        @Override
        public long castToPointer(Class<?> as, T obj) {
            return _impl.castToPointer(as, obj);
        }
    }
}
