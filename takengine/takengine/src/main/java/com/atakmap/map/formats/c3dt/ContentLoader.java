package com.atakmap.map.formats.c3dt;

import com.atakmap.map.RenderContext;
import com.atakmap.util.Disposable;

final class ContentLoader extends ResourceManager.Job implements Disposable {
    private GLContent glcontent;
    private boolean done;

    private ResourceManager resmgr;
    private ContentSource source;
    private String baseUri;
    private Tile tile;

    public ContentLoader(ResourceManager resmgr, ContentSource source, String baseUri, Tile tile) {
        this.resmgr = resmgr;
        this.source = source;
        this.baseUri = baseUri;
        this.tile = tile;
    }

    @Override
    public void execute(RenderContext ctx) {
        do {
            if (this.isCanceled())
                break;

            try {
                final GLContent c = GLContentFactory.create(ctx, resmgr, this.baseUri, this.tile);
                if(c == null)
                    break;
                c.loadContent(ctx, this.source);
                synchronized(this) {
                    // content is loaded, update reference or release if canceled
                    if(this.isCanceled()) {
                        if(ctx.isRenderThread())
                            c.release();
                        else
                            ctx.queueEvent(new Runnable() {
                                public void run() {
                                    c.release();
                                }
                            });
                    } else {
                        glcontent = c;
                    }
                    this.done = true;
                    return;
                }
            } catch(Throwable ignored) {

            }
        } while(false);

        // glcontent remained null, synchronization not strictly necessary
        this.done = true;
    }

    public synchronized boolean isDone() {
        return this.done;
    }

    public synchronized GLContent transfer() {
        if(!done)
            return null;
        final GLContent retval = this.glcontent;
        this.glcontent = null;
        return retval;
    }

    @Override
    public synchronized void dispose() {
        this.cancel();
        final GLContent toDispose = this.transfer();
        if(toDispose != null) {
            resmgr.mainctx.queueEvent(new Runnable() {
                public void run() {
                    toDispose.release();
                }
            });
        }
    }
}
