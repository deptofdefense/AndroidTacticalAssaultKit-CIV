package com.atakmap.map.opengl;


import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.coremap.maps.coords.GeoCalculations;
import com.atakmap.lang.Unsafe;
import com.atakmap.map.elevation.quadtree.QuadTreeElevationTerrain;
import com.atakmap.map.layer.feature.geometry.Envelope;
import com.atakmap.map.layer.model.MeshBuilder;
import com.atakmap.map.layer.model.Model;
import com.atakmap.map.layer.model.ModelBuilder;
import com.atakmap.map.layer.model.Models;
import com.atakmap.map.layer.raster.osm.OSMUtils;
import com.atakmap.math.MathUtils;
import com.atakmap.math.Matrix;
import com.atakmap.opengl.GLTexture;
import com.atakmap.util.ConfigOptions;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Collection;

public class LegacyElMgrTerrainRenderService extends TerrainRenderService {
    private final static int MAX_GRID_NODE_LEVEL = 18;

    final GLTerrain terrain;

    private int gridSubdivisions = -1;
    private double drawGridCellWidth;
    private double drawGridCellHeight;
    private double drawGridOffsetLat;
    private double drawGridOffsetLng;
    private int drawGridNumCellsX;
    private int drawGridNumCellsY;

    int lastTerrainVersion = 0;
    FloatBuffer vertexCoords;
    long vertexCoordsPtr;
    ShortBuffer indices;
    int indicesGridCellsX;
    int indicesGridCellsY;
    int indicesCount;

    GLMapView.TerrainTile data = new GLMapView.TerrainTile();
    GLOffscreenVertex[] vertices;
    int verticesCount;

    int terrainEnabled = -1;

    public LegacyElMgrTerrainRenderService() {
        this.terrain = new QuadTreeElevationTerrain( MAX_GRID_NODE_LEVEL );
    }

    @Override
    public void dispose() {
    }

    // legacy implementation
    @Override
    public int getTerrainVersion() {
        return terrain.getTerrainVersion();
    }

