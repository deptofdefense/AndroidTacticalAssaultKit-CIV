/*
 * TileReader.java
 *
 * Created on April 24, 2013, 12:05 PM
 */

package com.atakmap.map.layer.raster.tilereader;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.database.*;

import java.io.*;

/**
 * @author Developer
 */
public class TileCache {

    /**************************************************************************/

    private final static int DATABASE_VERSION = 3;

    private final static int DATA_MASK_UL = 0x01;
    private final static int DATA_MASK_UR = 0x02;
    private final static int DATA_MASK_LR = 0x04;
    private final static int DATA_MASK_LL = 0x08;

    private final static ReadCallback NOOP_CALLBACK = new ReadCallback() {
        @Override
        public final boolean canceled() {
            return false;
        };

        @Override
        public final void update(int dstX, int dstY, int dstW, int dstH, byte[] data, int off,
                                 int len) {
        }
    };

    /**************************************************************************/

    private TileReader reader;
    private DatabaseIface tileDatabase;

    private boolean configured;
    
    private TileCacheData.Compositor compositor;
    private TileCacheData.Allocator allocator;
    private TileCacheData.Serializer serializer;

    private int halfTileWidth;
    private int halfTileHeight;

    private final String tableName;

    /** Creates a new instance of TileReader */
    public TileCache(String path, TileReader reader) {
        this.tileDatabase = IOProviderFactory.createDatabase(new File(path));
        ensureTileCache(this.tileDatabase);

        this.tableName = "tilecache";
        this.reader = reader;
    }

    public void close() {
        this.tileDatabase.close();
    }

    /**
     * Configures the tile cache to utilize the specified source, allocator, compositor and
     * serializer. The {@link com.atakmap.android.maps.tilesets.cache.TileCacheData} support
     * objects should be compatible with themselves as well as the reader. This means that the
     * compositor should be able to composite data produced by the allocator and serializer, that
     * the allocator should be able to deallocate data produced by the serializer and that data
     * produced by the allocator should return pixel data in a format that is identical to the
     * format returned via the <code>read</code> methods of the specified
     * {@link com.atakmap.android.maps.tilesets.TileReader}. If this general compatibility
     * constraint is not observed results will be undefined.
     * 
     * @param reader
     * @param allocator
     * @param compositor
     * @param serializer
     */
    private void configure() {
        if (this.configured)
            throw new IllegalStateException("Already configured.");

        this.allocator = this.reader.getTileCacheDataAllocator();
        this.compositor = this.reader.getTileCacheDataCompositor();
        this.serializer = this.reader.getTileCacheDataSerializer();
        
        if(this.allocator == null || this.compositor == null || this.serializer == null)
            throw new UnsupportedOperationException("Caching not supported for " + reader);

        this.halfTileWidth = this.reader.getTileWidth() / 2;
        this.halfTileHeight = this.reader.getTileHeight() / 2;

        this.configured = true;
    }

    /**
     * Retrieves the specified tile at the specified level from the tile cache. If the tile is not
     * currently cached, it will be derived from existing tiles or read directly from the underlying
     * source.
     * <P>
     * One of <code>tile</code> or <code>callback</code> should be specified. When <code>tile</code>
     * is non-<code>null</code> it will be populated with the appropriate data for the tile before
     * the method returns. When <code>callback</code> is non-<code>null</code>, periodic updates
     * will be delivered during execution of the method until all data for the entire tile has been
     * delivered. The methods of the callback will be invoked on the same thread as
     * <code>getTile</code> was invoked on and no reference will be held to <code>callback</code> on
     * any other thread or after the method has returned.
     * <P>
     * This method is <B>NOT</B> thread safe and should only be invoked on the thread used to read
     * data directly from the source.
     * 
     * @param tile Returns the data for the tile to be read; may be <code>null</code>.
     * @param tileRow The row number of the tile to be read
     * @param tileColumn The column number of the tile to be read
     * @param rset The resolution level of the tile to be read
     * @param callback Provides a callback interface for periodic updates as the data for the tile
     *            is read and also allows for an asynchronous abort of the read.
     * @return <code>true</code> if the tile was successfully read, <code>false</code> otherwise.
     *         The call will only be unsuccessful if the callback signals cancellation.
     */
    public boolean getTile(TileCacheData tile, long tileRow, long tileColumn, int rset,
            ReadCallback callback) {
        if (!this.configured)
            this.configure();

        //System.out.println("getTile: rset=" + rset + ",tileColumn=" + tileColumn + ",tileRow="
        //        + tileRow);
        if (callback == null)
            callback = NOOP_CALLBACK;
        final boolean retval = getTileImpl(tile, tileRow, tileColumn, rset, callback);
        //System.out.println("\t ---> done, canceled=" + callback.canceled());

        return retval;
    }

