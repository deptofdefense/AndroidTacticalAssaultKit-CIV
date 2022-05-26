/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.atakmap.os;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.atakmap.coremap.io.IOProviderFactory;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * FileObserver is a replacement for android.os.FileObserver class.
 *
 * This class will forward startWatching calls on to android.os.FileObserver unless
 * IOProviderFactory.isDefault() returns false, in which case it will periodically poll
 * the file system for observable changes to the observed files.
 */
public abstract class FileObserver extends android.os.FileObserver {
    private static final String LOG_TAG = "TAKFileObserver";

    private static class Stat {
        private final long lastModified;
        private final String path;
        private final boolean isDir;
        private boolean modifiedThisTime;

        Stat(File f) {
            lastModified = IOProviderFactory.lastModified(f);
            path = f.getPath();
            isDir = IOProviderFactory.isDirectory(f);
            modifiedThisTime = false;
        }
    }

    private static class ObserverThread extends Thread {
        private static final String TAG = "ObserverThread";
        private final static int DEFAULT_POLLING_INTERVAL = 10;
        private final static TimeUnit DEFAULT_POLLING_UNIT = TimeUnit.SECONDS;

        private Handler observerHandler;
        private final HashMap<String, PathObservers> m_observers = new HashMap<>();

        private final CountDownLatch latch;

        public ObserverThread(CountDownLatch latch) {
            super("TAKFileObserver");
            this.latch = latch;
        }

        @Override
        public void run() {
            Looper.prepare();
            observerHandler = new Handler(
                    Objects.requireNonNull(Looper.myLooper()));
            latch.countDown();
            Looper.loop();
        }

        private class PollingTask implements Runnable {
            private final String rootPath;
            private final File rootPathFile;
            private final long interval;
            private final TimeUnit intervalUnit;

            public PollingTask(String path, long interval,
                    TimeUnit intervalUnit) {
                this.rootPath = path;
                this.rootPathFile = new File(path);
                this.interval = interval;
                this.intervalUnit = intervalUnit;
            }

            public PollingTask(String path) {
                this(path, DEFAULT_POLLING_INTERVAL, DEFAULT_POLLING_UNIT);
            }

            @Override
            public void run() {
                PathObservers observers = m_observers.get(this.rootPath);
                if (observers != null) {
                    Map<String, Stat> newStats = readStats(this.rootPathFile);
                    for (Stat ns : newStats.values()) {
                        Stat oldStat = observers.oldStats.get(ns.path);
                        if (oldStat != null) {
                            ns.modifiedThisTime = (ns.lastModified != oldStat.lastModified);
                        } else {
                            ns.modifiedThisTime = true;
                        }
                    }
                    compareStats(newStats, observers.oldStats, this.rootPath);
                    observers.oldStats = newStats;

                    boolean posted = observerHandler.postDelayed(this,
                            intervalUnit.toMillis(interval));
                    if (!posted) {
                        Log.d(TAG, "postDelayed failed for polling task: "
                                + rootPath);
                    }
                }
            }
        }

        public Map<String, Stat> readStats(File f) {
            Map<String, Stat> results = new HashMap<>();

            Queue<File> fileQueue = new ArrayDeque<>();
            fileQueue.offer(f);
            while (fileQueue.size() > 0) {
                File currentFile = fileQueue.poll();
                if (IOProviderFactory.isDirectory(currentFile)) {
                    File[] listedFiles = IOProviderFactory
                            .listFiles(currentFile);
                    fileQueue.addAll(Arrays.asList(listedFiles));
                }
                Stat stat = new Stat(currentFile);
                results.put(stat.path, stat);
            }

            return results;
        }

        public void compareStats(Map<String, Stat> newStats,
                Map<String, Stat> oldStats, String rootPath) {
            for (Stat ns : newStats.values()) {
                Stat oldStat = oldStats.get(ns.path);
                if (oldStat == null) {
                    onEvent(rootPath, CREATE, ns.path);
                } else {
                    if (statsDiffer(ns, oldStat)) {
                        onEvent(rootPath, MODIFY, ns.path);
                    }
                    if (!ns.modifiedThisTime && oldStat.modifiedThisTime) {
                        //if it was modified in the last iteration, but not this one, assume it's done writing?
                        onEvent(rootPath, CLOSE_WRITE, ns.path);
                    }
                }
            }

            for (Stat os : oldStats.values()) {
                if (!newStats.containsKey(os.path)) {
                    onEvent(rootPath, DELETE, os.path);
                }
            }
        }

        private boolean statsDiffer(Stat x, Stat y) {
            return x.lastModified != y.lastModified
                    || x.isDir != y.isDir;
        }

        public void startWatching(String path, FileObserver observer) {
            observerHandler.post(new StartWatchingTask(path, observer));
        }

        private class StartWatchingTask implements Runnable {
            private final String path;
            private final FileObserver observer;

            public StartWatchingTask(String path, FileObserver observer) {
                this.path = path;
                this.observer = observer;
            }

