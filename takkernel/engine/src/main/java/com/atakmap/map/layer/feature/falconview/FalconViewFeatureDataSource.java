package com.atakmap.map.layer.feature.falconview;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.database.DatabaseIface;
import com.atakmap.interop.Pointer;
import com.atakmap.map.layer.feature.FeatureDataSource;
import com.atakmap.map.layer.feature.NativeFeatureDataSource;

import java.io.File;
import java.io.IOException;

import gov.tak.api.annotation.DontObfuscate;

/**
 * Native feature data source for LPT and DRW files
 */
@DontObfuscate
public class FalconViewFeatureDataSource implements FeatureDataSource {

    private static final String TAG = "FalconViewFeatureDataSource";

    public FalconViewFeatureDataSource() {
        // Register this provider interface with JNI
        registerProvider();
    }

    @Override
    public Content parse(File file) throws IOException {
        String ext = FileSystemUtils.getExtension(file, true, false);
        if (!ext.equals("LPT") && !ext.equals("DRW"))
            return null;

        // Attempt to parse the .lpt/.drw file as a valid database
        Pointer ptr = parse(file.getAbsolutePath());
        if (ptr == null)
            return null;

        return new NativeFeatureDataSource.NativeContent(ptr);
    }

    @Override
    public String getName() {
        return "falconview";
    }

    @Override
    public int parseVersion() {
        return 1;
    }

    /**
     * Interface for creating database interfaces given a file
     */
    public interface DBCreator {
        DatabaseIface createDatabase(File file);
    }

    private static DBCreator _dbCreator;

    /**
     * Set the database creator instance
     * Must be set or else database interfaces will not be created
     * @param creator Database creator
     */
    public static void initDBCreator(DBCreator creator) {
        _dbCreator = creator;
    }

    /**
     * Creates a new database interface given a LPT/DRW file
     * @param file LPT/DRW file
     * @return Database interface
     */
    public static DatabaseIface createDatabase(File file) {
        if (_dbCreator != null)
            return _dbCreator.createDatabase(file);
        return null;
    }

    static native Pointer parse(String path);

    static native void registerProvider();
}