    /**
     * @param request
     * @param tile
     * @param tileRow
     * @param tileColumn
     * @param rset
     * @param recurse <code>true</code> if missing tile parts should be
     * @return
     */
    private boolean getTileImpl(TileCacheData tile, long tileRow, long tileColumn, int rset,
            ReadCallback callback) {
        if (callback.canceled())
            return false;

        TileCacheData[] parts = this.getTileParts(tileRow, tileColumn, rset, callback);

        final int tileWidth = this.reader.getTileWidth(rset, tileColumn);
        final int tileHeight = this.reader.getTileHeight(rset, tileRow);

        final boolean needRight = (tileWidth > this.halfTileWidth);
        final boolean needLower = (tileHeight > this.halfTileHeight);

        // check if we need any tile parts
        final boolean needParts = ((parts[0] == null) || (needRight
                && (parts[1] == null || (needLower && parts[2] == null)) || (needLower && parts[3] == null)));

        // the request was canceled and we still need parts, return null
        if (callback.canceled() && needParts) {
            for (int i = 0; i < parts.length; i++)
                if (parts[i] != null)
                    this.allocator.deallocate(parts[i]);
            return false;
        }

        final int tileLeftWidth = Math.min(halfTileWidth, tileWidth);
        final int tileRightWidth = Math.min(halfTileWidth, tileWidth - halfTileWidth);
        final int tileUpperHeight = Math.min(halfTileHeight, tileHeight);
        final int tileLowerHeight = Math.min(halfTileHeight, tileHeight - halfTileHeight);

        // obtain any missing tile parts. request the tileparts from the
        // previous RSET level as that will build the tileparts for the tile at
        // this level and possibly the previous level as well without incurring
        // too much additional overhead (same amount of data is being read from
        // disk, additional overhead comes from an additional subsampling of the
        // data, but second subsampling should be relatively low cost).
        if (needParts) {
            final int tilePartRset = rset - 1;
            TileCacheData scratchTile = null;
            // if all parts are going to be the same size, pre-allocate an
            // intermediate buffer
            if (tileLeftWidth == tileRightWidth
                    && tileLeftWidth == this.reader.getTileWidth(tilePartRset, tileColumn * 2) &&
                    tileUpperHeight == tileLowerHeight
                    && tileUpperHeight == this.reader.getTileHeight(tilePartRset, tileRow * 2)) {

                scratchTile = this.allocator.allocate(
                        this.reader.getTileWidth(tilePartRset, tileColumn * 2),
                        this.reader.getTileHeight(tilePartRset, tileRow * 2));
            }

            int need = 0;
            if (parts[0] == null)
                need |= DATA_MASK_UL;
            if (parts[1] == null && needRight)
                need |= DATA_MASK_UR;
            if (parts[2] == null && needLower)
                need |= DATA_MASK_LL;
            if (parts[3] == null && needRight && needLower)
                need |= DATA_MASK_LR;

            //System.out.println("need mask=" + need);
            TileSpec[] tileSpecs = new TileSpec[] {
                    new TileSpec(DATA_MASK_UL, null, tileLeftWidth, tileUpperHeight), // UL
                    new TileSpec(DATA_MASK_UR, null, tileRightWidth, tileUpperHeight), // UR
                    new TileSpec(DATA_MASK_LL, null, tileLeftWidth, tileLowerHeight), // LL
                    new TileSpec(DATA_MASK_LR, null, tileRightWidth, tileLowerHeight), // LR
            };

            int readOrDerived = 0;
            long tilePartRow;
            long tilePartColumn;
            for (int i = 0; i < 4; i++) {
                if ((need & tileSpecs[i].mask) == tileSpecs[i].mask) {
                    tilePartRow = (tileRow * 2) + (i / 2);
                    tilePartColumn = (tileColumn * 2) + (i % 2);

                    // try to obtain the missing part from the database
                    parts[i] = this.getTilePart(null, tilePartRow, tilePartColumn, tilePartRset,
                            callback);
                    if (parts[i] == null && callback.canceled())
                        break;
                    // read the tile part
                    if (parts[i] == null)
                        parts[i] = this.readAsTilePart(tilePartRow, tilePartColumn, tilePartRset);
                    if (parts[i] == null) // cancelled
                        break;

                    readOrDerived |= tileSpecs[i].mask;
                    if (callback.canceled())
                        break;

                    // post a data update
                    callback.update((i % 2) * tileLeftWidth, (i / 2) * tileUpperHeight,
                            parts[i].getWidth(), parts[i].getHeight(), parts[i].getPixelData(),
                            parts[i].getPixelDataOffset(), parts[i].getPixelDataLength());
                    // clear the need mask
                    need &= ~tileSpecs[i].mask;
                }
            }
            if (scratchTile != null)
                this.allocator.deallocate(scratchTile);

            if (readOrDerived != 0)
                this.cacheTileParts(tileRow, tileColumn, rset, readOrDerived, parts);

            if (callback.canceled()) {
                for (int i = 0; i < parts.length; i++)
                    if (parts[i] != null)
                        this.allocator.deallocate(parts[i]);
                return false;
            }
        }

        final boolean tileComplete = ((parts[0] != null) && (!needRight || (parts[1] != null && (!needLower || parts[3] != null))
                && (!needLower || parts[2] != null)));

        // we want to cache the tile if we filled any tile parts that weren't
        // already in the cache
        final boolean cacheTile = needParts && tileComplete;

        // if the request has been canceled and we don't have all of the parts
        // necessary for caching, return now, otherwise, perform the caching
        // even if canceled to avoid subsequent reads for the data
        if (!cacheTile && callback.canceled()) {
            for (int i = 0; i < parts.length; i++)
                if (parts[i] != null)
                    this.allocator.deallocate(parts[i]);
            return false;
        }

        // all data was successfully obtained; the user specified a null tile so
        // there is no need to assemble
        if (!cacheTile && tile == null) {
            for (int i = 0; i < parts.length; i++)
                if (parts[i] != null)
                    this.allocator.deallocate(parts[i]);
            return true;
        }

        // 'tile' may be null here if we are caching
        boolean deallocateTile = (tile == null);
        if (tile == null)
            tile = this.allocator.allocate(tileWidth, tileHeight);

        final boolean assembled = assembleTileFromParts(tile, halfTileWidth, halfTileHeight, parts,
                this.compositor, cacheTile ? null : callback);
        if (!assembled) {
            if (deallocateTile)
                this.allocator.deallocate(tile);
            for (int i = 0; i < parts.length; i++)
                if (parts[i] != null)
                    this.allocator.deallocate(parts[i]);
            return false;
        }

        if (cacheTile) {
            int quarterTileWidth = (tileWidth + 1) >> 1;
            int quarterTileHeight = (tileHeight + 1) >> 1;

            TileCacheData quarterTile = this.allocator
                    .allocate(quarterTileWidth, quarterTileHeight);
            this.compositor.composite(quarterTile, tile, 0, 0, quarterTile.getWidth(),
                    quarterTile.getHeight());

            // cache this tile as a tile part for the next RSET; this tile part may
            // have already been cached by 'readAndCacheTile'
            int dataMask;
            if (tileRow % 2 == 0 && tileColumn % 2 == 0)
                dataMask = DATA_MASK_UL;
            else if (tileRow % 2 == 0)
                dataMask = DATA_MASK_UR;
            else if (tileColumn % 2 == 0)
                dataMask = DATA_MASK_LL;
            else
                dataMask = DATA_MASK_LR;
            this.cacheTilePart(tileRow / 2, tileColumn / 2, rset + 1, dataMask,
                    this.serializer.serialize(quarterTile));

            this.allocator.deallocate(quarterTile);

        }
        if (deallocateTile)
            this.allocator.deallocate(tile);
        for (int i = 0; i < parts.length; i++)
            if (parts[i] != null)
                this.allocator.deallocate(parts[i]);

        return true;
    }

