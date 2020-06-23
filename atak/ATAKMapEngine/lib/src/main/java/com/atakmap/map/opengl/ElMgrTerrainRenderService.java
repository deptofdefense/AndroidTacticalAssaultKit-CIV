package com.atakmap.map.opengl;

import android.opengl.GLES30;

import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.AtakMapView;
import com.atakmap.map.MapRenderer;
import com.atakmap.map.MapSceneModel;
import com.atakmap.map.elevation.ElevationManager;
import com.atakmap.map.elevation.ElevationSource;
import com.atakmap.map.elevation.ElevationSourceManager;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.feature.geometry.GeometryFactory;
import com.atakmap.map.layer.model.MeshBuilder;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelBuilder;
import com.atakmap.map.layer.model.ModelInfo;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.math.PointD;
import com.atakmap.opengl.GLTexture;
import com.atakmap.opengl.Skirt;
import com.atakmap.util.ReferenceCount;

import java.lang.ref.Reference;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class ElMgrTerrainRenderService extends TerrainRenderService implements ElevationSourceManager.OnSourcesChangedListener, ElevationSource.OnContentChangedListener {

    private static final String TAG = "ElMgrTerrainRenderService";

    private List<QuadNode> queue = new ArrayList<QuadNode>();

    private MapSceneModel[] scene;
    private int numScenes;
    private boolean dispose;

    /** root quadnode for eastern hemisphere, 180x180 */
    private QuadNode east = new QuadNode(-1, 0, 0d, -90d, 180d, 90d);
    /** root quadnode for western hemisphere, 180x180 */
    private QuadNode west = new QuadNode(-1, 0, -180d, -90d, 0d, 90d);

    private WorldTerrain worldTerrain = new WorldTerrain();

    /**
     * Eastern and Western hemisphere quads. These are the roots from which all
     * terrain tile collection should occur.
     */
    private QuadNode[] roots = new QuadNode[]
    {
    // east
            new QuadNode(east, 0d, 0, 90d, 90d),
            new QuadNode(east, 90d, 0, 180d, 90d),
            new QuadNode(east, 90d, -90d, 180d, 0d),
            new QuadNode(east, 0d, -90d, 90d, 0d),

    // west
            new QuadNode(west, -180d, 0, -90d, 90d),
            new QuadNode(west, -90d, 0, 0d, 90d),
            new QuadNode(west, -90d, -90d, 0d, 0d),
            new QuadNode(west, -180d, -90d, -90d, 0d),
    };

    private static int TERRAIN_LEVEL = 6;

    Thread fetchWorker = new Thread(new TileFetchWorker(), TAG);
    Thread requestWorker = new Thread(new RequestHandlerWorker(), TAG);

    int nodeCount;

    int terrainVersion = 1;

    int numPosts = 32;
    double resadj = 32d;
    double resadj2 = 8d;
    double camLocAdj = 2.5d;
    boolean sticky = true;

    int sourceVersion;

    int sceneVersion = -1;

    MapRenderer renderer;

    ElMgrTerrainRenderService(MapRenderer renderer) {
        this.renderer = renderer;
        scene = new MapSceneModel[2];
        numScenes = 0;

        sourceVersion = 0;

        fetchWorker.setPriority(Thread.NORM_PRIORITY);
        fetchWorker.start();

        requestWorker.setPriority(Thread.NORM_PRIORITY);
        requestWorker.start();
    }

    public synchronized void dispose() {
        this.dispose = true;
        this.notifyAll();
    }

    @Override
    public synchronized int lock(GLMapView view, Collection<GLMapView.TerrainTile> tiles) {
        if(this.dispose)
            return this.terrainVersion;
        scene[0] = view.scene;
        numScenes = 1;
        {
            if (view.crossesIDL) {
                double focusLng = view.drawLng;
                if (view.idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper.HEMISPHERE_WEST)
                    focusLng += 360d;
                else if (view.idlHelper.getPrimaryHemisphere() == GLAntiMeridianHelper.HEMISPHERE_EAST)
                    focusLng -= 360d;
                else
                    throw new IllegalStateException();

                view.scratch.geo.set(GeoPoint.UNKNOWN);
                view.scratch.geo.set(view.drawLat, focusLng);
                MapSceneModel scene2 = new MapSceneModel(AtakMapView.DENSITY * 240f,
                        view.scene.width, view.scene.height,
                        view.scene.mapProjection,
                        view.scratch.geo,
                        view.scene.focusx, view.scene.focusy,
                        view.scene.camera.azimuth, view.scene.camera.elevation + 90d,
                        view.scene.gsd,
                        view.continuousScrollEnabled);

                // account for flipping of y-axis for OpenGL coordinate space
                scene2.inverse.translate(0d, view.scene.height, 0d);
                scene2.inverse.scale(1d, -1d, 1d);

                view.scratch.matrix.setToScale(1d, -1d, 1d);
                scene2.forward.preConcatenate(view.scratch.matrix);
                view.scratch.matrix.setToTranslation(0d, view.scene.height, 0d);
                scene2.forward.preConcatenate(view.scratch.matrix);

                scene[numScenes++] = scene2;
            }
        }

        // data is invalid if SRID changed
        boolean invalid = (worldTerrain.srid != view.drawSrid);
        // if `front` is empty or content invalid repopulate with "root" tiles
        // temporarily until new data is fetched
        if(worldTerrain.tiles.isEmpty() || invalid) {
            // we're draining `front`, break any locks
            for(TerrainTileRef tile : worldTerrain.tiles)
                tile.dereference();
            worldTerrain.tiles.clear();

            for(int i = 0; i < roots.length; i++) {
                for(int j = 0; j < numScenes; j++) {
                    worldTerrain.tiles.add(fetch(OSMUtils.mapnikTileResolution(roots[i].level) * 10d, roots[i].bounds, view.drawSrid, numPosts, numPosts, false));
                }
            }
            worldTerrain.srid = view.drawSrid;
        }

        // signal the request handler
        if(invalid || sceneVersion != view.drawVersion) {
            sceneVersion = view.drawVersion;
            this.notifyAll();
        }

        // copy the tiles into the client collection. acquire new locks on each
        // tile. these locks will be relinquished by the client when it calls
        // `unlock`
        for(TerrainTileRef tile : worldTerrain.tiles) {
            // acquire a new reference on the tile since it's being passed
            tiles.add(tile.reference());
        }

        this.elevationStats.reset();
        for(GLMapView.TerrainTile tile : tiles) {
            this.elevationStats.observe(tile.aabb.minZ);
            this.elevationStats.observe(tile.aabb.maxZ);
        }

        return this.terrainVersion;
    }

    // stub for chris to implement
    @Override
    public int getTerrainVersion() {
        return terrainVersion;
    }

    @Override
    public synchronized void unlock(Collection<GLMapView.TerrainTile> tiles) {
        // relinquish the references
        for(GLMapView.TerrainTile tile : tiles)
            ((TerrainTileRef)tile.opaque).dereference();
    }

    @Override
    public double getElevation(GeoPoint geo) {
        return ElevationManager.getElevation(geo.getLatitude(), geo.getLongitude(), null);
    }

    static TerrainTileRef fetch(double resolution, Envelope mbb, int srid, int numPostsLat, int numPostsLng, boolean fetchEl) {
        double[] els = new double[numPostsLat*numPostsLng];
        if(fetchEl) {
            ArrayList<GeoPoint> pts = new ArrayList<GeoPoint>(numPostsLat * numPostsLng);
            for (int postLat = 0; postLat < numPostsLat; postLat++) {
                for (int postLng = 0; postLng < numPostsLng; postLng++) {
                    pts.add(new GeoPoint(mbb.minY + ((mbb.maxY - mbb.minY) / (numPostsLat-1)) * postLat, mbb.minX + ((mbb.maxX - mbb.minX) / (numPostsLng-1)) * postLng));
                }
            }
            final boolean constrainQueryRes = false;

            ElevationSource.QueryParameters params = new ElevationSource.QueryParameters();
            params.order = Collections.<ElevationSource.QueryParameters.Order>singletonList(ElevationSource.QueryParameters.Order.ResolutionDesc);
            if(constrainQueryRes)
                params.maxResolution = resolution;
            params.spatialFilter = GeometryFactory.fromEnvelope(mbb);

            // try to fill all values using high-to-low res elevation chunks
            // covering the AOI
            if(!ElevationManager.getElevation(pts.iterator(), els, params) && constrainQueryRes) {
                // if there are holes, fill in using low-to-high res elevation
                // chunks covering the AOI
                params.order = Collections.<ElevationSource.QueryParameters.Order>singletonList(ElevationSource.QueryParameters.Order.ResolutionAsc);
                params.maxResolution = Double.NaN;

                ArrayList<GeoPoint> pts2 = new ArrayList<GeoPoint>(pts.size());
                double[] els2 = new double[pts.size()];
                for(int i = 0; i < els.length; i++) {
                    if (Double.isNaN(els[i])) {
                        pts2.add(pts.get(i));
                    }
                }

                // fetch the elevations
                ElevationManager.getElevation(pts2.iterator(), els2, params);

                int els2Idx = 0;
                for(int i = 0; i < els.length; i++) {
                    if (Double.isNaN(els[i])) {
                        els[i] = els2[els2Idx++];
                        if(els2Idx == pts2.size())
                            break;
                    }
                }
            }
        }

        // number of edge vertices is equal to perimeter length, plus one, to
        // close the linestring
        final int numEdgeVertices = ((numPostsLat-1)*2)+((numPostsLng-1)*2) + 1;

        double minEl = Double.isNaN(els[0]) ? 0d : els[0];
        double maxEl = Double.isNaN(els[0]) ? 0d : els[0];
        for(int i = 1; i < els.length; i++) {
            if(els[i] < minEl)
                minEl = els[i];
            if(els[i] > maxEl)
                maxEl = els[i];
        }

        double localOriginX = (mbb.minX+mbb.maxX)/2d;
        double localOriginY = (mbb.minY+mbb.maxY)/2d;
        double localOriginZ = (minEl+maxEl)/2d;

        GLMapView.TerrainTile retval = new GLMapView.TerrainTile();
        retval.info.srid = 4326;
        retval.info.localFrame = Matrix.getTranslateInstance(localOriginX, localOriginY, localOriginZ);
        retval.info.minDisplayResolution = resolution;

        final float skirtHeight = 500f;

        final int numIndices = GLTexture.getNumQuadMeshIndices(numPostsLat - 1, numPostsLng - 1)
                + 2 // degenerate link to skirt
                + Skirt.getNumOutputIndices(GLES30.GL_TRIANGLE_STRIP, numEdgeVertices);

        ShortBuffer indices = ShortBuffer.allocate(numIndices);
        GLTexture.createQuadMeshIndexBuffer(numPostsLat - 1, numPostsLng - 1, indices);

        final int skirtOffset = indices.position();

        // to achieve CW winding order, edge indices need to be specified
        // in CCW order
        ShortBuffer edgeIndices = ShortBuffer.allocate(numEdgeVertices);
        // top edge (right-to-left), exclude last
        for(int i = numPostsLng-1; i > 0; i--)
            edgeIndices.put((short)i);
        // left edge (bottom-to-top), exclude last
        for(int i = 0; i < (numPostsLat-1); i++) {
            final int idx = (i*numPostsLng);
            edgeIndices.put((short)idx);
        }
        // bottom edge (left-to-right), exclude last
        for(int i = 0; i < (numPostsLng-1); i++) {
            final int idx = ((numPostsLat-1)*numPostsLng)+i;
            edgeIndices.put((short)idx);
        }
        // right edge (top-to-bottom), exclude last
        for(int i = numPostsLat-1; i > 0; i--)
            edgeIndices.put((short)((i*numPostsLng)+(numPostsLng-1)));

        // close the loop by adding first-point-as-last
        edgeIndices.put((short)(numPostsLng-1));

        edgeIndices.flip();

        // insert the degenerate, last index of the mesh and first index
        // for the skirt
        indices.put(indices.get(GLTexture.getNumQuadMeshIndices(numPostsLat-1, numPostsLng-1)-1));
        indices.put(edgeIndices.get(0));

        // XXX - need to duplicate vertex buffer right now. In the future, switch to MeshBuilder method accepting pre-allocated vertex/index buffers. This method is causing SEGFAULT at the moment
        MeshBuilder model = new MeshBuilder(Model.VERTEX_ATTR_POSITION, true, Model.DrawMode.TriangleStrip);
        model.reserveVertices((numPostsLat*numPostsLng)+Skirt.getNumOutputVertices(numEdgeVertices));
        model.setWindingOrder(Model.WindingOrder.Clockwise);

        FloatBuffer fb = FloatBuffer.allocate(numPostsLat*numPostsLng*3 + Skirt.getNumOutputVertices(numEdgeVertices)*3);
        for(int postLat = 0; postLat < numPostsLat; postLat++) {
            // tile row
            for(int postLng = 0; postLng < numPostsLng; postLng++) {
                final double lat = mbb.minY+((mbb.maxY-mbb.minY)/(numPostsLat-1))*postLat;
                final double lng = mbb.minX+((mbb.maxX-mbb.minX)/(numPostsLng-1))*postLng;
                final double hae = Double.isNaN(els[(postLat*numPostsLng)+postLng]) ? 0d : els[(postLat*numPostsLng)+postLng];

                final double x = lng-localOriginX;
                final double y = lat-localOriginY;
                final double z = hae-localOriginZ;

                fb.put((float)x);
                fb.put((float)y);
                fb.put((float)z);
            }
        }
        fb.flip();

        Skirt.create(GLES30.GL_TRIANGLE_STRIP,
                     3,
                     fb,
                     edgeIndices,
                     numEdgeVertices,
                     indices,
                     skirtHeight);
        Unsafe.free(edgeIndices);

        // transfer the vertex data
        final int numVerts = fb.limit()/3;
        for(int i = 0; i < numVerts; i++) {
            model.addVertex(fb.get(), fb.get(), fb.get(),
                    0f, 0f,
                    0f, 0f, 0f,
                    1f, 1f, 1f, 1f);
        }
        Unsafe.free(fb);

        model.reserveIndices(indices.limit());
        model.addIndices(indices);

        ModelBuilder mbuilder = new ModelBuilder();
        mbuilder.addMesh(model);
        retval.model = mbuilder.build();
        retval.skirtIndexOffset = skirtOffset;
        retval.numIndices = indices.limit();
        retval.aabb = new Envelope(0d, 0d, 0d, 0d, 0d, 0d);
        Models.transform(retval.model.getAABB(), retval.info, retval.aabb, WGS84);

        if(srid != retval.info.srid) {
            ModelInfo xformInfo = new ModelInfo(retval.info);
            xformInfo.localFrame = null;
            xformInfo.srid = srid;
            Model omodel = retval.model;
            try {
                retval.model = Models.transform(retval.info, retval.model, xformInfo);
                retval.info = xformInfo;
            } finally {
                if(omodel != retval.model)
                    omodel.dispose();
            }
        }

        return new TerrainTileRef(retval);
    }

    synchronized void enqueue(QuadNode node) {
        if(node.queued)
            return;
        node.queued = true;
        queue.add(node);
        this.notifyAll();
    }

    @Override
    public synchronized void onSourceAttached(ElevationSource src) {
        sourceVersion++;
        terrainVersion++;
        renderer.requestRefresh();

        src.addOnContentChangedListener(this);
    }

    @Override
    public synchronized void onSourceDetached(ElevationSource src) {
        src.removeOnContentChangedListener(this);

        sourceVersion++;
        terrainVersion++;
        renderer.requestRefresh();
    }

    @Override
    public synchronized void onContentChanged(ElevationSource src) {
        sourceVersion++;
        terrainVersion++;
        renderer.requestRefresh();
    }

    class QuadNode {
        QuadNode ul;
        QuadNode ur;
        QuadNode lr;
        QuadNode ll;

        Envelope bounds;
        int level;

        int lastRequestLevel;

        TerrainTileRef tile;
        boolean queued;

        QuadNode parent;

        int sourceVersion;
        int srid;

        QuadNode(QuadNode parent, double minX, double minY, double maxX, double maxY) {
            this.parent = parent;
            this.bounds = new Envelope(minX, minY, -900d, maxX, maxY, 19000d);
            this.level = (this.parent != null) ? this.parent.level+1 : 0;
            this.srid = (this.parent != null) ? parent.srid : -1;

            nodeCount++;
            //Log.i("ElMgrTerrainRenderService", "Create node " + nodeCount);
        }

        QuadNode(int srid, int level, double minX, double minY, double maxX, double maxY) {
            this(null, minX, minY, maxX, maxY);
            this.level = level;
            this.srid = srid;

            //Log.i("ElMgrTerrainRenderService", "Create node " + nodeCount);
        }

        void shareTileReference(Collection<TerrainTileRef> tiles) {
            this.tile.reference();
            tiles.add(this.tile);
        }

        boolean needsFetch(QuadNode node, int fetchSrid) {
            return node == null ||
                   node.sourceVersion != ElMgrTerrainRenderService.this.sourceVersion ||
                   (!node.queued &&
                           (node.tile == null ||
                            node.tile.value.info.srid != fetchSrid));
        }
        public boolean collect(MapSceneModel scene, WorldTerrain terrain) {
            final int level = computeTargetLevel(scene);
            if(level > this.level) {
                final double centerX = (this.bounds.minX+this.bounds.maxX)/2d;
                final double centerY = (this.bounds.minY+this.bounds.maxY)/2d;

                // compute child intersections
                final boolean recurseLL = MapSceneModel.intersects(
                        scene,
                        bounds.minX, bounds.minY, bounds.minZ, centerX, centerY, bounds.maxZ);
                final boolean recurseLR = MapSceneModel.intersects(
                        scene,
                        centerX, bounds.minY, bounds.minZ, bounds.maxX, centerY, bounds.maxZ);
                final boolean recurseUR = MapSceneModel.intersects(
                        scene,
                        centerX, centerY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
                final boolean recurseUL = MapSceneModel.intersects(
                        scene,
                        bounds.minX, centerY, bounds.minZ, centerX, bounds.maxY, bounds.maxZ);

                final boolean fetchul = needsFetch(ul, terrain.srid);
                final boolean fetchur = needsFetch(ur, terrain.srid);
                final boolean fetchlr = needsFetch(lr, terrain.srid);
                final boolean fetchll = needsFetch(ll, terrain.srid);
                final boolean fetchingul = (ul != null && ul.queued);
                final boolean fetchingur = (ur != null && ur.queued);
                final boolean fetchinglr = (lr != null && lr.queued);
                final boolean fetchingll = (ll != null && ll.queued);

                // fetch tile nodes
                if(fetchll && !fetchingll) {
                    if(ll == null)
                        ll = new QuadNode(this, this.bounds.minX, this.bounds.minY, centerX, centerY);
                    enqueue(ll);
                }
                if(fetchlr && !fetchinglr) {
                    if(lr == null)
                        lr = new QuadNode(this, centerX, this.bounds.minY, this.bounds.maxX, centerY);
                    enqueue(lr);
                }
                if(fetchur && !fetchingur) {
                    if(ur == null)
                        ur = new QuadNode(this, centerX, centerY, this.bounds.maxX, this.bounds.maxY);
                    enqueue(ur);
                }
                if(fetchul && !fetchingul) {
                    if(ul == null)
                        ul = new QuadNode(this, this.bounds.minX, centerY, centerX, this.bounds.maxY);
                    enqueue(ul);
                }

                // only allow recursion if all nodes have been fetched
                final boolean recurse = (!(fetchll || fetchingll) || (fetchll && ll.tile != null)) &&
                                        (!(fetchlr || fetchinglr) || (fetchlr && lr.tile != null)) &&
                                        (!(fetchur || fetchingur) || (fetchur && ur.tile != null)) &&
                                        (!(fetchul || fetchingul) || (fetchul && ul.tile != null)) &&
                                        (recurseLL || recurseUL || recurseUR || recurseLR);
                if(recurseLL) {
                     if(recurse) {
                        ll.collect(scene, terrain);
                    }
                } else if(ll != null) {
                    ll.reset(false);
                    if(recurse)
                        ll.shareTileReference(terrain.tiles);
                }
                if(recurseLR) {
                    if(recurse) {
                        lr.collect(scene, terrain);
                    }
                } else if(lr != null) {
                    lr.reset(false);
                    if(recurse)
                        lr.shareTileReference(terrain.tiles);
                }
                if(recurseUR) {
                    if(recurse) {
                        ur.collect(scene, terrain);
                    }
                } else if(ur != null) {
                    ur.reset(false);
                    if(recurse)
                        ur.shareTileReference(terrain.tiles);
                }
                if(recurseUL) {
                    if(recurse) {
                        ul.collect(scene, terrain);
                    }
                } else if(ul != null) {
                    ul.reset(false);
                    if(recurse)
                        ul.shareTileReference(terrain.tiles);
                }

                if(recurse)
                    return true;
            }

            if(needsFetch(this, terrain.srid)) {
                if(this.level <= 1) {
                    this.sourceVersion = ElMgrTerrainRenderService.this.sourceVersion;
                    this.tile = fetch(OSMUtils.mapnikTileResolution(this.level)*10d, this.bounds, terrain.srid, numPosts, numPosts, false);
                } else {
                    enqueue(this);
                    if(this.tile == null)
                        return false;
                }
            }
            if(this.tile == null && this.queued)
                return false;
            if(this.tile == null || this.tile.value.model == null)
                throw new IllegalStateException("tile or tile.model is null");
            this.shareTileReference(terrain.tiles);
            return true;
        }

        private int computeTargetLevel(MapSceneModel view) {
            final GeoPoint closest = GeoPoint.createMutable();
            final double gsd = GLMapView.estimateResolution(view, bounds.maxY, bounds.minX, bounds.minY, bounds.maxX, closest) * ((view.camera.elevation > -90d) ? resadj : resadj2);
             /*
            if(view.camera.elevation > -90d) {
                // get eye pos as LLA
                final PointD eyeProj;
                if (!view.camera.perspective) {
                    eyeProj = adjustCamLocation(view);
                } else {
                    eyeProj = view.camera.location;
                }
                GeoPoint eye = view.mapProjection.inverse(eyeProj, null);
                eye = new GeoPoint(eye.getLatitude(), GeoCalculations.wrapLongitude(eye.getLongitude()));

                final boolean isSame = (eye.getLatitude() == closest.getLatitude() && eye.getLongitude() == closest.getLongitude());

                final double az = isSame ? view.camera.azimuth : GeoCalculations.bearingTo(eye, closest);

                double brg = view.camera.azimuth - az;
                if (brg > 180)
                    brg -= 360;
                else if (brg < -180)
                    brg += 360;

                // tile is behind camera, return 0 to emit tile at current level
                if (Math.abs(brg) > 91d)
                    return 0;
            }
             */
            double level = MathUtils.clamp(OSMUtils.mapnikTileLeveld(gsd, 0d), 0, 16);
            if(sticky) {
                // make the target level a little sticky
                if (Math.abs(level - lastRequestLevel) > 0.5d) {
                    lastRequestLevel = (int) level;
                }
            } else {
                lastRequestLevel = (int)Math.round(level);
            }
            // XXX - adaptive based on distance from camera
            return lastRequestLevel;
        }
        /*
        private int computeTargetLevelOrig(MapSceneModel view) {
            final double gsd;
            if(view.camera.elevation > -90d) {
                // get eye pos as LLA
                final PointD eyeProj;
                if (!view.camera.perspective) {
                    eyeProj = adjustCamLocation(view);
                } else {
                    eyeProj = view.camera.location;
                }
                GeoPoint eye = view.mapProjection.inverse(eyeProj, null);
                eye = new GeoPoint(eye.getLatitude(), GeoCalculations.wrapLongitude(eye.getLongitude()));

                // XXX - find closest LLA on tile
                final double closestLat = MathUtils.clamp(eye.getLatitude(), bounds.minY, bounds.maxY);
                final double closestLng = MathUtils.clamp(eye.getLongitude(), bounds.minX, bounds.maxX);

                final GeoPoint closest = new GeoPoint(closestLat, closestLng);
                final boolean isSame = (eye.getLatitude() == closestLat && eye.getLongitude() == closestLng);

                final double slant = GeoCalculations.slantDistanceTo(eye, closest);
                final double az = isSame ? view.camera.azimuth : GeoCalculations.bearingTo(eye, closest);

                double brg = view.camera.azimuth - az;
                if (brg > 180)
                    brg -= 360;
                else if (brg < -180)
                    brg += 360;

                // tile is behind camera, return 0 to emit tile at current level
                if (Math.abs(brg) > 91d)
                    return 0;

                //double _range = (m_MetersPerPixel*(_viewHeight / 2.0) / std::tan((HVFOV)*M_PI / 180.0));
                gsd = slant * Math.tan(Math.toRadians(view.camera.fov / 2d)) / (view.height / 2d) *resadj;
            } else {
                gsd = view.gsd*resadj2;
            }
            double level = MathUtils.clamp(OSMUtils.mapnikTileLeveld(gsd, 0d), 0, 16);
            if(sticky) {
                // make the target level a little sticky
                if (Math.abs(level - lastRequestLevel) > 0.5d) {
                    lastRequestLevel = (int) level;
                }
            } else {
                lastRequestLevel = (int)Math.round(level);
            }
            // XXX - adaptive based on distance from camera
            return lastRequestLevel;
        }
*/
        private PointD adjustCamLocation(MapSceneModel view) {
            final double camlocx = view.camera.location.x*view.displayModel.projectionXToNominalMeters;
            final double camlocy = view.camera.location.y*view.displayModel.projectionYToNominalMeters;
            final double camlocz = view.camera.location.z*view.displayModel.projectionZToNominalMeters;
            final double camtgtx = view.camera.target.x*view.displayModel.projectionXToNominalMeters;
            final double camtgty = view.camera.target.y*view.displayModel.projectionYToNominalMeters;
            final double camtgtz = view.camera.target.z*view.displayModel.projectionZToNominalMeters;

            final double len = MathUtils.distance(camlocx, camlocy, camlocz, camtgtx, camtgty, camtgtz);

            final double dirx = (camlocx - camtgtx)/len;
            final double diry = (camlocy - camtgty)/len;
            final double dirz = (camlocz - camtgtz)/len;
            return new PointD((camtgtx + (dirx*len*camLocAdj)) / view.displayModel.projectionXToNominalMeters,
                    (camtgty + (diry*len*camLocAdj)) / view.displayModel.projectionYToNominalMeters,
                    (camtgtz + (dirz*len*camLocAdj)) / view.displayModel.projectionZToNominalMeters);
        }


        void reset(boolean data) {
            if(ul != null)
                ul.reset(true);
            if(ur != null)
                ur.reset(true);
            if(lr != null)
                lr.reset(true);
            if(ll != null)
                ll.reset(true);
            ul = null;
            ur = null;
            lr = null;
            ll = null;

            if(data && this.tile != null) {
                this.tile.dereference();
                this.tile = null;
            }
        }
    }

    private static void updateParentZBounds(QuadNode node) {
        if(node.parent != null) {
            boolean updated = (node.bounds.minZ < node.parent.bounds.minZ) || (node.bounds.maxZ > node.parent.bounds.maxZ);

            if(node.bounds.minZ < node.parent.bounds.minZ)
                node.parent.bounds.minZ = node.bounds.minZ;
            if(node.bounds.maxZ > node.parent.bounds.maxZ)
                node.parent.bounds.maxZ = node.bounds.maxZ;

            if(updated)
                updateParentZBounds(node.parent);
        }
    }
    private class RequestHandlerWorker implements Runnable {
        @Override
        public void run() {
            // signals that the front and back buffers should be flipped
            boolean flip = false;
            // NOTE: `reset` is external here to allow for forcing tree rebuild
            // by toggling value in debugger
            boolean reset = false;
            MapSceneModel[] fetchScene = new MapSceneModel[2];
            WorldTerrain fetchBuffer = new WorldTerrain();
            while(true) {
                int numFetchScenes = 0;
                synchronized(ElMgrTerrainRenderService.this) {
                    if (ElMgrTerrainRenderService.this.dispose)
                        break;

                    final int sceneSrid = (scene[0] != null) ? scene[0].mapProjection.getSpatialReferenceID() : worldTerrain.srid;
                    // if we're marked for flip and the SRID is the same, swap the front and back
                    // buffers
                    if(flip && fetchBuffer.srid == sceneSrid) {
                        // flip the front and back buffers, references are transferred
                        WorldTerrain swap = ElMgrTerrainRenderService.this.worldTerrain;
                        ElMgrTerrainRenderService.this.worldTerrain = fetchBuffer;
                        fetchBuffer = swap;

                        // flip was done
                        flip = false;

                        // dereference the contents of the previous front
                        // buffer (now fetch buffer) in preparation for clear
                        for(TerrainTileRef ref : fetchBuffer.tiles)
                            ref.dereference();
                        // clear back buffer for the next fetch
                        fetchBuffer.tiles.clear();

                        // request refresh
                        renderer.requestRefresh();
                    }

                    // if scene is unchanged and no new terrain, wait
                    if(sceneVersion == worldTerrain.sceneVersion && terrainVersion == worldTerrain.terrainVersion) {
                        try {
                            ElMgrTerrainRenderService.this.wait();
                        } catch(InterruptedException ignored) {}

                        flip = false;
                        continue;
                    }

                    for(int i = 0; i < ElMgrTerrainRenderService.this.numScenes; i++)
                        fetchScene[numFetchScenes++] = ElMgrTerrainRenderService.this.scene[i];

                    final boolean invalid = east.srid != sceneSrid;
                    reset |= invalid;

                    // synchronize quadtree SRID with current scene
                    if(reset) {
                        // SRID is changed, update the roots
                        if(invalid) {
                            east.srid = sceneSrid;
                            west.srid = sceneSrid;
                        }

                        for(int i = 0; i < roots.length; i++) {
                            roots[i].reset(invalid);
                            roots[i] = new QuadNode(roots[i].parent, roots[i].bounds.minX, roots[i].bounds.minY, roots[i].bounds.maxX, roots[i].bounds.maxY);
                        }

                        queue.clear();
                        reset = false;
                    }

                    flip = true;
                    fetchBuffer.srid = sceneSrid;
                    fetchBuffer.sourceVersion = ElMgrTerrainRenderService.this.sourceVersion;
                    fetchBuffer.terrainVersion = ElMgrTerrainRenderService.this.terrainVersion;
                    fetchBuffer.sceneVersion = ElMgrTerrainRenderService.this.sceneVersion;
                }

                for(int i = 0; i < roots.length; i++) {
                    for(int j = 0; j < numFetchScenes; j++) {
                        if(MapSceneModel.intersects(fetchScene[j], roots[i].bounds.minX, roots[i].bounds.minY, roots[i].bounds.minZ, roots[i].bounds.maxX, roots[i].bounds.maxY, roots[i].bounds.maxZ)) {
                            roots[i].collect(fetchScene[j], fetchBuffer);
                        } else {
                            // no intersection
                            if(roots[i].tile == null) {
                                // there's no data, grab an empty tile
                                roots[i].tile = fetch(OSMUtils.mapnikTileResolution(roots[i].level) * 10d, roots[i].bounds, fetchBuffer.srid, numPosts, numPosts, false);
                                roots[i].sourceVersion = fetchBuffer.sourceVersion;
                            }

                            roots[i].reset(false);

                            // add a new reference to the tile to "back"
                            roots[i].shareTileReference(fetchBuffer.tiles);
                        }
                    }
                }
            }
        }
    }
    private class TileFetchWorker implements Runnable {
        @Override
        public void run() {
            QuadNode node = null;
            TerrainTileRef tile = null;
            int fetchedNodes = 0;
            int fetchSrcVersion = ~sourceVersion;
            while(true) {
                synchronized(ElMgrTerrainRenderService.this) {
                    if (ElMgrTerrainRenderService.this.dispose) {
                        if(tile != null)
                            tile.dereference();
                        break;
                    }

                    if(node != null) {
                        node.queued = false;
                        node.sourceVersion = fetchSrcVersion;
                        if(node.tile != null)
                            node.tile.dereference();

                        // reference is transferred to node
                        node.tile = tile;

                        //node.tile.info.minDisplayResolution = node.level;
                        if(node.level > TERRAIN_LEVEL) {
                            node.bounds.minZ = node.tile.value.aabb.minZ;
                            node.bounds.maxZ = node.tile.value.aabb.maxZ;
                            updateParentZBounds(node);
                        }
                        node = null;
                        tile = null;

                        terrainVersion++;
                        ElMgrTerrainRenderService.this.notifyAll();

                        renderer.requestRefresh();
                    }

                    if(queue.isEmpty()) {
                        try {
                            ElMgrTerrainRenderService.this.wait();
                        } catch(InterruptedException ignored) {}

                        continue;
                    }

                    node = queue.remove(queue.size()-1);

                    Envelope testBounds = node.parent != null ? node.parent.bounds : node.bounds;
                    boolean isect = false;
                    for(int i = 0; i < numScenes; i++)
                        isect |= MapSceneModel.intersects(scene[i], testBounds.minX, testBounds.minY, testBounds.minZ, testBounds.maxX, testBounds.maxY, testBounds.maxZ);
                    if(!isect) {
                        node.queued = false;
                        node = null;
                        continue;
                    }

                    fetchSrcVersion = sourceVersion;
                }

                tile = fetch(OSMUtils.mapnikTileResolution(node.level)*2.5d, node.bounds, node.srid, numPosts, numPosts, node.level > TERRAIN_LEVEL);
                fetchedNodes++;

                //Log.i("ElMgrTerrainRenderService", "fetched node, count=" + fetchedNodes);
            }
        }
    }

    final static class WorldTerrain {
        int srid = -1;
        final Collection<TerrainTileRef> tiles = new HashSet<>();
        int sourceVersion = -1;
        int sceneVersion = -1;
        int terrainVersion = -1;
    }

    final static class TerrainTileRef extends ReferenceCount<GLMapView.TerrainTile> {
        TerrainTileRef(GLMapView.TerrainTile value) {
            super(value, true);
            value.opaque = this;
        }

        @Override
        protected void onDereferenced() {
            value.model.dispose();
            super.onDereferenced();
        }
    }
}
