
package com.atakmap.android.helloworld;

import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Pair;

import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapTextFormat;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.android.maps.graphics.GLMapItemSpi3;
import com.atakmap.android.maps.graphics.GLPointMapItem2;
import com.atakmap.android.maps.graphics.GLMarker2;

import com.atakmap.map.hittest.HitTestResult;
import com.atakmap.map.hittest.HitTestQueryParameters;
import com.atakmap.map.MapRenderer3;

import com.atakmap.coremap.maps.coords.GeoPointMetaData;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.opengl.GLMapBatchable2;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.opengl.GLRenderGlobals;
import com.atakmap.math.MathUtils;
import com.atakmap.opengl.GLES20FixedPipeline;
import com.atakmap.opengl.GLNinePatch;
import com.atakmap.opengl.GLRenderBatch2;
import com.atakmap.opengl.GLText;

public class GLSpecialMarker extends GLPointMapItem2
        implements GLMapBatchable2 {

    private GeoPointMetaData gpm = new GeoPointMetaData();

    public final static GLMapItemSpi3 SPI = new GLMapItemSpi3() {
        @Override
        public int getPriority() {
            // TargetItem : PointMapItem : MapItem
            return 2;
        }

        @Override
        public GLMapItem2 create(Pair<MapRenderer, MapItem> object) {
            final MapRenderer surface = object.first;
            final MapItem item = object.second;

            if (!("b-g-n-M-O-B".equals(item.getType())))
                return null;

            if (!(item instanceof Marker))
                return null;

            return new GLSpecialMarker(surface, (Marker) item);
        }

    };

    /**************************************************************************/

    private static GLText text = null;

    private final MapItem subject;

    final GLMarker2 markerRenderer;

    public GLSpecialMarker(MapRenderer surface, PointMapItem subject) {
        super(surface, subject, GLMapView.RENDER_PASS_SPRITES);
        markerRenderer = new GLMarker2(surface, (Marker) subject); //, GLMapView.RENDER_PASS_SPRITES);
        this.subject = subject;
    }

    @Override
    public void startObserving() {
        super.startObserving();
        markerRenderer.startObserving();
        
    }

    @Override
    public void stopObserving() {
        super.stopObserving();
        markerRenderer.stopObserving();
    }

    @Override
    public void setLollipopsVisible(boolean v) {
        super.setLollipopsVisible(v);
        markerRenderer.setLollipopsVisible(v);
    }

    @Override
    public void setClampToGroundAtNadir(boolean v) {
        super.setClampToGroundAtNadir(v);
        markerRenderer.setClampToGroundAtNadir(v);
    }

    @Override
    public boolean getClampToGroundAtNadir() {
        return super.getClampToGroundAtNadir();
    }

    /**************************************************************************/

    
    
    @Override
    public void draw(GLMapView ortho, int renderPass) {

        markerRenderer.draw(ortho, renderPass);

        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        ortho.scratch.geo.set(this.latitude,
                ortho.idlHelper.wrapLongitude(this.longitude));
        if (ortho.drawTilt > 0d)
            ortho.scratch.geo.set(altHae);

        forward(ortho, ortho.scratch.geo, ortho.scratch.pointD);
        float xpos = (float) ortho.scratch.pointD.x;
        float ypos = (float) ortho.scratch.pointD.y;
        float zpos = (float) ortho.scratch.pointD.z;

        if (ortho.drawTilt > 0d) {
            // move up ~5 pixels from surface
            ypos += 5;
        }

        if (text == null)
            text = GLText.getInstance(new MapTextFormat(Typeface.DEFAULT_BOLD,
                    MapView.getDefaultTextFormat().getFontSize()));

        final float offx = (ortho.drawTilt > 0d) ? 40f : 20f;
        final float offy = 20f;

        GLES20FixedPipeline.glPushMatrix();
        GLES20FixedPipeline.glTranslatef(xpos + offx, ypos + offy, zpos);

        StringBuilder label = new StringBuilder();
        final String src = this.gpm.getGeopointSource();
        label.append("INFO");
        final int labelColor = Color.YELLOW;

        GLNinePatch smallNinePatch = GLRenderGlobals.get(this.context)
                .getSmallNinePatch();
        if (smallNinePatch != null) {
            GLES20FixedPipeline.glColor4f(0f, 0f, 0f, 0.6f);
            GLES20FixedPipeline.glPushMatrix();

            GLES20FixedPipeline
                    .glTranslatef(-4f, -text.getDescent(), 0f);
            final float textWidth = text.getStringWidth(label.toString());
            final float textHeight = text.getStringHeight();
            smallNinePatch.draw(textWidth + 8f, textHeight);
            GLES20FixedPipeline.glPopMatrix();

        }

        text.draw(label.toString(),
                Color.red(labelColor) / 255f,
                Color.green(labelColor) / 255f,
                Color.blue(labelColor) / 255f,
                Color.alpha(labelColor) / 255f);

        GLES20FixedPipeline.glPopMatrix();

    }

    @Override
    protected HitTestResult hitTestImpl(MapRenderer3 renderer,
            HitTestQueryParameters params) {
        return markerRenderer.hitTest(renderer, params);
    }


    /**************************************************************************/

    @Override
    public void batch(GLMapView view, GLRenderBatch2 batch, int renderPass) {
        if (!MathUtils.hasBits(renderPass, this.renderPass))
            return;

        // XXX - implement batching 
        batch.end();
        this.draw(view, renderPass);
        batch.begin();
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        super.onPointChanged(item);
        gpm = item.getGeoPointMetaData();
    }
}
