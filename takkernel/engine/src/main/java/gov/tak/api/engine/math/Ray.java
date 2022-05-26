package gov.tak.api.engine.math;

public final class Ray {
    public PointD origin;
    public Vector direction;

    public Ray(PointD origin, Vector direction) {
        this.origin = origin;
        this.direction = direction;

        this.direction.normalize();
    }
}
