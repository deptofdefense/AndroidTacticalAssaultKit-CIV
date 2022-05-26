package com.atakmap.map.formats.c3dt;

import gov.tak.api.engine.map.RenderContext;

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