    @Override
    public int lock(GLMapView view, Collection<GLMapView.TerrainTile> tiles) {
        this.terrain.update(view);

        // dynamically generate the texture mesh based on the zoom level
        final int level = MathUtils.clamp(OSMUtils.mapnikTileLevel(view.drawMapResolution), 0, MAX_GRID_NODE_LEVEL);

        if (this.gridSubdivisions == -1)
            this.gridSubdivisions = ConfigOptions.getOption("glmapview.offscreen.grid-subdivisions", 3);


        double oldWidth = drawGridCellWidth;
        double oldOffsetLat = drawGridOffsetLat;
        double oldOffsetLng = drawGridOffsetLng;

        this.drawGridCellWidth = (360d / (double) (1L << (long) level)) / (double) (this.gridSubdivisions + 1);
        this.drawGridCellHeight = this.drawGridCellWidth;

        this.drawGridOffsetLng = ((int) (view.idlHelper.wrapLongitude(view.westBound) / this.drawGridCellWidth) - 1) * this.drawGridCellWidth;
        final double gridMaxLng = (Math.ceil(view.idlHelper.wrapLongitude(view.eastBound) / this.drawGridCellWidth) + 1) * this.drawGridCellWidth;
        final double gridMinLat = ((int) (view.southBound / this.drawGridCellHeight) - 1) * this.drawGridCellHeight;
        this.drawGridOffsetLat = (Math.ceil(view.northBound / this.drawGridCellHeight) + 1) * this.drawGridCellHeight;

        boolean completeOffsetRefresh = (Double.compare(oldWidth,drawGridCellWidth) != 0 || 
                                               Double.compare(oldOffsetLat,drawGridOffsetLat) != 0 || 
                                               Double.compare(oldOffsetLng,drawGridOffsetLng) != 0);

        int oldCellX = drawGridNumCellsX;
        int oldCellY = drawGridNumCellsY;
        this.drawGridNumCellsX = (int) Math.ceil((gridMaxLng - this.drawGridOffsetLng) / this.drawGridCellWidth);
        this.drawGridNumCellsY = (int) Math.ceil((this.drawGridOffsetLat - gridMinLat) / this.drawGridCellHeight);

        boolean updateVertexLocations = completeOffsetRefresh || (oldCellX != drawGridNumCellsX) || (oldCellY != drawGridNumCellsY);

        int idx;

        this.indicesCount = GLTexture.getNumQuadMeshIndices(this.drawGridNumCellsX, this.drawGridNumCellsY);
        this.verticesCount = GLTexture.getNumQuadMeshVertices(this.drawGridNumCellsX, this.drawGridNumCellsY);

        final int numVerts = this.verticesCount;

        // rebuild the indices as necessary
        if (this.indicesGridCellsX != this.drawGridNumCellsX || this.indicesGridCellsY != this.drawGridNumCellsY) {
            if (this.indices == null
                    || this.indices.capacity() < (this.indicesCount * 2)) {
                ByteBuffer buf = ByteBuffer.allocateDirect(this.indicesCount * 2);
                buf.order(ByteOrder.nativeOrder());

                this.indices = buf.asShortBuffer();
            }

            // fill the indices
            this.indices.clear();
            GLTexture.createQuadMeshIndexBuffer(this.drawGridNumCellsX,
                    this.drawGridNumCellsY,
                    this.indices);
            this.indices.flip();

            this.indicesGridCellsX = this.drawGridNumCellsX;
            this.indicesGridCellsY = this.drawGridNumCellsY;
        }

        if (this.vertexCoords == null
                || this.vertexCoords.capacity() < (numVerts * 3)) {

            ByteBuffer buf;

            buf = ByteBuffer.allocateDirect(numVerts * 12);
            buf.order(ByteOrder.nativeOrder());
            this.vertexCoords = buf.asFloatBuffer();
            this.vertexCoordsPtr = Unsafe.getBufferPointer(this.vertexCoords);
        }

        // iterate grid and compute lat/lon given offscreen scene model

        if (terrainEnabled == -1)
            terrainEnabled = ConfigOptions.getOption("glmapview.offscreen.terrain-enabled", 1);

        final int requiredVertices = ((this.drawGridNumCellsX + 1) * (this.drawGridNumCellsY + 1));
        if (this.vertices == null) {
            this.vertices = new GLOffscreenVertex[requiredVertices];
            completeOffsetRefresh = true;
        } else if (this.vertices.length < requiredVertices) {
            GLOffscreenVertex[] oldArray = this.vertices;
            this.vertices = new GLOffscreenVertex[requiredVertices];
            for (int i = 0; i < oldArray.length; i++) {
                this.vertices[i] = oldArray[i];
                oldArray[i].clearVersions();
            }
            completeOffsetRefresh = true;
        }
        this.verticesCount = requiredVertices;

        updateVertexLocations = updateVertexLocations || completeOffsetRefresh;
        if (updateVertexLocations)
            updateOffscreenVertexLocations(requiredVertices);

        boolean updateAltitudes = (terrainEnabled != 0) && (updateVertexLocations || (
                lastTerrainVersion != terrain.getTerrainVersion()));

        if (updateAltitudes) {
            lastTerrainVersion =
                    terrain.updateAltitude(vertices, requiredVertices);
        }

        // XXX - using frame center as current elevation adjustment, but
        //       should continue to experiment

        // compute elevation adjustment based on parameters of scene
        this.elevationStats.reset();
        GLOffscreenVertex.computeElevationStatistics(
                this.vertices,
                0,
                requiredVertices,
                this.elevationStats);

        // construct model if necessary
        if (updateVertexLocations ||
                updateAltitudes ||
                this.data.model == null ||
                this.data.info.srid != view.drawSrid) {

            // establish the origin of the local frame for the model
            view.scratch.geo.set(GeoPoint.UNKNOWN);
            view.scratch.geo.set(view.drawLat, view.drawLng);
            view.scene.mapProjection.forward(view.scratch.geo, view.scratch.pointD);

            final double localFrameOriginX = view.scratch.pointD.x;
            final double localFrameOriginY = view.scratch.pointD.y;
            final double localFrameOriginZ = view.scratch.pointD.z;

            this.data.info.localFrame = Matrix.getTranslateInstance(view.scratch.pointD.x, view.scratch.pointD.y, view.scratch.pointD.z);
            this.data.info.srid = view.drawSrid;


            MeshBuilder builder = new MeshBuilder(
                    Model.VERTEX_ATTR_POSITION,
                    true,
                    Model.DrawMode.TriangleStrip);
            builder.setWindingOrder(Model.WindingOrder.CounterClockwise);

            for (int i = 0; i < requiredVertices; i++) {
                view.scene.mapProjection.forward(this.vertices[i].geo, view.scratch.pointD);

                builder.addVertex(view.scratch.pointD.x - localFrameOriginX, view.scratch.pointD.y - localFrameOriginY, view.scratch.pointD.z - localFrameOriginZ,
                        0f, 0f,
                        0, 0, 0,
                        1f, 1f, 1f, 1f);
            }

            // set the faces
            {
                final int pos = this.indices.position();
                builder.addIndices(this.indices);
                this.indices.position(pos);
            }

            if (this.data.model != null)
                this.data.model.dispose();
            this.data.model = ModelBuilder.build(builder.build());
            if(this.data.aabb == null)
                this.data.aabb = new Envelope(Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
            Models.transform(this.data.model.getAABB(), this.data.info, this.data.aabb, WGS84);
        }

        tiles.add(this.data);

        return this.lastTerrainVersion;
    }

    @Override
    public void unlock(Collection<GLMapView.TerrainTile> tiles) {
        for(GLMapView.TerrainTile tile : tiles) {
            if(tile.model == this.data.model)
                continue;
            tile.model.dispose();
        }
    }

    private void updateOffscreenVertexLocations(int requiredVertices) {
        int idx = 0;
        GLOffscreenVertex vertex;
        for( int i = 0; i <= this.drawGridNumCellsY; i++ )
        {
            for( int j = 0; j <= this.drawGridNumCellsX; j++ )
            {
                // compute the grid coordinate
                vertex = this.vertices[idx];
                if( vertex == null )
                {
                    vertex = new GLOffscreenVertex( );
                    this.vertices[idx] = vertex;
                }

                double gridLat = this.drawGridOffsetLat - ( i * this.drawGridCellHeight );
                double gridLng = this.drawGridOffsetLng + ( j * this.drawGridCellWidth );

                if( gridLat < -90d )
                {
                    gridLat = -90d;
                }
                else if( gridLat > 90d )
                {
                    gridLat = 90d;
                }

                gridLng = GeoCalculations.wrapLongitude(gridLng);

                if( Double.compare(vertex.geo.getLatitude( ), gridLat) != 0
                        || Double.compare(vertex.geo.getLongitude( ), gridLng) != 0 )
                {
                    vertex.setLocation( gridLat, gridLng );
                }
                idx++;
            }
        }
    }

    @Override
    public double getElevation(GeoPoint geo) {
        return this.terrain.getElevation(geo, null);
    }
}
