package com.atakmap.interop;

import com.atakmap.coremap.log.Log;
import com.atakmap.util.Collections2;
import com.atakmap.util.ReadWriteLock;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public final class NativePeerManager {

    private final static String TAG = "NativePeerManager";
    
    private static boolean trackInfo = false;

    private static final int INFO_TOTAL_ALLOCS_IDX = 0;
    private static final int INFO_LIVE_ALLOCS_IDX = 1;
    private static final int INFO_LIVE_REFS_IDX = 2;

    private static final int MAX_CLEANER_THREADS = 4;

    private static ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
    private static Map<Class<?>, int[]> nativeAllocationsInfo = new HashMap<>();

    private static List<Thread> cleanerThreads = new LinkedList<>();
    private final static CleanerThread worker = new CleanerThread();
    private final static Set<CleanerReference> activeReferences = Collections2.newIdentityHashSet();

    public static synchronized com.atakmap.lang.ref.Cleaner register(Object managed, Pointer pointer, ReadWriteLock guard, Object opaque, Cleaner cleaner) {
        if(managed == null)
            return null;
        if(pointer == null || pointer.raw == 0L)
            return null;

        final CleanerReference ref;
        if(trackInfo)
            ref = new TrackingCleanerReference(managed, new NativePeer(pointer, guard), opaque, cleaner, referenceQueue);
        else
            ref = new CleanerReference(managed, new NativePeer(pointer, guard), opaque, cleaner, referenceQueue);
        activeReferences.add(ref);

        if(cleanerThreads.isEmpty())
            spawnCleanerThread();

        return ref;
    }

    private static void spawnCleanerThread() {
        Thread t = new Thread(worker);
        t.setName("NativePeerManager-CleanerThread@" + Integer.toString(t.hashCode(), 16));
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        cleanerThreads.add(t);
        t.start();
    }

    public static synchronized void dumpInfo() {
        Log.i(TAG, "Native Allocation Info");
        Log.i(TAG, "Total Live Allocations: " + activeReferences.size());
        if(trackInfo) {
            Log.i(TAG, "Managed Classes using Native Peers: " + nativeAllocationsInfo.size());
            SortedSet<Class<?>> managedClassKeys = new TreeSet<Class<?>>(new Comparator<Class<?>>() {
                @Override
                public int compare(Class<?> a, Class<?> b) {
                    return a.getName().compareTo(b.getName());
                }
            });
            managedClassKeys.addAll(nativeAllocationsInfo.keySet());

            for (Class<?> c : managedClassKeys) {
                int[] info = nativeAllocationsInfo.get(c);
                Log.i(TAG, "Managed class " + c + "   total allocs: " + info[INFO_TOTAL_ALLOCS_IDX] + " live allocs: " + info[INFO_LIVE_ALLOCS_IDX] + " refs: " + info[INFO_LIVE_REFS_IDX]);
            }
        }
    }

    public static abstract class Cleaner {
        public final void run(Pointer pointer, Object opaque, ReadWriteLock guard) {
            if(guard != null)
                guard.acquireWrite();
            try {
                if(pointer.value != 0L)
                    this.run(pointer, opaque);
            } finally {
                if(guard != null)
                    guard.releaseWrite();
            }
        }

        protected abstract void run(Pointer pointer, Object opaque);
    }

    static class CleanerReference extends PhantomReference<Object> implements com.atakmap.lang.ref.Cleaner {
        final NativePeer nativePeer;
        final Object opaque;
        final Cleaner cleaner;

        public CleanerReference(Object r, NativePeer nativePeer, Object opaque, Cleaner cleaner, ReferenceQueue<? super Object> q) {
            super(r, q);

            this.nativePeer = nativePeer;
            this.opaque = opaque;
            this.cleaner = cleaner;
        }

        @Override
        public void clean() {
            if(nativePeer.pointer.raw != 0L && cleaner != null)
                cleaner.run(nativePeer.pointer, opaque, nativePeer.guard);
        }
    }

    static class TrackingCleanerReference extends CleanerReference {
        int[] info;

        public TrackingCleanerReference(Object r, NativePeer nativePeer, Object opaque, Cleaner cleaner, ReferenceQueue<? super Object> q) {
            super(r, nativePeer, opaque, cleaner, q);

            info = nativeAllocationsInfo.get(r.getClass());
            if(info == null)
                nativeAllocationsInfo.put(r.getClass(), info=new int[3]);
            info[INFO_TOTAL_ALLOCS_IDX]++;
            if(nativePeer.pointer.type == Pointer.REFERENCE)
                info[INFO_LIVE_REFS_IDX]++;
            else
                info[INFO_LIVE_ALLOCS_IDX]++;
        }

        @Override
        public void clean() {
            super.clean();

            synchronized(NativePeerManager.class) {
                // remove the cleaner
                if(!activeReferences.remove(this))
                    return;

                // update the statistics
                if(nativePeer.pointer.type == Pointer.REFERENCE)
                    info[INFO_LIVE_REFS_IDX]--;
                else
                    info[INFO_LIVE_ALLOCS_IDX]--;
            }
        }
    }

    final static class NativePeer {
        final Pointer pointer;
        final ReadWriteLock guard;

        public NativePeer(Pointer pointer, ReadWriteLock guard) {
            this.pointer = pointer;
            this.guard = guard;
        }
    }

    final static class CleanerThread implements Runnable {
        @Override
        public void run() {
            // initial wait is infinite, since at least one item must be in the queue when the
            // thread is spawned
            long wait = 0;
            while(true) {
                CleanerReference ref;
                try {
                    ref = (CleanerReference)referenceQueue.remove(wait);
                } catch(InterruptedException e) {
                    continue;
                }

                // cleanup the native reference
                if(ref != null) {
                    ref.clean();
                    ref.clear();
                }

                synchronized(NativePeerManager.class) {
                    // timeout elapsed, exit the thread
                    if(ref == null) {
                        cleanerThreads.remove(Thread.currentThread());
                        break;
                    }

                    activeReferences.remove(ref);

                    final int allocs = activeReferences.size();

                    // if there are fewer native allocations than cleaner threads, start draining
                    wait = (allocs<MAX_CLEANER_THREADS) ? 500 : 0;

                    // spawn another cleaner thread if falling behind
                    if(allocs > (cleanerThreads.size()<<MAX_CLEANER_THREADS) && (cleanerThreads.size() < MAX_CLEANER_THREADS))
                        spawnCleanerThread();
                }
            }
        }
    }
}
