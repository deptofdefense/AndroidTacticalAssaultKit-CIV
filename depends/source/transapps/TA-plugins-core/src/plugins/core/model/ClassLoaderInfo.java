package plugins.core.model;

import android.os.Bundle;

/**
 * Information related to the loading of the classes for the Java class loader.
 * These items are very low level paths and directories as used by the Android Dalvik VM.
 *
 */
public class ClassLoaderInfo {

    /**
     * Static class and fields for defining the ClassLoader Info portion of the Plugin Table
     */
    public static class Fields {
        public static final String DEX_PATH = "dexpath";
        public static final String DEX_OUTPUT_DIR = "dexoutputdir";
        public static final String LIB_PATH = "libpath";
    }
    
    private String dexPath;
    private String dexOutputDir;
    private String libPath;

    /**
     * Default Constructor
     */
    public ClassLoaderInfo() {
    }

    /**
     * Constructor to load from a Bundle
     *
     * @param bundle Bundle containing the Class Loader info fields:dex output directory, dex path and library path
     */
    public ClassLoaderInfo(Bundle bundle) {
        setDexOutputDir(bundle.getString(Fields.DEX_OUTPUT_DIR));
        setDexPath(bundle.getString(Fields.DEX_PATH));
        setLibPath(bundle.getString(Fields.LIB_PATH));
    }

    /**
     * Constructor taking the dex path, dex output directory and load library path
     * @param dexPath The path where the plugin application is installed
     * @param dexOutputDir The path where the dalvik VM caches dependency files for an application
     * @param libPath the path to the native libraries in the plugin application
     */
    public ClassLoaderInfo( String dexPath, String dexOutputDir, String libPath ) {
        setDexPath(dexPath);
        setDexOutputDir(dexOutputDir);
        setLibPath(libPath);
    }

    /**
     * Get the dex path
     *
     * @return String dex path
     */
    public String getDexPath() {
        return dexPath;
    }

    /**
     * Set the dex path
     *
     * @param dexPath String dex path
     */
    public void setDexPath(String dexPath) {
        this.dexPath = dexPath;
    }

    /**
     * Get the Dalvik VM cache output path for this plugin
     *
     * @return String Plugin application specific dalvik cache directory
     */
    public String getDexOutputDir() {
        return dexOutputDir;
    }

    /**
    * Get the Dalvik VM cache output path for this plugin
    *
    * @param dexOutputDir The plugin application specific dalvik cache directory
    */
    public void setDexOutputDir(String dexOutputDir) {
        this.dexOutputDir = dexOutputDir;
    }

    /**
     * Returns the plugin application specific directory where any native libraries for this plugin
     * reside.
     *
     * @return String path to the plugin's native library path
     */
    public String getLibPath() {
        return libPath;
    }

    /**
     * Set the path to the native libraries for this plugin.
     *
     * @param libPath
     */
    public void setLibPath(String libPath) {
        this.libPath = libPath;
    }

    /**
     * Conversion function to store the data from this class into a bundle.
     *
     * @return Bundle containg the ClassLoader information.
     */
    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(Fields.DEX_OUTPUT_DIR, getDexOutputDir());
        bundle.putString(Fields.DEX_PATH, getDexPath());
        bundle.putString(Fields.LIB_PATH, getLibPath());
        return bundle;
    }
}
