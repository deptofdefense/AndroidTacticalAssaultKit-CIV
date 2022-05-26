package gov.tak.platform.commons.opengl;

import java.nio.*;
import java.util.HashMap;
import java.util.Map;

final class VertexAttribArrays {
    final int[] attribVbos = new int[64];
    final Map<Integer, Integer> attribVbosOverflow = new HashMap<>();
    final int[] vbo = new int[1];

    public void enable(int id) {

    }
    public void disable(int id) {
        if(id < 0)
            return;
        vbo[0] = GLES20.GL_NONE;
        if(id < attribVbos.length) {
            vbo[0] = attribVbos[id];
            attribVbos[id] = GLES20.GL_NONE;
        } else {
            if(attribVbosOverflow.containsKey(id))
                vbo[0] = attribVbosOverflow.remove(id);
        }
        if(vbo[0] == GLES20.GL_NONE)
            return;
        GLES20.glDeleteBuffers(1, vbo, 0);
        vbo[0] = GLES20.GL_NONE;
    }
    public void pointer(int id, int size, int type, boolean normalized, int stride, Buffer data) {
        if(id < 0)
            return;
        disable(id);
        int off = JOGLGLES.offset(data);
        int bufsize = JOGLGLES.limit(data);
        if(bufsize == 0)
            return;

        vbo[0] = GLES20.GL_NONE;
        GLES20.glGenBuffers(1, vbo, 0);
        if(vbo[0] == GLES20.GL_NONE)
            return;

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, bufsize, data, GLES20.GL_STATIC_DRAW);
        GLES20.glVertexAttribPointer(id, size, type, normalized, stride, off);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, GLES20.GL_NONE);
        if(id < attribVbos.length)
            attribVbos[id] = vbo[0];
        else
            attribVbosOverflow.put(id, vbo[0]);
        vbo[0] = GLES20.GL_NONE;
    }
}
