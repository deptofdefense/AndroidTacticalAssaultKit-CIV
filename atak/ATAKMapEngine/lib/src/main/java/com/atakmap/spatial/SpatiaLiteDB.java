package com.atakmap.spatial;

import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;


/**
 * Utility class with functions for interrogating and initializing spatial
 * metadata in a SpatiaLite database.
 **/
public
class SpatiaLiteDB
  {
    /**
     * Options for initializing SpatiaLite spatial reference systems.
     *
     * <P>NONE loads no SRIDs
     * <P>WGS84 loads only WGS84 (i.e., 4326)
     * <P>ALL loads all known SRIDs
     *
     **/
    public
    enum SRID
      {
        NONE,                       // Load no SRIDs
        WGS84,                      // Load WGS84 only.
        ALL                         // Load all known SRIDs.
      }


    /**
     * Returns the major version number of a SpatiaLite database.
     *
     * @param db        The SpatiaLite database to check.
     * @return          The major version number (or -1).
     **/
    public static
    int
    getMajorVersion (DatabaseIface db)
      { return getVersion (db)[0]; }


    /**
     * Returns the minor version number of a SpatiaLite database.
     *
     * @param db        The SpatiaLite database to check.
     * @return          The minor version number (or -1).
     **/
    public static
    int
    getMinorVersion (DatabaseIface db)
      { return getVersion (db)[1]; }


    /**
     * Returns the version number of a SpatiaLite database as an array of
     * integers.
     *
     * @param db        The SpatiaLite database to check.
     * @return          An array of (at least) 2 version numbers.  If an error
     *                  occurs { -1, -1 } will be returned.
     **/
    public static
    int[]
    getVersion (DatabaseIface db)
      {
        int[] result = { -1, -1 };
        CursorIface cursor = null;

        try
          {
            cursor = db.query ("SELECT spatialite_version()", null);
            if (cursor.moveToNext ())
              {
                String[] version = cursor.getString (0).split ("\\.");

                if (version.length > 2)
                  { result = new int[version.length]; }
                for (int i = 0; i < version.length; ++i)
                  { result[i] = Integer.parseInt (version[i]); }
              }
          }
        catch (Exception ignored)
          { }
        finally
          {
            if (cursor != null)
              { cursor.close (); }
          }

        return result;
      }


    /**
     * Tests whether a SpatiaLite database has spatial reference system support.
     * 
     * @param db        The SpatiaLite database to check.
     * @return          TRUE if the database has spatial reference system
     *                  support; FALSE otherwise.
     **/
    public static
    boolean
    hasSpatialSupport (DatabaseIface db)
      {
        boolean result = false;
        CursorIface cursor = null;

        try
          {
            cursor = db.query ("SELECT 1 FROM sqlite_master WHERE type='table' AND name='spatial_ref_sys'",
                                null);
            result = cursor.moveToNext ();
          }
        catch (Exception ignored)
          { }
        finally
          {
            if (cursor != null)
              { cursor.close (); }
          }

        return result;
      }


    /**
     * Tests a SpatiaLite database for a spatial reference system.
     *
     * @param db        The SpatiaLite database to check.
     * @param srid      The SRID of the spatial reference system to check for.
     * @return          TRUE if the database has the requested SRID; FALSE
     *                  otherwise.
     **/
    public static
    boolean
    hasSRID (DatabaseIface db,
             int srid)
      {
        boolean result = false;
        CursorIface cursor = null;

        try
          {
            cursor = db.query ("SELECT 1 from spatial_ref_sys WHERE srid = "
                               + String.valueOf (srid),
                               null);
            result = cursor.moveToNext ();
          }
        catch (Exception ignored)
          { }
        finally
          {
            if (cursor != null)
              { cursor.close (); }
          }

        return result;
      }


    /**
     * @param db                The SpatiaLite database to initialize (using a 
     *                          transaction) with all known spatial reference
     *                          systems.
     * @return                  TRUE if the initialization was successful; FALSE
     *                          if the attempt failed (or the spatial metadata
     *                          is already initialized).
     **/
    public static
    boolean
    initSpatialSupport (DatabaseIface db)
      { return initSpatialSupport (db, true, SRID.ALL); }


    /**
     * @param db                The SpatiaLite database to initialize for
     *                          spatial reference system support. 
     * @param useTransaction    Whether to use a transaction.
     * @param initSRID          Determines which SRIDs to insert into the
     *                          spatial reference systems.
     * @return                  TRUE if the initialization was successful; FALSE
     *                          if the attempt failed (or the spatial metadata
     *                          is already initialized).
     **/
    public static
    boolean
    initSpatialSupport (DatabaseIface db,
                        boolean useTransaction,
                        SRID initSRID)
      {
        boolean result = false;

        if (!db.isReadOnly ())
          {
            CursorIface cursor = null;
            int[] version = getVersion (db);

            String initSQL = "SELECT InitSpatialMetaData()";

            if (version[0] > 4 || version[0] == 4 && version[1] >= 1)
              {
                switch (initSRID)
                  {
                  case NONE:

                    initSQL = useTransaction
                        ? "SELECT InitSpatialMetaData(1, 'NONE')"
                        : "SELECT InitSpatialMetaData('NONE')";
                    break;

                  case WGS84:

                    initSQL = useTransaction
                        ? "SELECT InitSpatialMetaData(1, 'WGS84')"
                        : "SELECT InitSpatialMetaData('WGS84')";
                    break;

                  default:

                    if (useTransaction)
                      { initSQL = "SELECT InitSpatialMetaData(1)"; }
                    break;
                  }
              }

            try
              {
                cursor = db.query (initSQL, null);
                result = cursor.moveToNext () && cursor.getInt (0) == 1;
              }
            catch (Exception ignored)
              { }
            finally
              {
                if (cursor != null)
                  { cursor.close (); }
              }
          }

        return result;
      }


    /**
     * @param db        The SpatiaLite database to modify.
     * @param srid      The SRID to add to the spatial reference systems.
     * @return          TRUE if the SRID was added to the database;
     *                  FALSE if the attempt failed or the SRID is already known.
     **/
    public static
    boolean
    insertSRID (DatabaseIface db,
                int srid)
      {
        boolean result = false;

        if (!db.isReadOnly ())
          {
            CursorIface cursor = null;

            try
              {
                cursor = db.query ("SELECT InsertEpsgSrid("
                                   + String.valueOf (srid)
                                   + ")",
                                   null);
                result = cursor.moveToNext () && cursor.getInt (0) == 1;
              }
            catch (Exception ignored)
              { }
            finally
              {
                if (cursor != null)
                  { cursor.close (); }
              }
          }

        return result;
      }


    private
    SpatiaLiteDB ()
      { throw new AssertionError (); }
  }
