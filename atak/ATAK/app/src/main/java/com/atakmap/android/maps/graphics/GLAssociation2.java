
package com.atakmap.android.maps.graphics;

import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.atakmap.android.maps.Association;
import com.atakmap.android.maps.Association.OnClampToGroundChangedListener;
import com.atakmap.android.maps.Association.OnColorChangedListener;
import com.atakmap.android.maps.Association.OnFirstItemChangedListener;
import com.atakmap.android.maps.Association.OnLinkChangedListener;
import com.atakmap.android.maps.Association.OnSecondItemChangedListener;
import com.atakmap.android.maps.Association.OnStrokeWeightChangedListener;
import com.atakmap.android.maps.Association.OnStyleChangedListener;
import com.atakmap.android.maps.Association.OnTextChangedListener;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.feature.Feature;
import com.atakmap.map.layer.feature.geometry.LineString;
import com.atakmap.map.layer.feature.geometry.opengl.GLBatchLineString;
import com.atakmap.map.layer.feature.style.BasicStrokeStyle;
import com.atakmap.map.layer.feature.style.CompositeStyle;
import com.atakmap.map.layer.feature.style.PatternStrokeStyle;
import com.atakmap.map.layer.feature.style.Style;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLText;

public class GLAssociation2 extends AbstractGLMapItem2 implements
        OnStyleChangedListener,
        OnLinkChangedListener, OnColorChangedListener,
        OnFirstItemChangedListener,
        OnSecondItemChangedListener, OnStrokeWeightChangedListener,
        OnClampToGroundChangedListener,
        OnTextChangedListener {

    private final GeoPoint[] _points = {
            null, null
    };

    private int _link;
    private GLText _glText;
    private String _text;
    private GLNinePatch _ninePatch;
    private boolean _clampToGround;
    private double _unwrap;

    private final GLBatchLineString impl;
    private int color = -1;
    private boolean outline = false;
    private byte pattern = (byte) 0xFF;
    private float strokeWidth = 1f;

    private float _textAngle;
    private float _textWidth;
    private float _textHeight;
    private final float[] _textPoint = new float[2];
    private final static float div_180_pi = 180f / (float) Math.PI;
    private static final float div_2 = 1f / 2f;

    public GLAssociation2(MapRenderer surface, Association subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SPRITES
                | GLMapView.RENDER_PASS_SURFACE);

        impl = new GLBatchLineString(surface);

        _link = subject.getLink();
        _clampToGround = subject.getClampToGround();
        if (subject.getFirstItem() != null) {
            subject.getFirstItem().addOnPointChangedListener(
                    _firstItemMovedListener);
        }
        if (subject.getSecondItem() != null) {
            subject.getSecondItem().addOnPointChangedListener(
                    _secondItemMovedListener);
        }
        final PointMapItem i1 = subject.getFirstItem();
        if (i1 != null)
            _setEndPoint(0, i1.getPoint());
        final PointMapItem i2 = subject.getSecondItem();
        if (i2 != null)
            _setEndPoint(1, i2.getPoint());

        onAssociationTextChanged(subject);
    }

    @Override
    public void startObserving() {
        final Association association = (Association) this.subject;
        super.startObserving();
        association.addOnStyleChangedListener(this);
        association.addOnLinkChangedListener(this);
        association.addOnColorChangedListener(this);
        association.addOnFirstItemChangedListener(this);
        association.addOnSecondItemChangedListener(this);
        association.addOnStrokeWeightChangedListener(this);
        association.addOnTextChangedListener(this);

        // sync 'impl' with 'subject'
        onAssociationStyleChanged(association);
        onAssociationColorChanged(association);
        onAssociationStrokeWeightChanged(association);
        _setEndPoint(0, _points[0]);
    }

    @Override
    public void stopObserving() {
        final Association association = (Association) this.subject;
        super.stopObserving();
        association.removeOnStyleChangedListener(this);
        association.removeOnLinkChangedListener(this);
        association.removeOnColorChangedListener(this);
        association.removeOnSecondItemChangedListner(this);
        final PointMapItem firstItem = association.getFirstItem();
        if (firstItem != null) {
            firstItem.removeOnPointChangedListener(_firstItemMovedListener);
        }
        final PointMapItem secondItem = association.getSecondItem();
        if (secondItem != null) {
            secondItem.removeOnPointChangedListener(_secondItemMovedListener);
        }
        association.removeOnSecondItemChangedListner(this);
        association.removeOnStrokeWeightChangedListener(this);
    }

    private void refreshStyle() {
        Style update = null;
        if ((pattern & 0xFF) != 0xFF) {
            update = new PatternStrokeStyle(pattern & 0xFF, 8, this.color,
                    strokeWidth);
        } else {
            update = new BasicStrokeStyle(color, strokeWidth);
            if (outline) {
                BasicStrokeStyle bg = new BasicStrokeStyle(
                        0xFF000000 & this.color, strokeWidth + 2f);
                update = new CompositeStyle(new Style[] {
                        bg, update
                });
            }
        }
        impl.setStyle(update);
        context.requestRefresh();
    }

    @Override
    public void onAssociationLinkChanged(final Association association) {
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _link = association.getLink();
            }
        });
    }

    @Override
    public void onAssociationStyleChanged(final Association association) {
        int style = association.getStyle();
        switch (style) {
            case Association.STYLE_DASHED:
                pattern = (byte) 0x3F;
                break;
            case Association.STYLE_DOTTED:
                pattern = (byte) 0x03;
                break;
            case Association.STYLE_SOLID:
                outline = false;
                pattern = (byte) 0xFF;
                break;
            case Association.STYLE_OUTLINED:
                outline = true;
                pattern = (byte) 0xFF;
                break;

        }

        refreshStyle();
    }

    @Override
    public void onAssociationColorChanged(final Association association) {
        color = association.getColor();
        refreshStyle();
    }

    @Override
    public void onSecondAssociationItemChanged(Association association,
            PointMapItem prevItem) {
        prevItem.removeOnPointChangedListener(_secondItemMovedListener);
        final PointMapItem secondItem = association.getSecondItem();
        if (secondItem != null) {
            secondItem.addOnPointChangedListener(_secondItemMovedListener);
        }
        _updateItemPoint(1, secondItem);
    }

    private void _updateItemPoint(final int index,
            final PointMapItem pointItem) {
        final GeoPoint point = (pointItem != null) ? pointItem.getPoint()
                : null;
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _setEndPoint(index, point);
            }
        });
    }

    @Override
    public void onFirstAssociationItemChanged(Association association,
            PointMapItem prevItem) {
        prevItem.removeOnPointChangedListener(_firstItemMovedListener);
        PointMapItem firstItem = association.getFirstItem();
        if (firstItem != null) {
            firstItem.addOnPointChangedListener(_firstItemMovedListener);
        }
        _updateItemPoint(0, firstItem);
    }

    @Override
    public void onAssociationStrokeWeightChanged(Association association) {
        strokeWidth = (float) association.getStrokeWeight();
        refreshStyle();
    }

    @Override
    public void onAssociationTextChanged(Association assoc) {
        final String text = assoc.getText();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _text = text;
                if (_glText != null) {
                    _textWidth = _glText.getStringWidth(_text);
                    _textHeight = _glText.getStringHeight();
                }
            }
        });
    }

    @Override
    public void onAssociationClampToGroundChanged(Association assoc) {
        final boolean clampToGround = assoc.getClampToGround();
        this.context.queueEvent(new Runnable() {
            @Override
            public void run() {
                _clampToGround = clampToGround;
                if (_glText != null) {
                    _textWidth = _glText.getStringWidth(_text);
                    _textHeight = _glText.getStringHeight();
                }
                impl.setAltitudeMode(
                        _clampToGround ? Feature.AltitudeMode.ClampToGround
                                : Feature.AltitudeMode.Absolute);
                impl.setTessellationEnabled(_clampToGround);

                // force geometry refresh
                _setEndPoint(0, _points[0]);
            }
        });
    }

    @Override
    public void draw(GLMapView ortho, int renderPass) {
        boolean sprites = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SPRITES);
        boolean surface = MathUtils.hasBits(renderPass,
                GLMapView.RENDER_PASS_SURFACE);

        this.bounds.setWrap180(ortho.continuousScrollEnabled);
        _unwrap = ortho.idlHelper.getUnwrap(this.bounds);

        if (_points[0] != null &&
                _points[1] != null &&
                _link != Association.LINK_NONE) {

            // delegate drawing of the link to GLPolyline
            if (surface && _clampToGround || sprites && !_clampToGround)
                impl.draw(ortho);

            if (sprites)
                _drawText(ortho);
        }
    }

    private void _setEndPoint(final int index, final GeoPoint point) {
        impl.setAltitudeMode(_clampToGround ? Feature.AltitudeMode.ClampToGround
                : Feature.AltitudeMode.Absolute);
        impl.setTessellationEnabled(_clampToGround);

        _points[index] = point;

        final boolean valid = (_points[0] != null && _points[1] != null);

        LineString ls = new LineString(3);
        if (_points[0] != null)
            ls.addPoint(_points[0].getLongitude(), _points[0].getLatitude(),
                    Double.isNaN(_points[0].getAltitude()) ? 0d
                            : _points[0].getAltitude());
        if (_points[1] != null)
            ls.addPoint(_points[1].getLongitude(), _points[1].getLatitude(),
                    Double.isNaN(_points[1].getAltitude()) ? 0d
                            : _points[1].getAltitude());
        impl.setGeometry(ls);

        // dispatch bounds update
        if (valid) {
            final double lat0 = _points[0].getLatitude();
            final double lon0 = _points[0].getLongitude();
            final double lat1 = _points[1].getLatitude();
            final double lon1 = _points[1].getLongitude();

            this.context.queueEvent(new Runnable() {
                @Override
                public void run() {
                    MapView mv = MapView.getMapView();
                    final double N = Math.max(lat0, lat1); // N
                    double W = Math.min(lon0, lon1); // W
                    final double S = Math.min(lat0, lat1); // S
                    double E = Math.max(lon0, lon1); // E

                    // if both are unwrapped, wrap
                    if(Math.abs(W) > 180d && Math.abs(E) > 180d) {
                        final double ew = GeoCalculations.wrapLongitude(E);
                        final double ww = GeoCalculations.wrapLongitude(W);
                        E = Math.max(ew, ww);
                        W = Math.min(ew, ww);
                    }
                    bounds.set(N, W, S, E);
                    bounds.setWrap180(mv != null
                            && mv.isContinuousScrollEnabled());

                    dispatchOnBoundsChanged();
                }

            });
        }
    }

    private final PointMapItem.OnPointChangedListener _firstItemMovedListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            _updateItemPoint(0, item);
        }
    };

    private final PointMapItem.OnPointChangedListener _secondItemMovedListener = new PointMapItem.OnPointChangedListener() {
        @Override
        public void onPointChanged(PointMapItem item) {
            _updateItemPoint(1, item);
        }
    };

    private void _drawText(GLMapView ortho) {
        GeoPoint[] pts = _points;
        float[] points = new float[pts.length * 2 + 6];

        float xmin = Float.MAX_VALUE;
        float xmax = Float.MIN_VALUE;
        float ymin = Float.MAX_VALUE;
        float ymax = Float.MIN_VALUE;

        for (int x = 0; x < pts.length; x++) {
            GeoPoint gp = pts[x];
            if (!gp.isAltitudeValid()) {
                double alt = ortho.getTerrainMeshElevation(gp.getLatitude(),
                        gp.getLongitude());
                gp = new GeoPoint(gp.getLatitude(), gp.getLongitude(), alt);
            }
            forward(ortho, gp, ortho.scratch.pointF, _unwrap);
            points[2 * x] = ortho.scratch.pointF.x;
            points[2 * x + 1] = ortho.scratch.pointF.y;
            if (points[2 * x] < xmin)
                xmin = points[2 * x];
            if (points[2 * x] > xmax)
                xmax = points[2 * x];
            if (points[2 * x + 1] < ymin)
                ymin = points[2 * x + 1];
            if (points[2 * x + 1] > ymax)
                ymax = points[2 * x + 1];
        }

        float screenRot = 0f;

        if (_glText == null && _text != null) {
            _glText = GLText.getInstance(MapView.getTextFormat(
                    Typeface.DEFAULT, +2));
            _ninePatch = GLRenderGlobals.get(this.context)
                    .getMediumNinePatch();
            _textWidth = _glText.getStringWidth(_text);
            _textHeight = _glText.getStringHeight();
        }
        boolean drawText = false;
        if (_text == null || (xmax - xmin < _textWidth
                && ymax - ymin < _textWidth
                || _text.length() == 0)) {
            drawText = false;
            _textPoint[0] = (xmax + xmin) / 2;
            _textPoint[1] = ymin - _textHeight / 2;
            _textAngle = 0;
        } else {
            drawText = true;
            if (pts.length % 2 == 0) {
                int idx = 2 * (pts.length / 2 - 1);

                PointF startPoint = new PointF(points[idx], points[idx + 1]);
                PointF endPoint = new PointF(points[idx + 2], points[idx + 3]);

                float xmid = (int) (points[idx] + points[idx + 2]) * div_2;
                float ymid = (int) (points[idx + 1] + points[idx + 3]) * div_2;

                // obtain the bounds of the current view
                RectF _view = GLMapItem.getDefaultWidgetViewF(this.context);

                // find the point that is contained in the image, if both points are outside the 
                // image, it does not matter.

                if (startPoint.y < _view.top &&
                        startPoint.x < _view.right &&
                        startPoint.x > 0 &&
                        startPoint.y > 0) {
                    //Log.d("SHB", "start point is inside the view");
                } else {
                    //Log.d("SHB", "end point is inside the view");
                    PointF tmp = startPoint;
                    startPoint = endPoint;
                    endPoint = tmp;
                }

                // determine the intersection point, if both points intersect, center between them
                // if one point intersects, draw the text to be the midline between the point 
                // inside the view and the intersection point.

                PointF[] ip = GLMapItem._getIntersectionPoint(_view,
                        startPoint, endPoint);

                if (ip[0] != null || ip[1] != null) {
                    if (ip[0] != null && ip[1] != null) {
                        xmid = (ip[0].x + ip[1].x) / 2.0f;
                        ymid = (ip[0].y + ip[1].y) / 2.0f;
                    } else {

                        if (ip[0] != null) {
                            //Log.d("SHB", "bottom is clipped");
                            xmid = (ip[0].x + startPoint.x) / 2.0f;
                            ymid = (ip[0].y + startPoint.y) / 2.0f;
                        } else {
                            //Log.d("SHB", "top is clipped");
                            xmid = (ip[1].x + startPoint.x) / 2.0f;
                            ymid = (ip[1].y + startPoint.y) / 2.0f;
                        }
                    }
                }

                _textAngle = (float) (Math.atan2(points[idx + 1]
                        - points[idx + 3],
                        points[idx]
                                - points[idx + 2])
                        * div_180_pi)
                        - screenRot;

                _textPoint[0] = xmid;
                _textPoint[1] = ymid;
            } else {
                int idx = 2 * (pts.length - 1) / 2;
                float xmid = (int) points[idx];
                float ymid = (int) points[idx + 1];
                _textAngle = (float) (Math.atan2(points[idx - 1]
                        - points[idx + 3],
                        points[idx - 2]
                                - points[idx + 2])
                        * div_180_pi)
                        - screenRot;
                _textPoint[0] = xmid;
                _textPoint[1] = ymid;
            }
        }
        if (_textAngle > 90 || _textAngle < -90)
            _textAngle += 180;

        if (drawText) {
            GLES20FixedPipeline.glPushMatrix();
            GLES20FixedPipeline.glTranslatef(_textPoint[0], _textPoint[1],
                    0);
            GLES20FixedPipeline.glRotatef(_textAngle, 0f, 0f, 1f);
            GLES20FixedPipeline.glTranslatef(
                    -_glText.getStringWidth(_text) / 2,
                    -_glText.getStringHeight() / 2 + _glText.getDescent(),
                    0);
            GLES20FixedPipeline.glPushMatrix();
            float outlineOffset = -((GLText.getLineCount(_text) - 1) * _glText
                    .getBaselineSpacing())
                    - _glText.getDescent();
            GLES20FixedPipeline.glTranslatef(-8f, outlineOffset - 4f, 0f);
            GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.8f);
            if (_ninePatch != null) {
                _ninePatch
                        .draw(_glText.getStringWidth(_text) + 16f,
                                _glText.getStringHeight() + 8f);
            }
            GLES20FixedPipeline.glPopMatrix();
            _glText.draw(_text, 1.0f, 1.0f, 1.0f, 1.0f);

            GLES20FixedPipeline.glPopMatrix();
        }
    }
}
