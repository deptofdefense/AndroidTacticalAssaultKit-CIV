package com.atakmap.map.formats.quantizedmesh;

/*
 * Utility functions for dealing with QM Tile Coordinate Vectors
 */
public class TileCoord {
    public static native double getLatitude(double yCoord, int level);
    public static native double getLongitude(double xCoord, int level);
    public static native double getSpacing(int level);
    public static native double getTileX(double lng, int level);
    public static native double getTileY(double lat, int level);
}
