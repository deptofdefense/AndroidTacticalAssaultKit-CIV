package com.atakmap.map.formats.c3dt;

import com.atakmap.io.ProtocolHandler;
import com.atakmap.map.RenderContext;

import java.util.Date;

interface GLContent {

    enum State {
        Loading,
        Loaded,
        Failed
    }

    State getState();
    boolean loadContent(RenderContext ctx, ContentSource source);
    boolean draw(MapRendererState view);
    void release();
}
