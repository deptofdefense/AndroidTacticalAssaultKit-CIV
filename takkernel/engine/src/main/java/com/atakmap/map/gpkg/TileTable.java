package com.atakmap.map.gpkg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.map.gpkg.GeoPackage.ContentsRow;

/**
 * Object class representing the tables required to access tile information from
 * a GeoPackage formatted SQLite database. For more information about the tables
 * related to tile data in a GeoPackage database, see section 2.2 of the GeoPackage
 * specification at http://www.opengeospatial.org/standards/geopackage.
 */
public class TileTable extends GeoPackageContentsTable {
    private static final String TAG = "TileTable";
    
    private DatabaseIface database;
    
    // Package Private visibility, should only be able to create
    // instances of this class from within the GeoPackage class.
    TileTable(DatabaseIface database, ContentsRow contents){
        super (contents);
        this.database = database;
    }

    // Stuff from the gpkg_tile_matrix_set table
    public static class TileMatrixSet {
        public String table_name;
        public int srs_id;
        public double min_x;
        public double min_y;
        public double max_x;
        public double max_y;
    }
    
    /**
     * Read the info for this data table from the "gpkg_tile_matrix_set" table. Primarily
     * contained within this info is a minimum bounding box for this data table that will
     * coverage of the layers within the data table.
     * @return This data tables row from the gpkg_tile_matrix_set table.
     */
    public TileMatrixSet getTileMatrixSetInfo(){
        CursorIface result = null;
        try{
            result = database.query("SELECT * FROM gpkg_tile_matrix_set WHERE table_name=\"" + getName() + "\"", null);
            
            TileMatrixSet returnSet = new TileMatrixSet();
            
            if(!result.moveToNext()){
                return null;
            }
            
            returnSet.table_name = result.getString(result.getColumnIndex("table_name"));
            returnSet.srs_id = result.getInt(result.getColumnIndex("srs_id"));
            returnSet.min_x = result.getDouble(result.getColumnIndex("min_x"));
            returnSet.min_y = result.getDouble(result.getColumnIndex("min_y"));
            returnSet.max_x = result.getDouble(result.getColumnIndex("max_x"));
            returnSet.max_y = result.getDouble(result.getColumnIndex("max_y"));
            
            return returnSet;
        }catch(Exception e){
            Log.e(TAG, "Error reading TileMatrixSet Entry", e);
        }finally{
            if (result != null){
                result.close();
            }
        }
        
        return null;
    }
    
    // Stuff from the gpkg_tile_matrix table
    public static class ZoomLevelRow{
        public int zoom_level;
        public int matrix_width;
        public int matrix_height;
        public int tile_width;
        public int tile_height;
        public double pixel_x_size;
        public double pixel_y_size;
    }
    
    /**
     * Get a list of the zoom levels provided by this GeoPackage file these are not guaranteed to 
     * be contiguous, but they will be sorted in ascending order.
     * @return Array containing a list of integer indexes representing zoom levels in the GeoPackage.
     */
    public int[] getZoomLevels(){
        CursorIface result = null;
        try{
            result = database.query("SELECT zoom_level FROM gpkg_tile_matrix WHERE table_name=\"" + getName() + "\"", null);
            
            List<Integer> resultsList = new ArrayList<Integer>();
            
            while(result.moveToNext()){
                resultsList.add(result.getInt(0));
            }
            
            int[] resultArr = new int[resultsList.size()];
            for(int i = 0 ; i < resultsList.size(); i++){
                resultArr[i] = resultsList.get(i);
            }
            
            Arrays.sort(resultArr);
            
            return resultArr;
        }catch(Exception e){
            Log.e(TAG, "Error reading Zoom Levels", e);
        }finally{
            if (result != null){
                result.close();
            }
        }
        
        return new int[0];
    }
    
