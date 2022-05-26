package com.atakmap.map.layer.feature;

import com.atakmap.interop.Pointer;
import com.atakmap.map.layer.feature.geometry.Geometry;
import com.atakmap.map.layer.feature.geometry.GeometryCollection;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.Point;
import com.atakmap.map.layer.feature.geometry.Polygon;
import com.atakmap.map.layer.feature.style.Style;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
final class Interop {
    static Field Style_pointer = null;
    static Method Style_create = null;
    static Field Geometry_pointer = null;
    static Method Geometry_create = null;

    static TEGC tegc;

    static Pointer getPointer(Style s) {
        if(s == null)
            return null;

        if(Style_pointer == null) {
            try {
                Field f = Style.class.getDeclaredField("pointer");
                f.setAccessible(true);
                Style_pointer = f;
            } catch(Throwable t) {
                throw new RuntimeException(t);
            }
        }

        try {
            return (Pointer) Style_pointer.get(s);
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static long getRawPointer(Style s) {
        final Pointer retval = getPointer(s);
        if(retval == null)
            return 0L;
        return retval.raw;
    }

    static Style createStyle(Pointer pointer, Object owner) {
        if(pointer == null || pointer.raw == 0L)
            return null;
        if(Style_create == null) {
            try {
                Method m = Style.class.getDeclaredMethod("create", Pointer.class, Object.class);
                m.setAccessible(true);
                Style_create = m;
            } catch(Throwable t) {
                throw new RuntimeException(t);
            }
        }

        try {
            return (Style) Style_create.invoke(null, pointer, owner);
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static Pointer getPointer(Geometry s) {
        if(s == null)
            return null;

        if(Geometry_pointer == null) {
            try {
                Field f = Geometry.class.getDeclaredField("pointer");
                f.setAccessible(true);
                Geometry_pointer = f;
            } catch(Throwable t) {
                throw new RuntimeException(t);
            }
        }

        try {
            return (Pointer) Geometry_pointer.get(s);
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static long getRawPointer(Geometry s) {
        final Pointer retval = getPointer(s);
        if(retval == null)
            return 0L;
        return retval.raw;
    }

    static Geometry createGeometry(Pointer pointer, Object owner) {
        if(pointer == null || pointer.raw == 0L)
            return null;
        if(Geometry_create == null) {
            try {
                Method m = Geometry.class.getDeclaredMethod("create", Pointer.class, Object.class);
                m.setAccessible(true);
                Geometry_create = m;
            } catch(Throwable t) {
                throw new RuntimeException(t);
            }
        }

        try {
            return (Geometry) Geometry_create.invoke(null, pointer, owner);
        } catch(Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static int getGeometryClass(Class<? extends Geometry> geomClass) {
        if(tegc == null)
            tegc = new TEGC();

        if(geomClass.equals(Point.class)) {
            return tegc.Point;
        } else if(geomClass.equals(Polygon.class)) {
            return tegc.Polygon;
        } else if(geomClass.equals(GeometryCollection.class)) {
            return tegc.GeometryCollection;
        } else if(geomClass.equals(LineString.class)) {
            return tegc.LineString;
        } else {
            throw new IllegalArgumentException();
        }
    }

    static class TEGC {
        public int LineString;
        public int Point;
        public int Polygon;
        public int GeometryCollection;

        TEGC() {
            LineString = get("LineString");
            Point = get("Point");
            Polygon = get("Polygon");
            GeometryCollection = get("GeometryCollection");
        }

        static int get(String type) {
            try {
                Method m = Geometry.class.getDeclaredMethod("getTEGC_" + type);
                m.setAccessible(true);
                return (Integer)m.invoke(null);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }
}
