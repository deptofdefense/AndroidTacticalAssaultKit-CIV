
package com.atakmap.map.gdal;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.gdal.gdal.Dataset;
import org.gdal.gdal.gdal;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.ogr;
import org.gdal.osr.SpatialReference;

import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.EngineLibrary;
import com.atakmap.map.layer.raster.tilereader.TileReader;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.util.ConfigOptions;

import static com.atakmap.map.gdal.VSIJFileFilesystemHandler.installFilesystemHandler;


public class GdalLibrary {

    public static SpatialReference EPSG_4326 = null;
    private static boolean initialized = false;
    private static boolean initSuccess = false;

    private static Map<String, DatabaseInfo> readonlyDatabases = new HashMap<String, DatabaseInfo>();
    private static Map<String, DatabaseInfo> readwriteDatabases = new HashMap<String, DatabaseInfo>();

    private GdalLibrary() {
    } // non-instantiable

    /*************************************************************************/

    public static synchronized boolean init(File gdalDataDir) {
        if (!initialized) {
            try {
                EngineLibrary.initialize();

                initImpl(gdalDataDir);
            } catch (IOException ignored) {
            } finally {
                initialized = true;
            }
        }
        return initSuccess;
    }

    public static synchronized boolean isInitialized() {
        return initialized;
    }

    private static void initImpl(File gdalDir) throws IOException {

        // IMPORTANT: as of GDAL upgrade 2.2.3, some SQLite containers are now
        //            supported natively by GDAL. Specify that these drivers
        //            should be skipped as they are breaking normal handling of
        //            GPKG and MBtiles.
        
        // XXX - use of Driver::Deregister() is observed NOT to work

        gdal.SetConfigOption("GDAL_SKIP", "MBTiles,GPKG");

        // NOTE: AllRegister automatically loads the shared libraries
        gdal.AllRegister();
        gdal.SetConfigOption("GDAL_DATA", gdalDir.getAbsolutePath());
        // debugging
        gdal.SetConfigOption("CPL_DEBUG", "OFF");
        gdal.SetConfigOption("CPL_LOG_ERRORS", "ON");

        gdal.SetConfigOption("GDAL_DISABLE_READDIR_ON_OPEN", "TRUE");

        if (!IOProviderFactory.exists(gdalDir)) {
            if (!IOProviderFactory.mkdirs(gdalDir)) {
                Log.d("GdalLibrary", "XXX: bad could not make the gdalDir: " + gdalDir);
            }
        }
        File gdalDataVer = new File(gdalDir, "gdal.version");
        boolean unpackData = true;
        if (IOProviderFactory.exists(gdalDataVer)) {
            byte[] versionBytes = new byte[(int) IOProviderFactory.length(gdalDataVer)];
            InputStream inputStream = null;
            try {
                inputStream = IOProviderFactory.getInputStream(gdalDataVer);
                int r = inputStream.read(versionBytes);
                if (r != versionBytes.length) 
                    Log.d("GdalLibrary", "versionBytes, read: " + r + " expected: " + versionBytes.length);

            } finally {
                if (inputStream != null)
                    inputStream.close();
            }
            final int libVersion = Integer.parseInt(gdal.VersionInfo("VERSION_NUM"));
            final int devVersion = Integer.parseInt(new String(versionBytes, FileSystemUtils.UTF8_CHARSET));

            unpackData = (libVersion > devVersion);
        }
        
        // make sure all files from the last unpack are present
        File gdalDataList = new File(gdalDir, "gdaldata.list2");
        if(!unpackData && IOProviderFactory.exists(gdalDataList)) {
            FileInputStream inputStream = null;
            DataInputStream dataInput = null;
            try {
                inputStream = IOProviderFactory.getInputStream(gdalDataList);
                dataInput = new DataInputStream(inputStream);
                
                final int numFiles = dataInput.readInt();
                int fileSize;
                String fileName;
                File dataFile;
                for(int i = 0; i < numFiles; i++) {
                    fileSize = dataInput.readInt();
                    fileName = dataInput.readUTF();
                    
                    dataFile = new File(gdalDir, FileSystemUtils.sanitizeWithSpacesAndSlashes(fileName));
                    if(!IOProviderFactory.exists(dataFile)|| IOProviderFactory.length(dataFile) != fileSize) {
                        unpackData = true;
                        break;
                    }
                }
            } catch(IOException e) {
                Log.w("GdalLibrary", "Unexpected IO error validating GDAL data list", e);
                unpackData = true;
            } finally {
                if(dataInput != null)
                    dataInput.close();
                if(inputStream != null)
                    inputStream.close();
            }
        } else {
            unpackData = true;
        }
    
        boolean initFailed = false;
        do {
            if (unpackData)
                unpackData(gdalDir, gdalDataVer, gdalDataList);
    
            if(!initFailed) {
                // if the unpack looks good try to init the spatial reference
                try {
                    EPSG_4326 = new SpatialReference();
                    EPSG_4326.ImportFromEPSG(4326);
                } catch(Exception e) {
                    // general exception occurred, force unpack
                    initFailed = true;
                    unpackData = true;
                    continue;
                }
            }
            
            break;
        } while(true);

        registerProjectionSpi();
        VSIFileFileSystemHandler vsiJfileHandler = new VSIFileFileSystemHandler();
        installFilesystemHandler(vsiJfileHandler);

        if(IOProviderFactory.isDefault())
            ConfigOptions.setOption("gdal-vsi-prefix", null);
        else
            ConfigOptions.setOption("gdal-vsi-prefix", VSIFileFileSystemHandler.PREFIX);

        initSuccess = true;
    }
    
