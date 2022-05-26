package gov.tak.api.engine.math;

/**
 * Geometric representation of some three-dimensional surface.
 */
public interface IGeometryModel {
    /**
     * Computes the intersection point between the geometry and the specified {@link Ray}. If the
     * ray intersects the geometry at multiple locations, the intersection point closest to the
     * ray's origin will be returned.
     *
     * @param ray   A ray
     * @return  The intersection point or <code>null</code> if no intersection was found.
     */
    PointD intersect(Ray ray);
}
