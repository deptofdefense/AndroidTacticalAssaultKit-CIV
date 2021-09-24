package com.atakmap.math;

public class Triangle implements GeometryModel {

    private final static double SMALL_NUM   = 0.0000000000000001d;

    public Vector3D V0;
    public Vector3D V1;
    public Vector3D V2;

    public Triangle(PointD a, PointD b, PointD c) {
        V0 = new Vector3D(a.x, a.y, a.z);
        V1 = new Vector3D(b.x, b.y, b.z);
        V2 = new Vector3D(c.x, c.y, c.z);
    }

    @Override
    public PointD intersect(Ray ray) {
        PointD retval = new PointD(Double.NaN, Double.NaN, Double.NaN);
        int err = this.intersect(retval, ray);
        if(err != 0)
            return null;
        return retval;
    }

    public int intersect(PointD value, Ray ray) {
        // derived from http://geomalgorithms.com/a06-_intersect-2.html#intersect3D_RayTriangle()
        Vector3D    u, v, n;              // triangle vectors
        Vector3D    dir, w0, w;           // ray vectors
        double     r, a, b;              // params to calc ray-plane intersect

        // get triangle edge vectors and plane normal
        //u = T.V1 - T.V0;
        u = V1.subtract(V0);
        //v = T.V2 - T.V0;
        v = V2.subtract(V0);
        //n = u * v;              // cross product
        n = u.cross(v);
        //if (n == (Vector)0)             // triangle is degenerate
        if(n.X == 0d && n.Y == 0d && n.Z == 0d)
            return -1;                  // do not deal with this case

        //dir = R.P1 - R.P0;              // ray direction vector
        dir = ray.direction;
        //w0 = R.P0 - T.V0;
        Vector3D RP0 = new Vector3D(ray.origin.x, ray.origin.y, ray.origin.z);
        w0 = RP0.subtract(V0);
        //a = -dot(n,w0);
        a = -n.dot(w0);
        //b = dot(n,dir);
        b = n.dot(dir);
        if (Math.abs(b) < SMALL_NUM) {     // ray is  parallel to triangle plane
            if (a == 0)                 // ray lies in triangle plane
                return -2;
            else return -3;              // ray disjoint from plane
        }

        // get intersect point of ray with triangle plane
        r = a / b;
        if (r < 0.0)                    // ray goes away from triangle
            return -4;                   // => no intersect
        // for a segment, also test if (r > 1.0) => no intersect

        //*I = R.P0 + r * dir;            // intersect point of ray and plane
        Vector3D I = RP0.add(dir.multiply(r));

        // is I inside T?
        double    uu, uv, vv, wu, wv, D;
        uu = u.dot(u);
        uv = u.dot(v);
        vv = v.dot(v);
        //w = *I - T.V0;
        w = I.subtract(V0);
        wu = w.dot(u);
        wv = w.dot(v);
        D = uv * uv - uu * vv;

        // get and test parametric coords
        double s, t;
        s = (uv * wv - vv * wu) / D;
        if (s < 0.0 || s > 1.0)         // I is outside T
            return -5;
        t = (uv * wu - uu * wv) / D;
        if (t < 0.0 || (s + t) > 1.0)  // I is outside T
            return -6;

        //return 1;                       // I is in T
        value.x = I.X;
        value.y = I.Y;
        value.z = I.Z;
        return 0;
    }
    
    public static int intersect(PointD value,
                                double V0x, double V0y, double V0z,
                                double V1x, double V1y, double V1z,
                                double V2x, double V2y, double V2z,
                                Ray ray) {

        // derived from http://geomalgorithms.com/a06-_intersect-2.html#intersect3D_RayTriangle()
        Vector3D    u, v, n;              // triangle vectors
        Vector3D    dir, w0, w;           // ray vectors
        double     r, a, b;              // params to calc ray-plane intersect

        // get triangle edge vectors and plane normal
        //u = T.V1 - T.V0;
        //u = V1.subtract(V0);
        u = new Vector3D(V1x-V0x, V1y-V0y, V1z-V0z);
        //v = T.V2 - T.V0;
        //v = V2.subtract(V0);
        v = new Vector3D(V2x-V0x, V2y-V0y, V2z-V0z);
        //n = u * v;              // cross product
        n = u.cross(v);
        //if (n == (Vector)0)             // triangle is degenerate
        if(n.X == 0d && n.Y == 0d && n.Z == 0d)
            return -1;                  // do not deal with this case

        //dir = R.P1 - R.P0;              // ray direction vector
        dir = ray.direction;
        //w0 = R.P0 - T.V0;
        //Vector3D RP0 = new Vector3D(ray.origin.x, ray.origin.y, ray.origin.z);
        //w0 = RP0.subtract(V0);
        w0 = new Vector3D(ray.origin.x-V0x, ray.origin.y-V0y, ray.origin.z-V0z);

        //a = -dot(n,w0);
        a = -n.dot(w0);
        //b = dot(n,dir);
        b = n.dot(dir);
        if (Math.abs(b) < SMALL_NUM) {     // ray is  parallel to triangle plane
            if (a == 0)                 // ray lies in triangle plane
                return -2;
            else return -3;              // ray disjoint from plane
        }

        // get intersect point of ray with triangle plane
        r = a / b;
        if (r < 0.0)                    // ray goes away from triangle
            return -4;                   // => no intersect
        // for a segment, also test if (r > 1.0) => no intersect

        //*I = R.P0 + r * dir;            // intersect point of ray and plane
        //Vector3D I = RP0.add(dir.multiply(r));
        Vector3D I = new Vector3D(ray.origin.x+(dir.X*r), ray.origin.y+(dir.Y*r), ray.origin.z+(dir.Z*r));

        // is I inside T?
        double    uu, uv, vv, wu, wv, D;
        uu = u.dot(u);
        uv = u.dot(v);
        vv = v.dot(v);
        //w = *I - T.V0;
        //w = I.subtract(V0);
        w = new Vector3D(I.X-V0x, I.Y-V0y, I.Z-V0z);
        wu = w.dot(u);
        wv = w.dot(v);
        D = uv * uv - uu * vv;

        // get and test parametric coords
        double s, t;
        s = (uv * wv - vv * wu) / D;
        if (s < 0.0 || s > 1.0)         // I is outside T
            return -5;
        t = (uv * wu - uu * wv) / D;
        if (t < 0.0 || (s + t) > 1.0)  // I is outside T
            return -6;

        //return 1;                       // I is in T
        value.x = I.X;
        value.y = I.Y;
        value.z = I.Z;
        return 0;
    }

    public static int intersect(PointD value,
                                double ULx, double ULy, double ULz,
                                double URx, double URy, double URz,
                                double LRx, double LRy, double LRz,
                                double LLx, double LLy, double LLz,
                                Ray ray) {
        
        int err;
        
        err = intersect(value,
                        ULx, ULy, ULz,
                        LLx, LLy, LLz,
                        URx, URy, URz,
                        ray);
        if(err == 0)
            return err;
        
        err = intersect(value,
                        LRx, LRy, LRz,
                        URx, URy, URz,
                        LLx, LLy, LLz,
                        ray);
        
        if(err == 0)
            return err;
        
        return err;
    }
}
