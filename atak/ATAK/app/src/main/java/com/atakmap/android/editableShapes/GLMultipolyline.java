
package com.atakmap.android.editableShapes;

import com.atakmap.android.drawing.mapItems.DrawingShape;
import com.atakmap.android.maps.MultiPolyline;
import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.maps.coords.MutableGeoBounds;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapRenderer3;
import com.atakmap.map.layer.control.SurfaceRendererControl;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.util.Visitor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @deprecated {@link MultiPolyline} no longer uses its own special renderer
 */
@Deprecated
@DeprecatedApi(since = "4.4", forRemoval = true, removeAt = "4.7")
public class GLMultipolyline extends GLEditablePolyline {

    private final MultiPolyline mp;

    private final List<GLEditablePolyline> _glLines;

    private SurfaceRendererControl _surfaceCtrl;

    /**
     * Function responsible for creating a new GL MultiPolyline
     * @param surface The map surface
     * @param subject - The multipolyline to render
     */
    public GLMultipolyline(MapRenderer surface, MultiPolyline subject) {
        super(surface, subject);

        if (surface instanceof MapRenderer3)
            _surfaceCtrl = ((MapRenderer3) surface)
                    .getControl(SurfaceRendererControl.class);
        else
            surface.visitControl(null, new Visitor<SurfaceRendererControl>() {
                @Override
                public void visit(SurfaceRendererControl object) {
                    _surfaceCtrl = object;
                }
            }, SurfaceRendererControl.class);
        List<DrawingShape> _lines = subject.getLines();

        mp = subject;

        subject.getBounds(bounds);

        //Add all the individual drawing shapes to this object
        _glLines = new ArrayList<>();
        for (DrawingShape ds : _lines) {
            //Create a new GL drawing shape for each line
            _glLines.add(new GLEditablePolyline(surface, ds));
        }
        this.startObserving();
    }

    /**
     * Just loop through all the individual lines and start observing
     */
    @Override
    public void startObserving() {
        this.update();
        super.startObserving();
        for (GLEditablePolyline gl : _glLines) {
            gl.startObserving();
        }
        mp.addOnEditableChangedListener(this);
    }

    /**
     * Just loop through all the individual lines and stop observing them
     */
    @Override
    public void stopObserving() {
        this.update();
        super.stopObserving();
        for (GLEditablePolyline gl : _glLines) {
            gl.stopObserving();
        }
        mp.removeOnEditableChangedListener(this);
    }

    /**
     * Just loop through all the lines and draw them individually
     * @param ortho a GLMapView
     */
    @Override
    public void draw(GLMapView ortho, int renderPass) {
        this.update();
        for (GLEditablePolyline glep : _glLines) {
            glep.draw(ortho, renderPass);
        }
    }

    /**
     * Make sure we are always rendering an accurate representation of the multi-polyline
     * so call this to catch any changes to the individual lines before we render
     * could be a little slow as this gets called everytime draw gets called
     */
    private void update() {
        List<DrawingShape> lines = mp.getLines();
        int startSize = _glLines.size();
        int totalSize = lines.size();

        // If any of the lines have been switched out (i.e. during CoT import)
        for (int i = 0; i < startSize && i < totalSize; i++) {
            DrawingShape ds = lines.get(i);
            GLEditablePolyline el = _glLines.get(i);
            if (el.getSubject() != ds) {
                // Line instance has been changed
                el.stopObserving();
                el.release();

                el = new GLEditablePolyline(this.context, ds);
                el.startObserving();
                _glLines.set(i, el);
            }
        }

        //If the multi-polyline contains a new line than the last time
        while (startSize < totalSize) {
            //Create a new GL Line for the new line
            GLEditablePolyline glep = new GLEditablePolyline(
                    this.context, lines.get(startSize));
            glep.startObserving();
            _glLines.add(glep);

            if (_surfaceCtrl != null) {
                MutableGeoBounds gb = new MutableGeoBounds(0, 0, 0, 0);
                glep.getBounds(gb);

                _surfaceCtrl.markDirty(new Envelope(gb.getWest(), gb.getSouth(),
                        0d, gb.getEast(), gb.getNorth(), 0d), true);
            }

            startSize++;
        }

        //If a line has been deleted
        if (startSize > totalSize) {
            Iterator<GLEditablePolyline> glIte = this._glLines.iterator();
            while (glIte.hasNext()) {
                //Loop through and see which line we removed
                GLEditablePolyline glep = glIte.next();

                if (!lines.contains(glep.getSubject())) {
                    //When we found the line we removed, remove its corresponding GL line
                    glIte.remove();
                    if (_surfaceCtrl != null) {
                        MutableGeoBounds gb = new MutableGeoBounds(0, 0, 0, 0);
                        glep.getBounds(gb);

                        _surfaceCtrl.markDirty(
                                new Envelope(gb.getWest(), gb.getSouth(), 0d,
                                        gb.getEast(), gb.getNorth(), 0d),
                                true);
                    }
                    glep.stopObserving();
                    glep.release();
                }
            }
        }
    }
}
