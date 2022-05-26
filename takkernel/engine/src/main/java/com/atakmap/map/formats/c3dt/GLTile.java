package com.atakmap.map.formats.c3dt;

import android.graphics.Color;

import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.opengl.GLMapView;
import com.atakmap.map.projection.ECEFProjection;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;

final class GLTile implements ContentSource.OnContentChangedListener {
    final static long FAILED_CONTENT_RETRY_MILLIS = 5 /*mins*/ * 60 /*sec/min*/ * 1000 /*ms/sec*/;

    final static int[] COLORS = new int[]
            {
                    Color.BLUE,
                    Color.CYAN,
                    Color.DKGRAY,
                    Color.GRAY,
                    Color.GREEN,
                    Color.LTGRAY,
                    Color.MAGENTA,
                    Color.RED,
                    Color.YELLOW,
                    Color.WHITE,
                    0xFF800000, // maroon
                    0xFFFF5733, // orange
                    0xFF000080, // navy
                    0xFF800080, // purple
            };

    final Tileset tileset;
    final Tile tile;
    final int level;
    final String baseUri;
    final double radius;
    final double paddedRadius;
    final GeoPoint centroid;
    GLTile[] children;
    GLContent content;
    long contentLoadTime;
    Matrix transform;
    Envelope aabb;
    ResourceManager resmgr;
    ContentSource source;
    ContentLoader contentLoader;

