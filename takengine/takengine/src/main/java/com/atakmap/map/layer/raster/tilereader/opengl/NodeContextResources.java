package com.atakmap.map.layer.raster.tilereader.opengl;

import android.graphics.PointF;
import android.opengl.GLES30;

import com.atakmap.lang.Unsafe;
import com.atakmap.opengl.GLTexture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

final class NodeContextResources {
    final int[] uniformGriddedTexCoords = new int[GLQuadTileNode3.MAX_GRID_SIZE];
    final int[] uniformGriddedIndices = new int[GLQuadTileNode3.MAX_GRID_SIZE];
    final ByteBuffer coordStreamBuffer;
    final FloatBuffer coordStreamBufferF;
    final ShortBuffer coordStreamBufferS;
    final static int[] discardBuffers = new int[16];
    static int numNumDiscardBuffers = 0;

    NodeContextResources() {
        // large enough to hold all mesh indices or vertex data or interleaved quad for texture-copy (position.xy+texcoord.uv)
        coordStreamBuffer = Unsafe.allocateDirect(
                Math.max(
                        Math.max(
                                // max indices
                                GLTexture.getNumQuadMeshIndices(GLQuadTileNode3.MAX_GRID_SIZE, GLQuadTileNode3.MAX_GRID_SIZE) * 2,
                                // max pos/texcoord
                                GLTexture.getNumQuadMeshVertices(GLQuadTileNode3.MAX_GRID_SIZE, GLQuadTileNode3.MAX_GRID_SIZE) * 12),
                        // max interleaved pox.xy,texcoord.uv for texture copy
                        4 * (8 + 8)));
        coordStreamBuffer.order(ByteOrder.nativeOrder());
        coordStreamBufferF = coordStreamBuffer.asFloatBuffer();
        coordStreamBufferS = coordStreamBuffer.asShortBuffer();
    }

    final boolean isUniformGrid(int gridWidth, int gridHeight) {
        return (gridWidth == gridHeight) &&
               (gridWidth > 0) &&
               ((gridWidth-1) < uniformGriddedTexCoords.length);
    }
    int getUniformGriddedTexCoords(int gridSize) {
        int uniformTexCoords = uniformGriddedTexCoords[gridSize-1];
        if(uniformTexCoords == GLES30.GL_NONE) {
            final int numVerts = GLTexture.getNumQuadMeshVertices(gridSize, gridSize);
            coordStreamBuffer.clear();
            coordStreamBufferF.clear();
            GLTexture.createQuadMeshTexCoords(new PointF(0f, 0f),
                                              new PointF(1f, 0f),
                                              new PointF(1f, 1f),
                                              new PointF(0f, 1f),
                                              gridSize,
                                              gridSize,
                                              coordStreamBufferF);

            uniformTexCoords = genBuffer();
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, uniformTexCoords);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, numVerts*2*4, coordStreamBuffer, GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, GLES30.GL_NONE);
            uniformGriddedTexCoords[gridSize-1] = uniformTexCoords;
        }
        return uniformTexCoords;
    }
    int getUniformGriddedIndices(int gridSize) {
        int uniformIndices = uniformGriddedIndices[gridSize-1];
        if(uniformIndices == GLES30.GL_NONE) {
            final int numIndices = GLTexture.getNumQuadMeshIndices(gridSize, gridSize);
            uniformIndices = genBuffer();
            coordStreamBuffer.clear();
            coordStreamBufferS.clear();
            GLTexture.createQuadMeshIndexBuffer(gridSize, gridSize, coordStreamBufferS);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, uniformIndices);
            GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, numIndices * 2, coordStreamBuffer, GLES30.GL_STATIC_DRAW);
            GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, GLES30.GL_NONE);
            uniformGriddedIndices[gridSize-1] = uniformIndices;
        }
        return uniformIndices;
    }
    int discardBuffer(int id) {
        if(id == GLES30.GL_NONE)
            return GLES30.GL_NONE;
        if(numNumDiscardBuffers == discardBuffers.length) {
            GLES30.glDeleteBuffers(numNumDiscardBuffers, discardBuffers, 0);
            numNumDiscardBuffers = 0;
        }
        discardBuffers[numNumDiscardBuffers++] = id;
        return GLES30.GL_NONE;

    }
    int genBuffer() {
        if(numNumDiscardBuffers == 0) {
            GLES30.glGenBuffers(1, discardBuffers, 0);
            numNumDiscardBuffers++;
        }
        return discardBuffers[--numNumDiscardBuffers];
    }
    void deleteBuffers() {
        if(numNumDiscardBuffers > 0)
            GLES30.glDeleteBuffers(numNumDiscardBuffers, discardBuffers, 0);
        numNumDiscardBuffers = 0;
    }
}