            @Override
            public void run() {
                PathObservers observers = m_observers.get(path);
                if (observers == null) {
                    observers = new PathObservers(path);
                    m_observers.put(path, observers);

                    // start polling task some time after this task ends
                    observerHandler.post(new PollingTask(path));
                }
                observers.addObserver(observer);
            }
        }

        public void stopWatching(String path, FileObserver observer) {
            observerHandler.post(new StopWatchingTask(path, observer));
        }

        private class StopWatchingTask implements Runnable {
            private final String path;
            private final FileObserver observer;

            public StopWatchingTask(String path, FileObserver observer) {
                this.path = path;
                this.observer = observer;
            }

            @Override
            public void run() {
                PathObservers observers = m_observers.get(path);
                if (observers != null) {
                    observers.removeObserver(observer);
                    if (observers.isEmpty()) {
                        m_observers.remove(path);
                    }
                }
            }
        }

        public void onEvent(String rootPath, int mask, String path) {
            observerHandler.post(new OnEventTask(rootPath, mask, path));
        }

        private class OnEventTask implements Runnable {
            private final String rootPath;
            private final int mask;
            private final String changedPath;

            public OnEventTask(String rootPath, int mask, String path) {
                this.rootPath = rootPath;
                this.mask = mask;
                this.changedPath = path;
            }

            @Override
            public void run() {
                PathObservers observers = m_observers.get(rootPath);
                if (observers != null) {
                    observers.onEvent(mask, changedPath);
                }
            }
        }

        private static class PathObservers {
            public final String rootPath;
            public Map<String, Stat> oldStats = new HashMap<>();
            public final List<WeakReference<FileObserver>> observers = new ArrayList<>();

            public PathObservers(String path) {
                this.rootPath = path;
            }

            public void addObserver(FileObserver observer) {
                observers.add(new WeakReference<>(observer));
            }

            public void removeObserver(FileObserver observer) {
                Iterator<WeakReference<FileObserver>> iter = observers
                        .iterator();
                while (iter.hasNext()) {
                    WeakReference<FileObserver> weakObserver = iter.next();
                    FileObserver obs = weakObserver.get();
                    if (obs == null || obs == observer) {
                        iter.remove();
                    }
                }
            }

            public boolean isEmpty() {
                return observers.isEmpty();
            }

            public void onEvent(int mask, String path) {
                Iterator<WeakReference<FileObserver>> iter = observers
                        .iterator();
                while (iter.hasNext()) {
                    WeakReference<FileObserver> weakObserver = iter.next();
                    FileObserver observer = weakObserver.get();
                    if (observer == null) {
                        // observer has been garbage collected
                        iter.remove();
                    } else {
                        try {
                            path = path.replaceFirst(rootPath, "");
                            observer.maskOnEvent(mask, path);
                        } catch (Exception e) {
                            Log.d(LOG_TAG,
                                    "Unhandled exception in FileObserver "
                                            + observer,
                                    e);
                        }
                    }
                }
            }
        }
    }

    private static class AndroidFileObserver extends android.os.FileObserver {
        private final FileObserver parentObserver;

        public AndroidFileObserver(FileObserver parent, String path, int mask) {
            super(path, mask);
            this.parentObserver = Objects.requireNonNull(parent);
        }

        @Override
        public void onEvent(int i, String s) {
            this.parentObserver.maskOnEvent(i, s);
        }
    }

    private static final ObserverThread s_observerThread;

    static {
        CountDownLatch latch = new CountDownLatch(1);
        s_observerThread = new ObserverThread(latch);
        s_observerThread.start();
        try {
            boolean timedout = latch.await(1, TimeUnit.SECONDS);
            if (!timedout) {
                Log.w(LOG_TAG, "Timed out waiting for observerThread to start");
            }
        } catch (InterruptedException e) {
            Log.w(LOG_TAG,
                    "Thread interrupted while waiting for observerThread to start");
            Thread.currentThread().interrupt();
        }
    }

    private final File mFile;
    private final Integer mMask;
    private boolean mWatching = false;
    private android.os.FileObserver obs = null;

    public FileObserver(String path) {
        this(path, ALL_EVENTS);
    }

    public FileObserver(String path, int mask) {
        super(path, mask);
        mFile = new File(path);
        mMask = mask;
    }

    public void startWatching() {
        if (IOProviderFactory.isDefault()) {
            if (obs == null) {
                obs = new AndroidFileObserver(this, mFile.toString(), mMask);
                obs.startWatching();
            }
        } else {
            if (!mWatching) {
                s_observerThread.startWatching(mFile.toString(), this);
                mWatching = true;
            }
        }
    }

    public void stopWatching() {
        if (obs != null) {
            obs.stopWatching();
            obs = null;
        } else {
            if (mWatching) {
                s_observerThread.stopWatching(mFile.toString(), this);
                mWatching = false;
            }
        }
    }

    public void maskOnEvent(int event, String path) {
        event = mMask & event;
        if (event != 0) {
            onEvent(event, path);
        }
    }

    public abstract void onEvent(int event, String path);
}
