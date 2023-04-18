package com.atakmap.map.layer.raster.tilereader.opengl;

import com.atakmap.opengl.GLTextureCache;

import gov.tak.api.annotation.ModifierApi;

@ModifierApi(since = "4.6", modifiers = {"final"}, target = "4.9")
public class NodeOptions {
    public boolean textureCopyEnabled;
    public boolean childTextureCopyResolvesParent;
    public GLTextureCache textureCache;
    public boolean progressiveLoad;
    public double levelTransitionAdjustment;
    public boolean textureBorrowEnabled;
    public boolean adaptiveTileLod;

    public NodeOptions() {
        this.textureCopyEnabled = true;
        this.childTextureCopyResolvesParent = true;
        this.textureCache = null;
        this.progressiveLoad = true;
        this.levelTransitionAdjustment = 0d;
        this.textureBorrowEnabled = true;
        this.adaptiveTileLod = false;
    }
}