    private static void unpackData(File gdalDir, File gdalDataVer, File gdalDataList) throws IOException {
        FileOutputStream fileOutputStream = null;

        InputStream inputStream = null;

        // obtain the data files listing
        URL url = GdalLibrary.class.getClassLoader().getResource("gdal/data/gdaldata.files");
        if (url == null)
            return;

        Collection<String> dataFiles = new LinkedList<>();

        InputStreamReader inputStreamReader;
        BufferedReader bufferedReader;
        try {
            inputStream = url.openStream();

            inputStreamReader = new InputStreamReader(inputStream);
            bufferedReader = new BufferedReader(inputStreamReader);
            String line = bufferedReader.readLine();
            while (line != null) {
                url = GdalLibrary.class.getClassLoader().getResource("gdal/data/" + line);
                if (url != null) {
                    // XXX - warn file not found
                    dataFiles.add(line);
                }
                
                line = bufferedReader.readLine();
            }
        } finally {
            if (inputStream != null)
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
        }

        Iterator<String> iter = dataFiles.iterator();
        String dataFileName;
        byte[] transfer = new byte[8192];
        int transferSize;
        File dataFile;
        FileOutputStream dataListFileStream = null;
        DataOutputStream dataListStream = null;
        try {
            dataListFileStream = IOProviderFactory.getOutputStream(gdalDataList);
            dataListStream = new DataOutputStream(dataListFileStream);
            
            dataListStream.writeInt(dataFiles.size());

            while (iter.hasNext()) {
                dataFileName = FileSystemUtils.sanitizeWithSpacesAndSlashes(iter.next());

                inputStream = null;
                fileOutputStream = null;
                try {
                    url = GdalLibrary.class.getClassLoader().getResource("gdal/data/" + dataFileName);
                    inputStream = url.openStream();
                    dataFile = new File(gdalDir, dataFileName);
                    fileOutputStream = IOProviderFactory.getOutputStream(dataFile);
    
                    do {
                        transferSize = inputStream.read(transfer);
                        if (transferSize > 0)
                            fileOutputStream.write(transfer, 0, transferSize);
                    } while (transferSize >= 0);
                } finally {
                    if (fileOutputStream != null)
                        fileOutputStream.close();
                    if (inputStream != null)
                        inputStream.close();
                }
                
                dataListStream.writeInt((int)IOProviderFactory.length(dataFile));
                dataListStream.writeUTF(dataFileName);
            }
        } finally {
            if(dataListFileStream != null)
                dataListFileStream.close();
            if(dataListStream != null)
                dataListStream.close();
        }

        // write out the gdal version that the data files correspond to
        try {
            fileOutputStream = IOProviderFactory.getOutputStream(gdalDataVer);
            fileOutputStream.write(gdal.VersionInfo("VERSION_NUM").getBytes());
        } catch (IOException ignored) {
            // not really a major issue
        } finally {
            if (fileOutputStream != null)
                try {
                    fileOutputStream.close();
                } catch (IOException ignored) {
                }
        }
    }