    /**
     * @return The four tile parts for the specified tile in the order: upper-left, upper-right,
     *         lower-left, lower-right. The parts are newly allocated and should be deallocated when
     *         no longer needed.
     */
    private TileCacheData[] getTileParts(long tileRow, long tileColumn, int rset,
            ReadCallback callback) {
        //long s = System.currentTimeMillis();
        // query the database for the tileparts for the requested tile
        QueryIface rs = null;
        try {
            TileCacheData[] retval = new TileCacheData[4];

            rs = this.tileDatabase.compileQuery("SELECT * FROM " + this.tableName + " WHERE tilerow = ? AND tilecolumn = ? AND rset = ? LIMIT 1");
            rs.bind(1, tileRow);
            rs.bind(2, tileColumn);
            rs.bind(3, rset);

            final boolean gotResult = rs.moveToNext();
            if (!gotResult)
                return retval;

            final int tileWidth = rs.getInt(rs.getColumnIndex("tilewidth"));
            final int tileHeight = rs.getInt(rs.getColumnIndex("tileheight"));

            final int tileLeftWidth = Math.min(halfTileWidth, tileWidth);
            final int tileRightWidth = Math.min(halfTileWidth, tileWidth - halfTileWidth);
            final int tileUpperHeight = Math.min(halfTileHeight, tileHeight);
            final int tileLowerHeight = Math.min(halfTileHeight, tileHeight - halfTileHeight);

            TileSpec[] tileSpecs = new TileSpec[] {
                    new TileSpec(DATA_MASK_UL, "dataul", tileLeftWidth, tileUpperHeight), // UL
                    new TileSpec(DATA_MASK_UR, "dataur", tileRightWidth, tileUpperHeight), // UR
                    new TileSpec(DATA_MASK_LL, "datall", tileLeftWidth, tileLowerHeight), // LL
                    new TileSpec(DATA_MASK_LR, "datalr", tileRightWidth, tileLowerHeight), // LR
            };

            final int dataMask = rs.getInt(rs.getColumnIndex("datamask"));
            for (int i = 0; i < 4; i++) {
                if ((dataMask & tileSpecs[i].mask) == tileSpecs[i].mask) {
                    retval[i] = this.serializer.deserialize(
                            rs.getBlob(rs.getColumnIndex(tileSpecs[i].field)), tileSpecs[i].width,
                            tileSpecs[i].height);
                    if (callback != null)
                        callback.update((i % 2) * tileLeftWidth, (i / 2) * tileUpperHeight,
                                retval[i].getWidth(), retval[i].getHeight(),
                                retval[i].getPixelData(), retval[i].getPixelDataOffset(),
                                retval[i].getPixelDataLength());
                }
            }

            return retval;
        } finally {
            //if (callback != null)
            //    System.out.println("tilepart query in: " + (System.currentTimeMillis() - s));
            if (rs != null)
                rs.close();
        }
    }

