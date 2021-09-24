
package com.atakmap.android.items;

import com.atakmap.android.maps.graphics.GLMapItem2;
import com.atakmap.coremap.log.Log;

import android.util.Pair;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.graphics.GLMapItemFactory;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.layer.Layer;
import com.atakmap.map.layer.opengl.GLLayer;
import com.atakmap.map.layer.opengl.GLLayer2;
import com.atakmap.map.layer.opengl.GLLayerFactory;
import com.atakmap.map.layer.opengl.GLLayerSpi2;
import com.atakmap.map.opengl.GLAsynchronousMapRenderable2;
import com.atakmap.map.opengl.GLMapView;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;

public class GLMapItemsDatabaseRenderer extends
        GLAsynchronousMapRenderable2<Collection<MapItem>> implements
        MapItemsDatabase.ContentListener, GLLayer {

    public final static GLLayerSpi2 SPI = new GLLayerSpi2() {
        @Override
        public int getPriority() {
            // MapItemsDatabaseLayer : Layer
            return 1;
        }

        @Override
        public GLLayer2 create(Pair<MapRenderer, Layer> arg) {
            final MapRenderer surface = arg.first;
            final Layer layer = arg.second;
            if (layer instanceof MapItemsDatabaseLayer)
                return GLLayerFactory.adapt(new GLMapItemsDatabaseRenderer(
                        surface,
                        (MapItemsDatabaseLayer) layer));
            return null;
        }
    };

    protected final MapItemsDatabaseLayer subject;
    protected final MapItemsDatabase database;
    private Map<MapItem, GLMapItem2> renderableList;
    protected final MapRenderer renderContext;

    GLMapItemsDatabaseRenderer(MapRenderer surface,
            MapItemsDatabaseLayer subject) {
        this.renderContext = surface;
        this.subject = subject;
        this.database = this.subject.getMapItemsDatabase();
        this.database.addContentListener(this);

        this.renderableList = new TreeMap<>(
                MapItem.ZORDER_RENDER_COMPARATOR);
    }

    /**************************************************************************/
    // GL Asynchronous Map Renderable

    @Override
    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SURFACE;
    }

    @Override
    public void draw(GLMapView view, int renderPass) {
        if (!this.subject.isVisible())
            return;
        super.draw(view, renderPass);
    }

    @Override
    public void draw(GLMapView view) {
        draw(view, getRenderPass());
    }

    @Override
    protected Collection<GLMapItem2> getRenderList() {
        return this.renderableList.values();
    }

    @Override
    protected Collection<MapItem> createPendingData() {
        return new LinkedList<>();
    }

    @Override
    protected void resetPendingData(Collection<MapItem> pendingData) {
        pendingData.clear();
    }

    @Override
    protected void releasePendingData(Collection<MapItem> pendingData) {
        pendingData.clear();
    }

    @Override
    protected String getBackgroundThreadName() {
        return "MapItem DB [" + this.subject.getName() + "] GL worker@"
                + Integer.toString(this.hashCode(), 16);
    }

    @Override
    protected boolean updateRenderList(ViewState state,
            Collection<MapItem> pendingData) {
        Map<MapItem, GLMapItem2> swap = new TreeMap<>(
                MapItem.ZORDER_RENDER_COMPARATOR);

        GLMapItem2 renderable;
        for (MapItem item : pendingData) {
            renderable = this.renderableList.remove(item);
            if (renderable == null) {
                renderable = GLMapItemFactory.create3(this.renderContext, item);
                if (renderable == null)
                    continue;
                renderable.startObserving();
            }

            swap.put(item, renderable);
        }

        final Collection<GLMapItem2> releaseList = this.renderableList.values();
        this.renderableList = swap;
        for (GLMapItem2 glitem : releaseList)
            glitem.stopObserving();
        this.renderContext.queueEvent(new Runnable() {
            @Override
            public void run() {
                for (GLMapItem2 renderable : releaseList)
                    renderable.release();
                releaseList.clear();
            }
        });
        return true;
    }

    @Override
    protected void query(ViewState state, Collection<MapItem> result) {
        MapItemsDatabase.MetaDataHolderCursor<MapItem> cursor = null;
        try {
            cursor = this.database.queryItems(new GeoPoint(state.northBound,
                    state.westBound),
                    new GeoPoint(state.southBound, state.eastBound),
                    state.drawMapScale,
                    true);

            while (cursor.moveToNext())
                result.add(cursor.get());
        } catch (Exception e) {
            Log.w("GLMapItemsDatabase", "Query on "
                    + this.getSubject().getName()
                    + " failed with unexpected exception.", e);
        } finally {
            if (cursor != null)
                cursor.close();
        }
    }

    /**************************************************************************/
    // Map Items Database Content Listener

    @Override
    public void contentChanged(MapItemsDatabase database) {
        this.invalidate();
    }

    /**************************************************************************/
    // GL Layer

    @Override
    public Layer getSubject() {
        return this.subject;
    }
}
