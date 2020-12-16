package com.atakmap.map;

import com.atakmap.coremap.log.Log;
import com.atakmap.interop.Pointer;
import com.atakmap.math.MathUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public abstract class Interop<T> {
    final static String TAG = "Interop";

    final static Map<Class<?>, Interop<?>> registry = new HashMap<>();

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
     * <P>The {@link Class} specified should be the base class or interface for the object hierarchy.
     * @param clazz
     * @param <T>
     * @return
     */
    public synchronized static <T> Interop<T> findInterop(Class<T> clazz) {
        Interop<?> retval = registry.get(clazz);
        if(retval != null)
            return (Interop<T>)retval;

        // if the specified class is not an interface, see if it exposes the desired methods
        if(!clazz.isInterface()) {
            try {
                retval = new ReflectInterop<T>(clazz, clazz);
            } catch(Throwable ignored) {

            }
        }
        if(retval == null) {
            // XXX - nested class names

            // loop up classname"Interop"
            try {
                Class<?> interopClazz = clazz.getClassLoader().loadClass(clazz.getName() + "Interop");
                retval = new ReflectInterop<T>(clazz, interopClazz);
            } catch(Throwable ignored) {}

            // look up "Native"classname
            try {
                Class<?> nativeClazz = clazz.getClassLoader().loadClass(clazz.getName().replace(clazz.getSimpleName(), "Native" + clazz.getSimpleName()));
                if(clazz.isAssignableFrom(nativeClazz)) {
                    retval = new ReflectInterop<T>(clazz, (Class<? extends T>)nativeClazz);
                }
            } catch(Throwable ignored) {}
        }

        if(retval != null)
            registry.put(clazz, retval);
        else
            Log.w(TAG, "Failed to find interop for " + clazz);

        return (Interop<T>)retval;
    }

    public synchronized static <T> void registerInterop(Class<T> clazz, Interop<T> interop) {
        registry.put(clazz, interop);
    }


    final static class ReflectInterop<T> extends Interop<T> {
        final static String TAG = "ReflectInterop";

        Class<?> target;
        Class<?> service;
        Method getPointer;
        Method create;
        Method clone;
        Method wrap;
        Method destruct;
        Method hasPointer;
        Method hasObject;
        Method getObject;

        ReflectInterop(Class<T> target, Class<?> service) {
            this.target = target;
            this.service = service;
            try {
                getPointer = service.getDeclaredMethod("getPointer", target);
                getPointer.setAccessible(true);
            } catch(Throwable t) {
                throw new IllegalArgumentException(t);
            }

            try {
                create = service.getDeclaredMethod("create", Pointer.class, Object.class);
                create.setAccessible(true);
            } catch(Throwable ignored) {}

            try {
                clone = service.getDeclaredMethod("clone", Long.TYPE);
                clone.setAccessible(true);
            } catch(Throwable ignored) {}

            try {
                wrap = service.getDeclaredMethod("wrap", target);
                wrap.setAccessible(true);
            } catch(Throwable ignored) {}

            try {
                destruct = service.getDeclaredMethod("destruct", Pointer.class);
                destruct.setAccessible(true);
            } catch(Throwable ignored) {}

            try {
                hasPointer = service.getDeclaredMethod("hasPointer", target);
                hasPointer.setAccessible(true);
            } catch(Throwable ignored) {}

            try {
                hasObject = service.getDeclaredMethod("hasObject", Long.TYPE);
                hasObject.setAccessible(true);
            } catch(Throwable ignored) {}

            try {
                getObject = service.getDeclaredMethod("getObject", Long.TYPE);
                getObject.setAccessible(true);
            } catch(Throwable ignored) {}

            if(!MathUtils.hasBits(getPointer.getModifiers(), Modifier.STATIC))
                throw new IllegalArgumentException();

            if(destruct != null && !MathUtils.hasBits(destruct.getModifiers(), Modifier.STATIC)) {
                destruct = null;
                clone = null;
                wrap = null;
            }

            if(create != null && !MathUtils.hasBits(create.getModifiers(), Modifier.STATIC)) {
                create = null;
            }

            if(clone != null && !MathUtils.hasBits(clone.getModifiers(), Modifier.STATIC)) {
                clone = null;
            }

            if(wrap != null && !MathUtils.hasBits(wrap.getModifiers(), Modifier.STATIC)) {
                wrap = null;
            }
        }

        @Override
        public long getPointer(T obj) {
            if(this.getPointer == null)
                throw new UnsupportedOperationException();
            try {
                return (Long) getPointer.invoke(null, obj);
            } catch(Throwable t) {
                Log.w(TAG, "getPointer() failed for " + service.getSimpleName(), t);
                return 0L;
            }
        }

        @Override
        public T create(Pointer pointer, Object owner) {
            if(this.create == null)
                throw new UnsupportedOperationException();
            try {
                return (T) create.invoke(null, pointer, owner);
            } catch(Throwable t) {
                Log.w(TAG, "create() failed for " + service.getSimpleName(), t);
                return null;
            }
        }

        @Override
        public Pointer clone(long pointer) {
            if(this.clone == null)
                throw new UnsupportedOperationException();
            try {
                return (Pointer) clone.invoke(null, pointer);
            } catch(Throwable t) {
                Log.w(TAG, "clone() failed for " + service.getSimpleName(), t);
                return null;
            }
        }

        @Override
        public Pointer wrap(T object) {
            if(this.wrap == null)
                throw new UnsupportedOperationException();
            try {
                return (Pointer) wrap.invoke(null, object);
            } catch(Throwable t) {
                Log.w(TAG, "wrap() failed for " + service.getSimpleName(), t);
                return null;
            }
        }

        @Override
        public void destruct(Pointer pointer) {
            try {
                destruct.invoke(null, pointer);
            } catch(Throwable t) {
                Log.w(TAG, "destruct() failed for " + service.getSimpleName(), t);
            }
        }

        @Override
        public boolean hasObject(long pointer) {
            if(this.wrap == null)
                throw new UnsupportedOperationException();
            try {
                return ((Boolean)hasObject.invoke(null, pointer)).booleanValue();
            } catch(Throwable t) {
                Log.w(TAG, "hasObject() failed for " + service.getSimpleName(), t);
                return false;
            }
        }

        @Override
        public T getObject(long pointer) {
            if(this.wrap == null)
                throw new UnsupportedOperationException();
            try {
                return (T) getObject.invoke(null, pointer);
            } catch(Throwable t) {
                Log.w(TAG, "getObject() failed for " + service.getSimpleName(), t);
                return null;
            }
        }

        @Override
        public boolean hasPointer(T object) {
            if(this.create == null)
                throw new UnsupportedOperationException();
            try {
                return ((Boolean)hasPointer.invoke(null, object)).booleanValue();
            } catch(Throwable t) {
                Log.w(TAG, "hasPointer() failed for " + service.getSimpleName(), t);
                return false;
            }
        }

        @Override
        public boolean supportsWrap() {
            return (this.wrap != null && this.destruct != null);
        }

        @Override
        public boolean supportsClone() {
            return (this.clone != null && this.destruct != null);
        }

        @Override
        public boolean supportsCreate() {
            return (this.create != null);
        }

        @Override
        public Class<?> getInteropClass() {
            return target;
        }
    }
}