    /**
     * Obtains the specified tile from the database as a tile part for assembling a tile in the next
     * RSET level. The returned tile will have quarter tile dimensions.
     * 
     * @param request
     * @param full Returns the full tile (may be <code>null</code>)
     * @param tileRow
     * @param tileColumn
     * @param rset
     * @return The tile part. The returned instance is newly allocated via the {@link #allocator}
     *         and should be deallocated when no longer needed.
     */
    private TileCacheData getTilePart(TileCacheData full, long tileRow, long tileColumn, int rset,
            ReadCallback callback) {
        if (callback.canceled())
            return null;

        TileCacheData[] parts = this.getTileParts(tileRow, tileColumn, rset, null);
        if (callback.canceled()) {
            for (int i = 0; i < parts.length; i++)
                if (parts[i] != null)
                    this.allocator.deallocate(parts[i]);
            return null;
        }

        final int tileWidth = this.reader.getTileWidth(rset, tileColumn);
        final int tileHeight = this.reader.getTileHeight(rset, tileRow);

        final boolean needRight = (tileWidth > this.halfTileWidth);
        final boolean needLower = (tileHeight > this.halfTileHeight);

        // if any parts are missing, return null and read the tile from the
        // source
        if ((parts[0] == null)
                || (needRight && (parts[1] == null || (needLower && parts[3] != null)) || (!needLower && parts[2] != null))) {
            for (int i = 0; i < parts.length; i++)
                if (parts[i] != null)
                    this.allocator.deallocate(parts[i]);
            return null;
        }

        boolean deallocateFull = (full == null);
        if (full == null)
            full = this.allocator.allocate(tileWidth, tileHeight);

        final boolean assembled = assembleTileFromParts(full, halfTileWidth, halfTileHeight, parts,
                this.compositor, callback);
        if (!assembled) {
            if (deallocateFull)
                this.allocator.deallocate(full);
            for (int i = 0; i < parts.length; i++)
                if (parts[i] != null)
                    this.allocator.deallocate(parts[i]);
            return null;
        }

        final int parentTileWidth = this.reader.getTileWidth(rset + 1, tileColumn / 2);
        final int parentTileHeight = this.reader.getTileHeight(rset + 1, tileRow / 2);

        final int quarterTileWidth;
        if (tileColumn % 2 == 0)
            quarterTileWidth = Math.min(parentTileWidth, this.halfTileWidth);
        else
            quarterTileWidth = parentTileWidth - this.halfTileWidth;

        final int quarterTileHeight;
        if (tileRow % 2 == 0)
            quarterTileHeight = Math.min(parentTileHeight, this.halfTileHeight);
        else
            quarterTileHeight = parentTileHeight - this.halfTileHeight;

        TileCacheData quarterTile = this.allocator.allocate(quarterTileWidth, quarterTileHeight);
        this.compositor.composite(quarterTile, full, 0, 0, quarterTile.getWidth(),
                quarterTile.getHeight());

        if (deallocateFull)
            this.allocator.deallocate(full);
        for (int i = 0; i < parts.length; i++)
            if (parts[i] != null)
                this.allocator.deallocate(parts[i]);

        return quarterTile;
    }

