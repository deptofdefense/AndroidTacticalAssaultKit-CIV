package com.atakmap.map.gpkg;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.atakmap.coremap.locale.LocaleUtil;

import com.atakmap.map.gpkg.GeoPackage.ContentsRow;

public class GeoPackageContentsTable
  {
    private ContentsRow contentsRow;

    protected GeoPackageContentsTable (ContentsRow r)
      { contentsRow = r; }

    /**
     * Get the name of the table that contains this layer's tile data. 
     * This value is the value stored in the "table_name" row of the 
     * gpkg_contents table.
     * @return This tile table's name.
     */
    public String getName ()
      { return contentsRow.table_name; }

    /**
     * Get a human readable identifier for this tile table. 
     * This value is the value stored in the "identifier" row of the 
     * gpkg_contents table.
     * @return A human-readable identifier (e.g. short name) for the
     * table_name content
     */
    public String getIdentifier ()
      { return contentsRow.identifier; }

    /**
     * Get a human readable description for this tile table. 
     * This value is the value stored in the "description" row of the 
     * gpkg_contents table.
     * @return A human-readable description for the table_name content.
     */
    public String getDescription ()
      { return contentsRow.description; }

    /**
     * Get a timestamp value associated with the last time that this
     * data was modified. This timestamp is stored in ISO 8601 format,
     * which will appear something like this: 2014-11-07T09:35:44.444Z
     * This value is the value stored in the "last_change" row of the 
     * gpkg_contents table.
     * @return Timestamp value in ISO 8601format as defined by the
     * strftime function '%Y-%m-%dT%H:%M:%fZ' format string applied 
     * to the current time when this file was last modified.
     */
    public String getLastChange ()
      { return contentsRow.last_change; }

    /**
     * Get this table's "last_change" value after conversion to a Java Date
     * object. See {@link com.atakmap.map.gpkg.TileTable#getLastChange() getLastChange()}
     * for more information on the "last_change" value.
     * @return This table's "last_change" value converted to a Java Date object.
     */
    public Date getLastChangeDate ()
      {
        try
          {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", LocaleUtil.getCurrent());
            return format.parse(contentsRow.last_change);
          }
        catch (ParseException e)
          { return null; }
      }

    /**
     * Get a bounding box minimum easting or longitude value for the
     * data stored in this data table. This value is part of an informative
     * bounding box, and is not necessarily the minimum bounding box surrounding
     * the data in this table.
     * This value is the value stored in the "min_x" row of the 
     * gpkg_contents table.
     * @return Bounding box minimum easting or longitude for all content in
     * this table.
     */
    public double getMinX ()
      { return contentsRow.min_x; }

    /**
     * Get a bounding box maximum easting or longitude value for the
     * data stored in this data table. This value is part of an informative
     * bounding box, and is not necessarily the minimum bounding box surrounding
     * the data in this table.
     * This value is the value stored in the "max_x" row of the 
     * gpkg_contents table.
     * @return Bounding box maximum easting or longitude for all content in
     * this table.
     */
    public double getMaxX ()
      { return contentsRow.max_x; }

    /**
     * Get a bounding box minimum northing or latitude value for the
     * data stored in this data table. This value is part of an informative
     * bounding box, and is not necessarily the minimum bounding box surrounding
     * the data in this table.
     * This value is the value stored in the "min_y" row of the 
     * gpkg_contents table.
     * @return Bounding box minimum northing or latitude for all content in
     * this table.
     */
    public double getMinY ()
      { return contentsRow.min_y; }

    /**
     * Get a bounding box maximum northing or latitude value for the
     * data stored in this data table. This value is part of an informative
     * bounding box, and is not necessarily the minimum bounding box surrounding
     * the data in this table.
     * This value is the value stored in the "max_y" row of the 
     * gpkg_contents table.
     * @return Bounding box maximum northing or latitude for all content in
     * this table.
     */
    public double getMaxY ()
      { return contentsRow.max_y; }

    /**
     * Get the Spatial Reference ID for this data table. This value
     * should match a value stored in this table's containing database's
     * "gpkg_spatial_ref_sys" table. See 
     * {@link com.atakmap.map.gpkg.GeoPackage#getSRSInfo(int) GeoPackage#getSRSInfo(int)}
     * for how to look up associated SRS data.
     * This value is the value stored in the "srs_id" row of the 
     * gpkg_contents table.
     * @return Spatial Reference ID (SRS).
     */
    public int getSRSID ()
      { return contentsRow.srs_id; }
  }