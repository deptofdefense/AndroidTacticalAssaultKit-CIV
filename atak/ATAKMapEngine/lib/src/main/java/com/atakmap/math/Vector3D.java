package com.atakmap.math;

public class Vector3D {
    public double X;
    public double Y;
    public double Z;

    public Vector3D(double x, double y, double z)
    {
        X=x;
        Y=y;
        Z=z;
    }

    public Vector3D subtract(Vector3D v)
    {
        return new Vector3D(this.X-v.X, this.Y-v.Y, this.Z-v.Z);
    }

    public Vector3D add(Vector3D v)
    {
        return new Vector3D(this.X+v.X, this.Y+v.Y, this.Z+v.Z);
    }

    public Vector3D multiply(double v)
    {
        return new Vector3D(this.X*v, this.Y*v, this.Z*v);
    }

    public double dot(Vector3D v)
    {
        return X*v.X + Y*v.Y + Z*v.Z;
    }

    public double dot(double vX, double vY, double vZ)
    {
        return X*vX + Y*vY + Z*vZ;
    }

    public Vector3D normalize()
    {
        return this.multiply(1d / Math.sqrt(X*X + Y*Y + Z*Z));
    }
    
    public Vector3D cross(Vector3D b)
    {
        final Vector3D a = this;
        return new Vector3D(a.Y*b.Z - a.Z*b.Y,
                            a.Z*b.X - a.X*b.Z,
                            a.X*b.Y - a.Y*b.X);
    }

    @Override
    public String toString() {
        return "Vector3D {" + X + "," + Y + "," + Z + "}";
    }
}
