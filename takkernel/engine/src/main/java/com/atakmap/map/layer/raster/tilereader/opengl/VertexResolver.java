package com.atakmap.map.layer.raster.tilereader.opengl;

import com.atakmap.map.opengl.GLMapView;
import com.atakmap.util.Releasable;

interface VertexResolver<T> extends Releasable {
        void beginDraw(GLMapView view);
        void endDraw(GLMapView view);

        /**
         * Signals that vertex resolution will commence for the specified node.
         *
         * <P>The invocation of this method must ALWAYS be followed by a
         * subsequent invocation of {@link #endNode(GLQuadTileNode2)} with the same
         * <code>GLTileNode2</code> instance.
         *
         * @param node  A node
         */
        void beginNode(T node);

        /**
         * Signals that vertex resolution has completed for the specified node.
         *
         * <P>The invocation of this method should ALWAYS follow a previous
         * invocation of {@link #beginNode(GLQuadTileNode2)}.
         *
         * @param node  A node
         */
        void endNode(T node);

        /**
         * Projects the specified image space coordinate.
         *
         * @param view      The view
         * @param imgSrcX   The image x-coordinate
         * @param imgSrcY   The image x-coordinate
         * @param vert       Returns the computed coordinate
         */
        void project(GLMapView view, long imgSrcX, long imgSrcY, GLQuadTileNode2.GridVertex vert);
}
