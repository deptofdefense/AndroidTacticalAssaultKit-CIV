package com.atakmap.map.layer;

import com.atakmap.interop.NativePeerManager;
import com.atakmap.interop.Pointer;
import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import java.util.IdentityHashMap;
import java.util.Map;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class NativeLayer implements Layer, Disposable {
    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            final Map<OnLayerVisibleChangedListener, Pointer>  listeners = (Map<OnLayerVisibleChangedListener, Pointer>)opaque;
            if(listeners != null) {
                synchronized (listeners) {
                    for (Pointer clistener : listeners.values())
                        removeOnLayerVisibleChangedListener(pointer.raw, clistener);
                    listeners.clear();
                }
            }

            destruct(pointer);
        }
    };

    final ReadWriteLock rwlock = new ReadWriteLock();
    private final Cleaner cleaner;
    Pointer pointer;
    Object owner;
    Map<OnLayerVisibleChangedListener, Pointer> listeners;

    NativeLayer(Pointer pointer, Object owner) {
        this.pointer = pointer;
        this.owner = owner;
        this.listeners = new IdentityHashMap<>();

        cleaner = NativePeerManager.register(this, pointer, rwlock, this.listeners, CLEANER);
    }

    @Override
    public void setVisible(boolean visible) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            setVisible(this.pointer.raw, visible);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public boolean isVisible() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return isVisible(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void addOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            synchronized (this.listeners) {
                if (this.listeners.containsKey(l))
                    return;
                this.listeners.put(l, addOnLayerVisibleChangedListener(this.pointer.raw, l));
            }
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public void removeOnLayerVisibleChangedListener(OnLayerVisibleChangedListener l) {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            final Pointer clistener;
            synchronized (this.listeners) {
                clistener = this.listeners.remove(l);
                if (l == null)
                    return;
            }
            removeOnLayerVisibleChangedListener(this.pointer.raw, clistener);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    @Override
    public String getName() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            return getName(this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }
    
    @Override
    public void dispose() {
        cleaner.clean();
    }

    /*************************************************************************/

    @DontObfuscate
    final static class NativeOnLayerVisibilityChangedListener implements Layer.OnLayerVisibleChangedListener {
        final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
            @Override
            protected void run(Pointer pointer, Object opaque) {
                VisibilityListener_destruct(pointer);
            }
        };

        Object owner;
        Pointer pointer;
        final ReadWriteLock rwlock = new ReadWriteLock();

        NativeOnLayerVisibilityChangedListener(Pointer pointer, Object owner) {
            NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

            this.pointer = pointer;
            this.owner = owner;
        }

        @Override
        public void onLayerVisibleChanged(Layer layer) {
            this.rwlock.acquireRead();
            try {
                if(this.pointer.raw == 0L)
                    return;
                VisibilityListener_visibilityChanged(this.pointer.raw, layer);
            } finally {
                this.rwlock.releaseRead();
            }
        }
    }

    /**********************************************************************************************/
    // Interop

    static native long getPointer(Layer object);
    static native Pointer wrap(Layer object);
    static native boolean hasPointer(Layer object);
    static native Layer create(Pointer pointer, Object ownerReference);
    static native boolean hasObject(long pointer);
    static native Layer getObject(long pointer);
    //static Pointer clone(long otherRawPointer);
    static native void destruct(Pointer pointer);

    // JNI implementation

    static native void setVisible(long ptr, boolean visible);
    static native boolean isVisible(long ptr);
    static native Pointer addOnLayerVisibleChangedListener(long ptr, OnLayerVisibleChangedListener l);
    static native void removeOnLayerVisibleChangedListener(long ptr, Pointer l);
    static native String getName(long ptr);

    static native void VisibilityListener_destruct(Pointer pointer);
    static native void VisibilityListener_visibilityChanged(long pointer, Layer layer);
}
