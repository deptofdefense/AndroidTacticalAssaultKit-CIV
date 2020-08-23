package com.atakmap.map.formats.c3dt;

import android.graphics.Color;

import com.atakmap.lang.Unsafe;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.opengl.GLES20FixedPipeline;

import java.nio.FloatBuffer;

final class GLVolume {
    public static void draw(MapRendererState view, Envelope aabb, int color) {
        FloatBuffer verts = null;
        try {
            verts = Unsafe.allocateDirect(3*(8+8+8), FloatBuffer.class);

            // bottom
            put(view, aabb.maxY, aabb.minX, aabb.minZ, verts); // upper
            put(view, aabb.maxY, aabb.maxX, aabb.minZ, verts);
            put(view, aabb.maxY, aabb.maxX, aabb.minZ, verts); // right
            put(view, aabb.minY, aabb.maxX, aabb.minZ, verts);
            put(view, aabb.minY, aabb.maxX, aabb.minZ, verts); // bottom
            put(view, aabb.minY, aabb.minX, aabb.minZ, verts);
            put(view, aabb.minY, aabb.minX, aabb.minZ, verts); // left
            put(view, aabb.maxY, aabb.minX, aabb.minZ, verts);
            // top
            put(view, aabb.maxY, aabb.minX, aabb.maxZ, verts); // upper
            put(view, aabb.maxY, aabb.maxX, aabb.maxZ, verts);
            put(view, aabb.maxY, aabb.maxX, aabb.maxZ, verts); // right
            put(view, aabb.minY, aabb.maxX, aabb.maxZ, verts);
            put(view, aabb.minY, aabb.maxX, aabb.maxZ, verts); // bottom
            put(view, aabb.minY, aabb.minX, aabb.maxZ, verts);
            put(view, aabb.minY, aabb.minX, aabb.maxZ, verts); // left
            put(view, aabb.maxY, aabb.minX, aabb.maxZ, verts);

            // UL
            put(view, aabb.maxY, aabb.minX, aabb.minZ, verts);
            put(view, aabb.maxY, aabb.minX, aabb.maxZ, verts);
            // UR
            put(view, aabb.maxY, aabb.maxX, aabb.minZ, verts);
            put(view, aabb.maxY, aabb.maxX, aabb.maxZ, verts);
            // LR
            put(view, aabb.minY, aabb.maxX, aabb.minZ, verts);
            put(view, aabb.minY, aabb.maxX, aabb.maxZ, verts);
            // LL
            put(view, aabb.minY, aabb.minX, aabb.minZ, verts);
            put(view, aabb.minY, aabb.minX, aabb.maxZ, verts);

            verts.flip();

            GLES20FixedPipeline.glEnableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
            GLES20FixedPipeline.glLineWidth(1f);
            GLES20FixedPipeline.glColor4f(Color.red(color)/255f, Color.green(color)/255f, Color.blue(color)/255f, Color.alpha(color)/255f);
            GLES20FixedPipeline.glVertexPointer(3, GLES20FixedPipeline.GL_FLOAT, 0, verts);
            GLES20FixedPipeline.glDrawArrays(GLES20FixedPipeline.GL_LINES, 0, verts.limit()/3);

            GLES20FixedPipeline.glDisableClientState(GLES20FixedPipeline.GL_VERTEX_ARRAY);
        } finally {
            if(verts != null)
                Unsafe.free(verts);
        }
    }

    private static void put(MapRendererState view, double lat, double lng, double alt, FloatBuffer verts) {
        view.scratch.geo.set(lat, lng, alt);
        view.scene.forward(view.scratch.geo, view.scratch.pointD);
        verts.put((float)view.scratch.pointD.x);
        verts.put((float)view.scratch.pointD.y);
        verts.put((float)view.scratch.pointD.z);
    }
}