    public static int getSpatialReferenceID(SpatialReference srs) {
        if (srs == null)
            return -1;
        String value;

        value = srs.GetAttrValue("AUTHORITY", 0);
        if (value == null || !value.equals("EPSG")) {
            // note there are producers out there that are supplying prj files without
            // the AUTHORITY set such as "WGS_1984_UTM_Zone_56S", which really is
            // AUTHORITY["EPSG","32756"] -- it seems like ESRI is the product being used
            // to generate the prj files.

            // this could exist anywhere in the PROJCS list, we may need to revisit
            value = srs.GetAttrValue("PROJCS", 0);
            if (value != null) {
                return CoordSysName2EPSG.lookup(value);
            }

            return -1;
        }
        value = srs.GetAttrValue("AUTHORITY", 1);
        if (value == null || !value.matches("\\d+"))
            return -1;
        return Integer.parseInt(value);
    }

    public static synchronized SQLiteDatabase openDatabase(String path,
            SQLiteDatabase.CursorFactory factory, int flags, DatabaseErrorHandler errorHandler) {
        Map<String, DatabaseInfo> databaseMap = (((flags & SQLiteDatabase.OPEN_READONLY) == SQLiteDatabase.OPEN_READONLY) ? readonlyDatabases
                : readwriteDatabases);
        DatabaseInfo info = databaseMap.get(path);
        if (info != null) {
            info.referenceCount++;
            return info.database;
        } else {
            SQLiteDatabase retval = SQLiteDatabase.openDatabase(path, factory, flags, errorHandler);
            databaseOpened(retval);
            return retval;
        }
    }

    public static synchronized SQLiteDatabase openDatabase(String path,
            SQLiteDatabase.CursorFactory factory, int flags) {
        Map<String, DatabaseInfo> databaseMap = (((flags & SQLiteDatabase.OPEN_READONLY) == SQLiteDatabase.OPEN_READONLY) ? readonlyDatabases
                : readwriteDatabases);
        DatabaseInfo info = databaseMap.get(path);
        if (info != null) {
            info.referenceCount++;
            return info.database;
        } else {
            SQLiteDatabase retval = SQLiteDatabase.openDatabase(path, factory, flags);
            databaseOpened(retval);
            return retval;
        }
    }

    public static SQLiteDatabase openOrCreateDatabase(String path,
            SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
        return openDatabase(path, factory, SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler);
    }

    public static SQLiteDatabase openOrCreateDatabase(String path,
            SQLiteDatabase.CursorFactory factory) {
        return openDatabase(path, factory, SQLiteDatabase.CREATE_IF_NECESSARY);
    }

    public static SQLiteDatabase openOrCreateDatabase(File file,
            SQLiteDatabase.CursorFactory factory) {
        return openDatabase(file.getPath(), factory, SQLiteDatabase.CREATE_IF_NECESSARY);
    }

    public static synchronized void closeDatabase(SQLiteDatabase database) {
        Map<String, DatabaseInfo> databaseMap = (database.isReadOnly() ? readonlyDatabases
                : readwriteDatabases);

        DatabaseInfo info = databaseMap.get(database.getPath());
        if (info == null || info.referenceCount == 0)
            throw new IllegalStateException();
        info.referenceCount--;
        if (info.referenceCount < 1) {
            databaseMap.remove(database.getPath());
            database.close();
        }
    }