    /**
     * Get the info associated with a zoom level within this data table.
     * @param zoomLevel Zoom level to retrieve info for.
     * @return ZoomLevelRow object containing the info for this zoom level,
     * or null if that zoom level does not exist.
     */
    public ZoomLevelRow getInfoForZoomLevel(int zoomLevel){
        if(zoomLevel < 0){
            return null;
        }
        CursorIface result = null;
        try{
            result = database.query("SELECT * FROM gpkg_tile_matrix WHERE table_name=\"" 
                                        + getName()
                                        + "\" AND zoom_level=\"" 
                                        + zoomLevel + "\"", null);
            
            if(!result.moveToNext()){
                return null;
            }
            
            ZoomLevelRow resultRow = new ZoomLevelRow();

            resultRow.zoom_level = result.getInt(result.getColumnIndex("zoom_level"));
            resultRow.matrix_width = result.getInt(result.getColumnIndex("matrix_width"));
            resultRow.matrix_height = result.getInt(result.getColumnIndex("matrix_height"));
            resultRow.tile_width = result.getInt(result.getColumnIndex("tile_width"));
            resultRow.tile_height = result.getInt(result.getColumnIndex("tile_height"));
            resultRow.pixel_x_size = result.getDouble(result.getColumnIndex("pixel_x_size"));
            resultRow.pixel_y_size = result.getDouble(result.getColumnIndex("pixel_y_size"));
            
            // XXX - have observed an issue with GPKG generated by APASS, run
            //       test case ID /opt/tiles/gpkg_tile_matrix/data/data_values_width_height
            
            TileMatrixSet info = getTileMatrixSetInfo();
            if(info != null) {
                final double tileWidthProjUnits = resultRow.tile_width*resultRow.pixel_x_size;
                final double tileHeightProjUnits = resultRow.tile_height*resultRow.pixel_y_size;
                
                final double matrixWidthProjUnits = info.max_x-info.min_x; 
                final double matrixHeightProjUnits = info.max_y-info.min_y;
                
                if(Double.compare(matrixWidthProjUnits, tileWidthProjUnits*resultRow.matrix_width) != 0) {
                    final double expectedMatrixWidth = matrixWidthProjUnits/tileWidthProjUnits;
                    if(isBadPixelXYValue(resultRow.pixel_x_size) || Math.abs(expectedMatrixWidth-resultRow.matrix_width) > 1d) {
                        final double pixel_x_size = matrixWidthProjUnits / (double)(resultRow.matrix_width*resultRow.tile_width); 
                        Log.w(TAG, "Bad pixel_x_size encountered for level " + resultRow.zoom_level + ", computing value " + resultRow.pixel_x_size + " ---> " + pixel_x_size);
                        resultRow.pixel_x_size = pixel_x_size;
                    } else {
                        Log.w(TAG, "Possible precision error for pixel_x_size encountered for level " + resultRow.zoom_level + ", tile registration errors may occur.");
                    }
                    
                }
                if(Double.compare(matrixHeightProjUnits, tileHeightProjUnits*resultRow.matrix_height) != 0) {
                    final double expectedMatrixHeight = matrixHeightProjUnits/tileHeightProjUnits;
                    if(isBadPixelXYValue(resultRow.pixel_y_size) || Math.abs(expectedMatrixHeight-resultRow.matrix_height) > 1d) {
                        final double pixel_y_size = matrixHeightProjUnits / (double)(resultRow.matrix_height*resultRow.tile_height); 
                        Log.w(TAG, "Bad pixel_y_size encountered for level " + resultRow.zoom_level + ", computing value " + resultRow.pixel_y_size + " ---> " + pixel_y_size);
                        resultRow.pixel_y_size = pixel_y_size;
                    } else {
                        Log.w(TAG, "Possible precision error for pixel_y_size encountered for level " + resultRow.zoom_level + ", tile registration errors may occur.");
                    }
                }
            }
            return resultRow;
        }catch(Exception e){
            Log.e(TAG, "Error reading ZoomLevelInfo Entry", e);
        }finally{
            if (result != null){
                result.close();
            }
        }
        
        return null;
    }
    
    private static boolean isBadPixelXYValue(double value) {
        return value == 0d ||
               Double.isNaN(value) ||
               Double.isInfinite(value);
    }
    
    // Stuff from data table
    /**
     * Retrieve the binary data for a tile within this data table. This tile may be compressed
     * as either PNG or JPEG. The GeoPackage format does not specify a means for defining
     * what format tile data is stored in, so the binary data must be inspected for magic numbers
     * to determine it's type. Additionally, the GeoPackage format specifies that data does
     * not need to be stored in a consistent format across all tiles in the data set, so 
     * it cannot be assumed that all tiles share the same format or bit depth.
     * @param zoom Zoom level of the desired tile
     * @param x Column number for the desired tile with in the tile matrix.
     * @param y Row number for the desired tile within the tile matrix
     * @return Binary data representing the tile, in either PNG or JPEG format, or 
     * null if that tile does not exist in the tile data table.
     */
    public byte[] getTile(int zoom, int x, int y){
        CursorIface result = null;
        try{
            result = database.query("SELECT tile_data FROM " + getName() + " WHERE "
                                        + "zoom_level=\"" + zoom + "\" AND "
                                        + "tile_column=\"" + x + "\" AND "
                                        + "tile_row=\"" + y + "\"", null);
            
            if(!result.moveToNext()){
                return null;
            }
            
            return result.getBlob(0);
        }catch(Exception e){
            Log.e(TAG, "Error reading ZoomLevelInfo Entry", e);
        }finally{
            if (result != null){
                result.close();
            }
        }
        
        return null;
    }
}