    GLTile(Tileset tileset, Tile tile, int level, String baseUri) {
        this.tileset = tileset;
        this.tile = tile;
        this.level = level;
        this.baseUri = baseUri;
        this.transform = Tile.accumulate(tile);
        this.aabb = Tile.approximateBounds(tile);

        if(tile.boundingVolume instanceof Volume.Region) {
            Volume.Region region = (Volume.Region)tile.boundingVolume;

            final double east = Math.toDegrees(region.east);
            final double west = Math.toDegrees(region.west);
            final double north = Math.toDegrees(region.north);
            final double south = Math.toDegrees(region.south);

            centroid = new GeoPoint((north+south)/2d, (east+west)/2d, (region.maximumHeight+region.minimumHeight)/2d);

            final double metersDegLat = GeoCalculations.approximateMetersPerDegreeLatitude(centroid.getLatitude());
            final double metersDegLng = GeoCalculations.approximateMetersPerDegreeLongitude(centroid.getLatitude());

            radius = MathUtils.distance(west*metersDegLng, north*metersDegLat, region.maximumHeight, east*metersDegLng, south*metersDegLat, region.minimumHeight) / 2d;

            final double pad = Math.max(metersDegLat, metersDegLng)/Math.min(metersDegLat, metersDegLng);
            paddedRadius = radius*pad;
        } else if(tile.boundingVolume instanceof Volume.Sphere) {
            Volume.Sphere sphere = (Volume.Sphere)tile.boundingVolume;

            radius = sphere.radius;

            PointD center = new PointD(sphere.centerX, sphere.centerY, sphere.centerZ);
            // transform the center
            if(transform != null)
                transform.transform(center, center);
            centroid = ECEFProjection.INSTANCE.inverse(center, null);

            final double metersDegLat = GeoCalculations.approximateMetersPerDegreeLatitude(centroid.getLatitude());
            final double metersDegLng = GeoCalculations.approximateMetersPerDegreeLongitude(centroid.getLatitude());

            final double pad = Math.max(metersDegLat, metersDegLng)/Math.min(metersDegLat, metersDegLng);
            paddedRadius = radius*pad;
        } else if(tile.boundingVolume instanceof Volume.Box) {
            Volume.Box box = (Volume.Box)tile.boundingVolume;

            radius = MathUtils.max(
                    MathUtils.distance(box.xDirHalfLen[0], box.xDirHalfLen[1], box.xDirHalfLen[2], 0d, 0d, 0d),
                    MathUtils.distance(box.yDirHalfLen[0], box.yDirHalfLen[1], box.yDirHalfLen[2], 0d, 0d, 0d),
                    MathUtils.distance(box.zDirHalfLen[0], box.zDirHalfLen[1], box.zDirHalfLen[2], 0d, 0d, 0d));

            PointD center = new PointD(box.centerX, box.centerY, box.centerZ);
            // transform the center
            if(transform != null)
                transform.transform(center, center);
            centroid = ECEFProjection.INSTANCE.inverse(center, null);

            final double metersDegLat = GeoCalculations.approximateMetersPerDegreeLatitude(centroid.getLatitude());
            final double metersDegLng = GeoCalculations.approximateMetersPerDegreeLongitude(centroid.getLatitude());

            final double pad = Math.max(metersDegLat, metersDegLng)/Math.min(metersDegLat, metersDegLng);
            paddedRadius = radius*pad;
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Returns <code>true</code> if content was rendered
     * @param view
     * @return
     */
    public boolean draw(MapRendererState view, boolean skipMaxSSESphere) {
        // do a quick check up front against nominal resolution
        if (((radius * 2d) / view.scene.gsd) < tileset.maxScreenSpaceError && !skipMaxSSESphere) {
            release();
            return false;
        }

        // make sure tile is in bounds
        if(cull(view, this)) {
            release();
            return false;
        }

        final double metersPerPixelAtD = computeMetersPerPixel(view);
        final double spherePixelsAtD = (radius * 2d) / metersPerPixelAtD;

        // if sphere at distance is less than max SSE, skip render
        if (spherePixelsAtD < tileset.maxScreenSpaceError &&
            !skipMaxSSESphere) {

            release();
            return false;
        }

        // estimate screen space error
        final double sse = tile.geometricError / metersPerPixelAtD;

        // if SSE is sufficiently large, draw children
        boolean drawSelf = true;
        if (sse > tileset.maxScreenSpaceError) {
            // XXX - this part needs to be re-explored
            if (children == null && tile.children != null) {
                children = new GLTile[tile.children.length];
                for (int i = 0; i < tile.children.length; i++) {
                    children[i] = new GLTile(tileset, tile.children[i], level + 1, this.baseUri);
                    children[i].resmgr = resmgr;
                    children[i].source = source;
                }
            }
            if (children != null) {
                int childrenToDraw = 0;
                int childrenDrawn = 0;
                for (int i = 0; i < children.length; i++) {
                    // if child does not intersect viewport, skip
                    if(cull(view, children[i])) {
                        children[i].release();
                        continue;
                    }

                    // draw the child, since we passed parent SSE test, the
                    // child must draw its content if replace
                    childrenToDraw++;
                    final boolean drawn = children[i].draw(view, (tile.refine == Refine.Replace));
                    if(drawn)
                        childrenDrawn++;
                }
                // we're drawing ourself if:
                // - refine mode is add
                // OR
                // - no children were drawn or not all visible children drew content
                drawSelf = (tile.refine == Refine.Add) || (childrenToDraw == 0 || childrenDrawn<childrenToDraw);
            }
        } else if(children != null) {
            for(int i = 0; i < children.length; i++)
                children[i].release();
            children = null;
        }

        //GLVolume.draw(view, aabb, COLORS[level%COLORS.length]);
        if (!drawSelf) {
            // content has been drawn, just not this tile's content
            return true;
        }

        // if there's no content OR time to retry, create the loader and submit the job
        if ((content == null && this.tile.content != null) ||
                (content != null && content.getState() == GLContent.State.Failed &&
                (System.currentTimeMillis() - contentLoadTime > FAILED_CONTENT_RETRY_MILLIS))) {
            this.content = null;
            if (contentLoader == null) {
                source.removeOnContentChangedListener(this);
                contentLoader = new ContentLoader(resmgr, this.source, this.baseUri, this.tile);
                resmgr.submit(contentLoader);
            }
            // once the content is loaded, transfer it to the tile
            if (contentLoader.isDone()) {
                this.content = contentLoader.transfer();
                contentLoader.dispose();
                contentLoader = null;

                // store last load time for retry on failure
                this.contentLoadTime = System.currentTimeMillis();
                if(this.content == null || this.content.getState() == GLContent.State.Failed) {
                    source.addOnContentChangedListener(this);
                }
            }
        }
        boolean selfDrawn = false;
        if (content != null && content.getState() == GLContent.State.Loaded) {
            selfDrawn = content.draw(view);
        }
        return selfDrawn;
    }

    private double computeMetersPerPixel(MapRendererState view) {
        // XXX - distance camera to object
        view.scene.mapProjection.forward(centroid, view.scratch.pointD);
        final double centroidWorldX = view.scratch.pointD.x;
        final double centroidWorldY = view.scratch.pointD.y;
        final double centroidWorldZ = view.scratch.pointD.z;

        double cameraWorldX = view.scene.camera.location.x;
        double cameraWorldY = view.scene.camera.location.y;
        double cameraWorldZ = view.scene.camera.location.z;

        // distance of the camera to the centroid of the tile
        final double dcam = MathUtils.distance(
                cameraWorldX * view.scene.displayModel.projectionXToNominalMeters,
                cameraWorldY * view.scene.displayModel.projectionYToNominalMeters,
                cameraWorldZ * view.scene.displayModel.projectionZToNominalMeters,
                centroidWorldX * view.scene.displayModel.projectionXToNominalMeters,
                centroidWorldY * view.scene.displayModel.projectionYToNominalMeters,
                centroidWorldZ * view.scene.displayModel.projectionZToNominalMeters
        );

        double metersPerPixelAtD = (dcam * Math.tan(view.scene.camera.fov / 2d) / ((view.top - view.bottom) / 2d));
        // if bounding sphere does not contain camera, compute meters-per-pixel at centroid,
        // else use nominal meters-per-pixel
        if (dcam <= radius) {
            // XXX - 
            return 0.01d; // 1cm
        }
        return metersPerPixelAtD;
    }

    // XXX - not working as expected
    private double computeScreenSpaceError(MapRendererState view) {
        // XXX - distance camera to object
        view.scene.mapProjection.forward(centroid, view.scratch.pointD);
        final double centroidWorldX = view.scratch.pointD.x;
        final double centroidWorldY = view.scratch.pointD.y;
        final double centroidWorldZ = view.scratch.pointD.z;

        double cameraWorldX = view.scene.camera.location.x;
        double cameraWorldY = view.scene.camera.location.y;
        double cameraWorldZ = view.scene.camera.location.z;

        // distance of the camera to the centroid of the tile
        final double dcam = MathUtils.distance(
                cameraWorldX * view.scene.displayModel.projectionXToNominalMeters,
                cameraWorldY * view.scene.displayModel.projectionYToNominalMeters,
                cameraWorldZ * view.scene.displayModel.projectionZToNominalMeters,
                centroidWorldX * view.scene.displayModel.projectionXToNominalMeters,
                centroidWorldY * view.scene.displayModel.projectionYToNominalMeters,
                centroidWorldZ * view.scene.displayModel.projectionZToNominalMeters
        );

        final double screenResolutionpParam = ((view.top - view.bottom) / 2d) / Math.tan(view.scene.camera.fov / 2d);
        return screenResolutionpParam * (tile.geometricError /dcam);
    }

    static boolean cull(MapRendererState view, GLTile tile) {
        // perform frustum culling. `flat` projection will use AABB; `globe`
        // projection will use bounding sphere, as it's unlikely that the
        // AABB will be a tighter volume
        if(view.drawSrid == 4326) {
            Envelope aabb = tile.aabb;
            return !MapSceneModel.intersects(view.scene, aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
        } else {
            return !MapSceneModel.intersects(view.scene, tile.centroid.getLatitude(), tile.centroid.getLongitude(), tile.centroid.getAltitude(), tile.radius);
        }
    }


    public void release() {
        if(contentLoader != null) {
            contentLoader.dispose();
            contentLoader = null;
        }
        if(content != null) {
            content.release();
            content = null;
        }
        if(this.children != null) {
            for (int i = 0; i < this.children.length; i++)
                this.children[i].release();
            this.children = null;
        }
    }

    public int getRenderPass() {
        return GLMapView.RENDER_PASS_SCENES;
    }

    @Override
    public void onContentChanged(ContentSource client) {
        contentLoadTime = (System.currentTimeMillis()-FAILED_CONTENT_RETRY_MILLIS-1L);
    }
}
