package gov.tak.api.engine.map;

import gov.tak.api.annotation.DontObfuscate;


@DontObfuscate
public interface IMapRendererEnums {
    @DontObfuscate
    enum DisplayMode {
        Flat,
        Globe,
    }

    @DontObfuscate
    enum DisplayOrigin {
        UpperLeft,
        LowerLeft,
    }

    @DontObfuscate
    enum InverseMode {
        /**
         * Transforms all three components (x,y,z) of the viewspace coordinate
         * into world space, using the applicable transforms. No intersections
         * are performed.
         */
        Transform,
        /**
         * Coonstructs a ray that passes through the specified viewspace
         * coordinate and computes the intersection with the current surface
         * model of the globe, including any terrain or environment meshes.
         */
        RayCast,
    }

    /** If specified, instructs the raycast operation to ignore terrain meshes */
    int HINT_RAYCAST_IGNORE_TERRAIN_MESH = 0x01;
    /** If specified, instructs the raycast operation to ignore surface meshes */
    int HINT_RAYCAST_IGNORE_SURFACE_MESH = 0x02;

    @DontObfuscate
    enum InverseResult {
        /** no intersection */
        None,
        /** Transformation only, no intersection */
        Transformed,
        /** Intersect with globe geometry model */
        GeometryModel,
        /** Intersect with surface mesh */
        SurfaceMesh,
        /** Intersect with terrain mesh */
        TerrainMesh,
    }

    @DontObfuscate
    enum CameraCollision {
        /** Camera collisions with surfaces is allowed */
        Ignore,
        /**
         * If a collision occurs, adjust the camera position, maintaining
         * focus
         */
        AdjustCamera,
        /**
         * If a collision occurs, adjust the focus position, maintaining
         * camera-focus direction vector
         */
        AdjustFocus,
        /** If a collision occurs, abort the camera update request */
        Abort,
    };
}
