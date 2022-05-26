package com.atakmap.map.formats.c3dt;

import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class Tile {
    public Tile parent;
    public double[] transform;
    public Volume boundingVolume;
    public Volume viewerRequestVolume;
    public double geometricError;
    public Refine refine;
    public Content content;
    public Tile[] children;

    public static Tile parse(Tile parent, JSONObject json) throws JSONException {
        if(json == null)
            return null;
        Tile tile = new Tile();
        tile.parent = parent;
        if(json.has("transform")) {
            tile.transform = new double[16];
            JSONArray arr = json.getJSONArray("transform");
            for(int i = 0; i < 16; i++)
                tile.transform[i] = arr.getDouble(i);
        }
        tile.boundingVolume = Volume.parse(json.optJSONObject("boundingVolume"));
        tile.viewerRequestVolume = Volume.parse(json.optJSONObject("viewerRequestVolume"));
        tile.geometricError = json.optDouble("geometricError", Double.NaN);
        tile.refine = Refine.parse(json.optString("refine", null));
        if(tile.refine == null && parent != null)
            tile.refine = parent.refine;
        else if(tile.refine == null && parent == null)
            tile.refine = Refine.Replace; // spec says required for root, but we won't hard fail
        tile.content = Content.parse(json.optJSONObject("content"));
        if(json.has("children")) {
            JSONArray arr = json.getJSONArray("children");
            tile.children = new Tile[arr.length()];
            for(int i = 0; i < arr.length(); i++)
                tile.children[i] = Tile.parse(tile, arr.getJSONObject(i));
        }
        return tile;
    }

    public static Matrix accumulate(Tile tile) {
        // if tile is null, there is no transform
        if(tile == null)
            return null;
        // obtain parent transform
        final Matrix parentXform = accumulate(tile.parent);
        // if tile transform is null, inherit from parent
        if(tile.transform == null)
            return parentXform;
        // create tile transform
        Matrix transform = new Matrix(
                tile.transform[0], tile.transform[4], tile.transform[8], tile.transform[12],
                tile.transform[1], tile.transform[5], tile.transform[9], tile.transform[13],
                tile.transform[2], tile.transform[6], tile.transform[10], tile.transform[14],
                tile.transform[3], tile.transform[7], tile.transform[11], tile.transform[15]
        );
        // pre-concatenate parent transform if non-null
        if(parentXform != null)
            transform.preConcatenate(parentXform);
        return transform;
    }

    public static Envelope approximateBounds(Tile tile) {
        if(tile.boundingVolume instanceof Volume.Region) {
            Volume.Region region = (Volume.Region)tile.boundingVolume;
            return new Envelope(Math.toDegrees(region.west), Math.toDegrees(region.south), region.minimumHeight, Math.toDegrees(region.east), Math.toDegrees(region.north), region.maximumHeight);
        }

        PointD center;
        double radius;
        if(tile.boundingVolume instanceof Volume.Sphere) {
            Volume.Sphere sphere = (Volume.Sphere)tile.boundingVolume;

            radius = sphere.radius;

            center = new PointD(sphere.centerX, sphere.centerY, sphere.centerZ);
        } else if(tile.boundingVolume instanceof Volume.Box) {
            Volume.Box box = (Volume.Box)tile.boundingVolume;

            radius = MathUtils.max(
                    MathUtils.distance(box.xDirHalfLen[0], box.xDirHalfLen[1], box.xDirHalfLen[2], 0d, 0d, 0d),
                    MathUtils.distance(box.yDirHalfLen[0], box.yDirHalfLen[1], box.yDirHalfLen[2], 0d, 0d, 0d),
                    MathUtils.distance(box.zDirHalfLen[0], box.zDirHalfLen[1], box.zDirHalfLen[2], 0d, 0d, 0d));

            center = new PointD(box.centerX, box.centerY, box.centerZ);
        } else {
            throw new IllegalStateException();
        }

        // transform the center
        final Matrix transform = Tile.accumulate(tile);
        if(transform != null)
            transform.transform(center, center);
        GeoPoint centroid = ECEFProjection.INSTANCE.inverse(center, null);

        final double metersDegLat = GeoCalculations.approximateMetersPerDegreeLatitude(centroid.getLatitude());
        final double metersDegLng = GeoCalculations.approximateMetersPerDegreeLongitude(centroid.getLongitude());

        return new Envelope(centroid.getLongitude()-(radius/metersDegLng),
                            centroid.getLatitude()-(radius/metersDegLat),
                            centroid.getAltitude()-radius,
                            centroid.getLongitude()+(radius/metersDegLng),
                            centroid.getLatitude()+(radius/metersDegLat),
                            centroid.getAltitude()+radius);
    }
}