    /**
     * Reads the specified tile from the source as the corresponding quarter tile for the
     * <B>next</B> RSET level.
     * 
     * @param tileRow
     * @param tileColumn
     * @param rset
     * @return The tile part. The returned instance is newly allocated via the {@link #allocator}
     *         and should be deallocated when no longer needed. If the read is interrupted,
     *         <code>null</code> will be returned.
     */
    private TileCacheData readAsTilePart(long tileRow, long tileColumn, int rset) {
        final int tileWidth = this.reader.getTileWidth(rset + 1, tileColumn / 2);
        final int tileHeight = this.reader.getTileHeight(rset + 1, tileRow / 2);

        final int quarterTileWidth;
        if (tileColumn % 2 == 0)
            quarterTileWidth = Math.min(tileWidth, this.halfTileWidth);
        else
            quarterTileWidth = tileWidth - this.halfTileWidth;

        final int quarterTileHeight;
        if (tileRow % 2 == 0)
            quarterTileHeight = Math.min(tileHeight, this.halfTileHeight);
        else
            quarterTileHeight = tileHeight - this.halfTileHeight;

        // read the tile
        TileCacheData retval = this.allocator.allocate(quarterTileWidth, quarterTileHeight);
        //long s = System.currentTimeMillis();
        final TileReader.ReadResult success = this.reader.read(this.reader.getTileSourceX(rset, tileColumn),
                this.reader.getTileSourceY(rset, tileRow),
                this.reader.getTileSourceWidth(rset, tileColumn),
                this.reader.getTileSourceHeight(rset, tileRow),
                quarterTileWidth,
                quarterTileHeight,
                retval.getPixelData());
        //long f = System.currentTimeMillis();
        //System.out.println("\ttilepart (" + rset + "," + tileRow + "," + tileColumn + ") in "
        //        + (f - s) + "ms");

        if (success != TileReader.ReadResult.SUCCESS) {
            this.allocator.deallocate(retval);
            retval = null;
        }
        return retval;
    }

