package com.atakmap.interop;

import com.atakmap.lang.ref.Cleaner;
import com.atakmap.util.Disposable;
import com.atakmap.util.ReadWriteLock;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public final class NativeRunnable implements Runnable, Disposable {
    final static NativePeerManager.Cleaner CLEANER = new NativePeerManager.Cleaner() {
        @Override
        protected void run(Pointer pointer, Object opaque) {
            destruct(pointer);
        }
    };

    final ReadWriteLock rwlock = new ReadWriteLock();

    final Pointer pointer;
    final long runfn_ptr;
    final Object owner;
    private final Cleaner cleaner;

    NativeRunnable(Pointer pointer, long runfn_ptr, Object owner) {
        cleaner = NativePeerManager.register(this, pointer, rwlock, null, CLEANER);

        this.pointer = pointer;
        this.runfn_ptr = runfn_ptr;
        this.owner = owner;
    }

    @Override
    public void run() {
        this.rwlock.acquireRead();
        try {
            if(this.pointer.raw == 0L)
                throw new IllegalStateException();
            run(this.runfn_ptr, this.pointer.raw);
        } finally {
            this.rwlock.releaseRead();
        }
    }

    public void dispose() {
        if(this.cleaner != null)
            this.cleaner.clean();
    }

    static native void run(long runfn, long pointer);
    static native void destruct(Pointer pointer);
}