    private static void databaseOpened(SQLiteDatabase database) {
        Map<String, DatabaseInfo> databaseMap = (database.isReadOnly() ? readonlyDatabases
                : readwriteDatabases);
        databaseMap.put(database.getPath(), new DatabaseInfo(database));
    }

    /**
     * @deprecated use {@link TileReader#getMasterIOThread()}
     */
    @Deprecated
    @DeprecatedApi(since = "4.1", forRemoval = true, removeAt = "4.4")
    public static synchronized TileReader.AsynchronousIO getMasterIOThread() {
        return TileReader.getMasterIOThread();
    }

    /**
     * Returns the WKT for the associated SRID.
     * 
     * @param srid  The SRID
     * 
     * @return  The WKT for the SRID or <code>null</code> if the WKT is not
     *          defined for the SRID
     */
    public static String getWkt(int srid) {
        SpatialReference spatialRef = new SpatialReference();
        int err = ogr.OGRERR_FAILURE;
        try {
            err = spatialRef.ImportFromEPSG(srid); 
        } catch(RuntimeException ignored) {}
        
        return (err == ogr.OGRERR_NONE) ? spatialRef.ExportToWkt() : null;
    }

    public static Dataset openDatasetFromFile(File file) {
        // implement chaining per https://gdal.org/user/virtual_file_systems.html#chaining
        String path = file.getAbsolutePath();
        // custom TAK IO VSI
        if(!IOProviderFactory.isDefault())
            path = VSIFileFileSystemHandler.PREFIX + path;
        // zip VSI
        if(file instanceof ZipVirtualFile)
            path = "/vsizip/" + path;
        return org.gdal.gdal.gdal.Open(path);
    }

    public static Dataset openDatasetFromFile(File file, int accessOpts) {
        // implement chaining per https://gdal.org/user/virtual_file_systems.html#chaining
        String path = file.getAbsolutePath();
        // custom TAK IO VSI
        if(!IOProviderFactory.isDefault())
            path = VSIFileFileSystemHandler.PREFIX + path;
        // zip VSI
        if(file instanceof ZipVirtualFile)
            path = "/vsizip/" + path;
        return org.gdal.gdal.gdal.Open(path, accessOpts);
    }

    public static Dataset openDatasetFromPath(String path) {
        File file;
        if(path.startsWith("zip://")) {
            path = path.replace("zip://", "");
            path = path.replace("%20", " ").
                        replace("%23", "#").
                        replace("%5B", "[").
                        replace("%5D", "]");
            file = new ZipVirtualFile(path);
        } else if (path.contains(".zip")) {
            file = new ZipVirtualFile(path);
        } else {
            if(path.startsWith("file:///"))
                path = path.substring(7);
            else if(path.startsWith("file://"))
                path = path.substring(6);
            path = path.replace("%20", " ").
                        replace("%23", "#").
                        replace("%5B", "[").
                        replace("%5D", "]");
            file = new File(path);
        }

        // if the file doesn't exist, it may be a pre-baked path, try opening
        // directly before going through to the File based method
        if(!IOProviderFactory.exists(file)) {
            Dataset dataset = org.gdal.gdal.gdal.Open(path);
            if(dataset != null)
                return dataset;
        }

        return openDatasetFromFile(file);
    }

    public static DataSource openDataSourceFromFile(String path) {
        if(IOProviderFactory.isDefault())
            return org.gdal.ogr.ogr.Open(path);
        else
            return org.gdal.ogr.ogr.Open(VSIFileFileSystemHandler.PREFIX + path);
    }

    /**************************************************************************/

    private static class DatabaseInfo {
        public final SQLiteDatabase database;
        public int referenceCount;

        public DatabaseInfo(SQLiteDatabase database) {
            this.database = database;
            this.referenceCount = 1;
        }
    }

    public static native void registerProjectionSpi();
}
