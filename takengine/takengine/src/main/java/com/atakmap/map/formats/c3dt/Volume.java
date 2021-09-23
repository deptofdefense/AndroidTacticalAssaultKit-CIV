package com.atakmap.map.formats.c3dt;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

abstract class Volume {
    public static final class Box extends Volume {
        public double centerX;
        public double centerY;
        public double centerZ;
        public double[] xDirHalfLen;
        public double[] yDirHalfLen;
        public double[] zDirHalfLen;
    }

    public static final class Region extends Volume {
        public double west;
        public double south;
        public double east;
        public double north;
        public double minimumHeight;
        public double maximumHeight;
    }

    public static final class Sphere extends Volume {
        public double centerX;
        public double centerY;
        public double centerZ;
        public double radius;
    }

    public static Volume parse(JSONObject json) throws JSONException {
        if(json == null)
            return null;
        if(json.has("box")) {
            JSONArray arr = json.getJSONArray("box");
            Box box = new Box();
            box.centerX = arr.getDouble(0);
            box.centerY = arr.getDouble(1);
            box.centerZ = arr.getDouble(2);
            box.xDirHalfLen = new double[] {arr.getDouble(3), arr.getDouble(4), arr.getDouble(5)};
            box.yDirHalfLen = new double[] {arr.getDouble(6), arr.getDouble(7), arr.getDouble(8)};
            box.zDirHalfLen = new double[] {arr.getDouble(9), arr.getDouble(10), arr.getDouble(11)};
            return box;
        } else if(json.has("region")) {
            JSONArray arr = json.getJSONArray("region");
            Region region = new Region();
            region.west = arr.getDouble(0);
            region.south = arr.getDouble(1);
            region.east = arr.getDouble(2);
            region.north = arr.getDouble(3);
            region.minimumHeight = arr.getDouble(4);
            region.maximumHeight = arr.getDouble(5);
            return region;
        } else if(json.has("sphere")) {
            JSONArray arr = json.getJSONArray("sphere");
            Sphere sphere = new Sphere();
            sphere.centerX = arr.getDouble(0);
            sphere.centerY = arr.getDouble(1);
            sphere.centerZ = arr.getDouble(2);
            sphere.radius = arr.getDouble(3);
            return sphere;
        } else {
            throw new IllegalArgumentException();
        }
    }
}
