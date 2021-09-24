package com.atakmap.map.opengl;

import com.atakmap.map.elevation.ElevationChunk;
import com.atakmap.map.layer.feature.geometry.Envelope;

public final class TerrainTile {
    public final ElevationChunk.Data data;
    public final int numIndices;
    public final Envelope aabbWgs84;
    public final int skirtOffset;
    public final boolean hasData;
    public final boolean heightmap;
    public final int numPostsX;
    public final int numPostsY;
    public final boolean invertYAxis;

    public TerrainTile(ElevationChunk.Data data, boolean hasData, int numIndices, int skirtOffset, Envelope aabbWgs84) {
        this(data, numIndices, skirtOffset, aabbWgs84, hasData, false, 0, 0, false);
    }
    public TerrainTile(ElevationChunk.Data data, boolean hasData, int numIndices, int skirtOffset, Envelope aabbWgs84, int numPostsX, int numPostsY, boolean invertYAxis) {
        this(data, numIndices, skirtOffset, aabbWgs84, hasData, true, numPostsX, numPostsY, invertYAxis);
    }
    TerrainTile(ElevationChunk.Data data, int numIndices, int skirtOffset, Envelope aabbWgs84, boolean hasData, boolean heightmap, int numPostsX, int numPostsY, boolean invertYAxis) {
        this.data = data;
        this.aabbWgs84 = aabbWgs84;
        this.numIndices = numIndices;
        this.skirtOffset = skirtOffset;
        this.hasData = hasData;
        this.heightmap = heightmap;
        this.numPostsX = numPostsX;
        this.numPostsY = numPostsY;
        this.invertYAxis = invertYAxis;
    }
}