    private void cacheTilePart(long tileRow, long tileColumn, int rset, int dataMask, byte[] data) {
        //long s = System.currentTimeMillis();
        QueryIface rs = null;
        try {
            rs = this.tileDatabase.compileQuery("SELECT * FROM " + this.tableName + " WHERE tilerow = ? AND tilecolumn = ? AND rset = ? LIMIT 1");
            rs.bind(1, tileRow);
            rs.bind(2, tileColumn);
            rs.bind(3, rset);
            if (rs.moveToNext()) {
                String tilePartName;
                switch (dataMask) {
                    case DATA_MASK_UL:
                        tilePartName = "dataul";
                        break;
                    case DATA_MASK_UR:
                        tilePartName = "dataur";
                        break;
                    case DATA_MASK_LR:
                        tilePartName = "datalr";
                        break;
                    case DATA_MASK_LL:
                        tilePartName = "datall";
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
                final int oldDataMask = rs.getInt(rs.getColumnIndex("datamask"));
                if ((oldDataMask & dataMask) == dataMask) {
                    // System.err.println("attempting to cache already cached tile!!!");
                    return;
                }

                StatementIface stmt = null;
                try {
                    stmt = this.tileDatabase.compileStatement("UPDATE " + this.tableName + " SET datamask = ?, " + tilePartName + " = ? WHERE tilerow = ? AND tilecolumn = ? AND rset = ?");
                    stmt.bind(1, oldDataMask|dataMask);
                    stmt.bind(2, data);
                    stmt.bind(3, tileRow);
                    stmt.bind(4, tileColumn);
                    stmt.bind(5, rset);

                    stmt.execute();
                } finally {
                    if(stmt != null)
                        stmt.close();
                }
            } else {
                final int tileWidth = this.reader.getTileWidth(rset, tileColumn);
                final int tileHeight = this.reader.getTileHeight(rset, tileRow);

                StatementIface stmt = null;
                try {
                    stmt = this.tileDatabase.compileStatement(
                            "INSERT INTO " + this.tableName +
                                " tilerow, tilecolumn, rset, tilewidth, tileheight, datamask, dataul, dataur, datalr, datall " +
                                "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");

                    stmt.bind(1, tileRow);
                    stmt.bind(2, tileColumn);
                    stmt.bind(3, rset);
                    stmt.bind(4, tileWidth);
                    stmt.bind(5, tileHeight);
                    stmt.bind(6, dataMask);
                    stmt.bind(7, (dataMask == DATA_MASK_UL) ? data : null);
                    stmt.bind(8, (dataMask == DATA_MASK_UR) ? data : null);
                    stmt.bind(8, (dataMask == DATA_MASK_LR) ? data : null);
                    stmt.bind(10, (dataMask == DATA_MASK_LL) ? data : null);

                    stmt.execute();
                } finally {
                    if(stmt != null)
                        stmt.close();
                }
            }
        } finally {
            //System.out.println("tilepart cached in " + (System.currentTimeMillis() - s) + "ms");
            if (rs != null)
                rs.close();
        }
    }

    private void cacheTileParts(long tileRow, long tileColumn, int rset, int dataMask,
            TileCacheData[] parts) {

        TileSpec[] tileSpecs = new TileSpec[] {
                new TileSpec(DATA_MASK_UL, "dataul", 0, 0), // UL
                new TileSpec(DATA_MASK_UR, "dataur", 0, 0), // UR
                new TileSpec(DATA_MASK_LL, "datall", 0, 0), // LL
                new TileSpec(DATA_MASK_LR, "datalr", 0, 0), // LR
        };

        // XXX - not the most efficient implementation
        for(int i = 0; i < 4; i++) {
            if((dataMask&tileSpecs[i].mask) == tileSpecs[i].mask)
                cacheTilePart(tileRow, tileColumn, rset, tileSpecs[i].mask, this.serializer.serialize(parts[i]));
        }
    }

    /**************************************************************************/


    public static void createTileCacheDatabase(File tilecacheDatabaseFile) {
        createTileCacheDatabase(tilecacheDatabaseFile.getAbsolutePath());
    }

    public static void createTileCacheDatabase(String tilecacheDatabasePath) {
        DatabaseIface database = null;
        try {
            database = IOProviderFactory.createDatabase(new File(tilecacheDatabasePath));
            ensureTileCache(database);
        } finally {
            if (database != null)
                database.close();
        }
    }

    public static void ensureTileCache(DatabaseIface database) {
        final int cacheVersion = database.getVersion();
        final boolean cacheInvalid = (cacheVersion != DATABASE_VERSION);

        if(cacheInvalid)
            database.execute("DROP TABLE IF EXISTS tilecache", null);

        database.execute("CREATE TABLE IF NOT EXISTS tilecache" +
                            " (tilerow INTEGER, " +
                              "tilecolumn INTEGER, " +
                              "rset INTEGER, " +
                              "tilewidth INTEGER, " +
                              "tileheight INTEGER, " +
                              "datamask INTEGER, " +
                              "dataul BLOB, " +
                              "dataur BLOB, " +
                              "datalr BLOB, " +
                              "datall BLOB);", null);

        database.setVersion(DATABASE_VERSION);
    }

    /**************************************************************************/

    private static boolean assembleTileFromParts(TileCacheData tile, int halfTileWidth,
            int halfTileHeight, TileCacheData[] tileParts, TileCacheData.Compositor compositor,
            ReadCallback callback) {
        int tx;
        int ty;
        int partX;
        int partY;
        int partWidth;
        int partHeight;
        for (int i = 0; i < 4; i++) {
            if (tileParts[i] != null) {
                tx = i % 2;
                ty = i / 2;
                partX = tx * halfTileWidth;
                partY = ty * halfTileHeight;
                partWidth = (Math.min((tx + 1) * (halfTileWidth), tile.getWidth()) - partX);
                partHeight = (Math.min((ty + 1) * (halfTileHeight), tile.getHeight()) - partY);

                if (callback != null && callback.canceled())
                    return false;
                compositor.composite(tile, tileParts[i], partX, partY, partWidth, partHeight);
            }
        }

        return true;
    }

    /**************************************************************************/

    private static class TileSpec {
        public int mask;
        public String field;
        public int width;
        public int height;

        public TileSpec(int mask, String field, int width, int height) {
            this.mask = mask;
            this.field = field;
            this.width = width;
            this.height = height;
        }
    }

    /**************************************************************************/

    /**
     * The callback interface for tile reading.
     */
    public static interface ReadCallback {

        /**
         * Returns a flag indicating whether or not the read has been canceled. This allows for
         * asynchronously cancelling a read that is currently processing.
         * 
         * @return <code>true</code> if the read should be canceled, <code>false</code> otherwise.
         */
        public boolean canceled();

        /**
         * This method is invoked when a region of the requested tile has been read.
         * 
         * @param dstX The x-coordinate of the tile that corresponds to the first pixel in
         *            <code>pixelData</code>
         * @param dstY The y-coordinate of the tile that corresponds to the first pixel in
         *            <code>pixelData</code>
         * @param dstW The width, in pixels, of the region of the tile to be updated with
         *            <code>pixelData</code>
         * @param dstH The height, in pixels, of the region of the tile to be updated with
         *            <code>pixelData</code>
         * @param pixelData The pixel data (in the same format as returned by the <code>read</code>
         *            method of the configured {@link com.atakmap.TileReader.tilesets.TileReader}.
         * @param off The offset into the <code>pixelData</code> array of the first pixel
         * @param len The number of bytes in <code>pixelData</code> that contain the update data
         */
        public void update(int dstX, int dstY, int dstW, int dstH, byte[] pixelData, int off,
                int len);
    } // ReadCallback
}
