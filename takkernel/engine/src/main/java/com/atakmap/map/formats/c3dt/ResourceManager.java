package com.atakmap.map.formats.c3dt;

import android.opengl.GLES30;

import gov.tak.api.engine.map.RenderContext;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

final class ResourceManager {
    RenderContext mainctx;
    Executor worker;

    Map<Thread, RenderContext> childContexts = new HashMap<>();

    public ResourceManager(RenderContext ctx) {
        mainctx = ctx;
        //worker = Util.newImmediateExecutor();
        //worker = Executors.newSingleThreadExecutor();
        worker = Executors.newFixedThreadPool(3);
    }

    public void submit(final Job job) {
        worker.execute(new Runnable() {
            @Override
            public void run() {
                RenderContext rc = mainctx;

                RenderContext child = childContexts.get(Thread.currentThread());
                if(child == null) {
                    child = mainctx.createChildContext();
                    if(child != null) {
                        childContexts.put(Thread.currentThread(), child);
                        child.attach();
                        rc = child;
                    }
                } else {
                    rc = child;
                }

                job.execute(rc);
                if(rc != mainctx)
                    GLES30.glFinish();
            }
        });
    }

    public static abstract class Job {
        private boolean canceled = false;

        public abstract void execute(RenderContext ctx);

        public synchronized boolean isCanceled() {
            return this.canceled;
        }

        public synchronized void cancel() {
            this.canceled = true;
        }
    }
}
