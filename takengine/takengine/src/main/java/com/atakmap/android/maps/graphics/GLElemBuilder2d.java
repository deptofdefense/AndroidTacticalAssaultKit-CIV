
package com.atakmap.android.maps.graphics;

import java.util.ArrayList;
import java.util.HashMap;

import android.graphics.PointF;

public class GLElemBuilder2d {

    public GLElemBuilder2d vert(float x, float y) {
        _vertIndex(x, y);
        return this;
    }

    public GLElemBuilder2d vertAndTexCoord(float x, float y, float s, float t) {
        int index = _verts.size();
        _verts.add(new PointF(x, y));
        _texCoords.add(new PointF(s, t));
        _indices.add(index);
        return this;
    }

    public int getIndexCount() {
        return _indices.size();
    }

    public int getVertCount() {
        return _verts.size();
    }

    public int getTexCoordCount() {
        return _texCoords.size();
    }

    public GLFloatArray buildFloatVerts() {
        GLFloatArray fa = new GLFloatArray(2, getVertCount());
        for (int i = 0; i < _verts.size(); ++i) {
            fa.setX(i, _verts.get(i).x);
            fa.setY(i, _verts.get(i).y);
        }
        return fa;
    }

    public GLFloatArray buildFloatTexCoods() {
        GLFloatArray fa = new GLFloatArray(2, getVertCount());
        for (int i = 0; i < _texCoords.size(); ++i) {
            fa.setX(i, _texCoords.get(i).x);
            fa.setY(i, _texCoords.get(i).y);
        }
        return fa;
    }

    public GLShortArray buildShortIndices() {
        GLShortArray sa = new GLShortArray(1, getIndexCount());
        for (int i = 0; i < _verts.size(); ++i) {
            sa.setX(i, _indices.get(i).shortValue());
        }
        return sa;
    }

    private void _vertIndex(float x, float y) {
        HashMap<Float, Integer> m = _vmap.get(x);
        if (m == null) {
            m = new HashMap<Float, Integer>();
            _vmap.put(x, m);
        }

        Integer index = m.get(y);
        if (index == null) {
            index = _verts.size();
            _verts.add(new PointF(x, y));
            m.put(y, index);
        }
        _indices.add(index);
    }

    private ArrayList<Integer> _indices = new ArrayList<Integer>();
    private ArrayList<PointF> _verts = new ArrayList<PointF>();
    private ArrayList<PointF> _texCoords = new ArrayList<PointF>();
    private HashMap<Float, HashMap<Float, Integer>> _vmap = new HashMap<Float, HashMap<Float, Integer>>();
}
