package com.iai.pri;

import java.io.InputStream;

/**
 * Main JNI interface into the C++ PRI implementation. This class will try and
 * load a library named "prijni" by default. A different name for the library
 * can be set by defining the com.iai.pri.PRIJNILibrary system property. I.E.
 * by adding the argument -Dcom.iai.pri.PRIJNILibrary=somelibrary to the jvm
 * when it is started.
 **/
public class PRIJNI {
    private static String PRI_JNI_LIB = "prijni";

// Remove the loading of the library so that ATAK can do this using it's security sanctioned 
// method.

//    static{
//        try {
//            System.loadLibrary("gnustl_shared");
//        
//            String libraryName = System.getProperty(
//                                    "com.iai.pri.PRIJNILibrary", PRI_JNI_LIB);
//            System.loadLibrary(libraryName);
//        } catch (UnsatisfiedLinkError e) {
//            System.out.println("Unable to load native library " + PRI_JNI_LIB);
//            System.out.println("Check java.library.path=" + System.getProperty("java.library.path"));
//            e.printStackTrace();
//            throw e; // re-throw
//        }
//    }

    // -------- PRI Methods ------------------------------

    /**
     * Use a PRI image's metadata to calculate geographic points using image
     * pixel locations.
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param points An array of PRIImagePoints that can should be used to
     * calculate equivalent ground points.
     * @return Array of PRIGroundPoints representing the image points passed
     * into this method. These points will be in the same order as their
     * corresponding image points. If an error occurred or the image was not a
     * PRI, this method will return null.
     **/
    public native static PRIGroundPoint[] imageToGround(
                                        long pointer, PRIImagePoint[] points);

    /**
     * Use a PRI image's metadata to calculate pixel locations using geographic
     * points on the earth. <br/><br/>
     * NOTE: This method is synchronized while most others are because it does
     * not interact with the cache that stores loaded PRI images in the native
     * code.
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param points An array of PRIGroundPoints that can should be used to
     * calculate equivalent pixel locations.
     * @return Array of PRIImagePoints representing the ground points passed
     * into this method. These points will be in the same order as their
     * corresponding ground points. If an error occurred, or the image was not a
     * PRI, this method will return null.
     **/
    public native static PRIImagePoint[] groundToImage(long pointer, PRIGroundPoint[] points);

    /**
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param structTop The image coordinate of the top of the structure whose
     * height is to be measured.
     * @param shadowTip The image coordinate of the tip of the shadow that 
     * corresponds to the top of the structure, i.e. structTop.
     * @return The estimated height of the structure in meters.
     **/
    //public native static synchronized double getHeightOffsetTopToShadowTip(long pointer, PRIImagePoint structureTopCoord, PRIImagePoint shadowTipCoord);
    //public native static synchronized double getHeightOffsetTopToShadowTip(long structureTopPriPointer, PRIImagePoint structureTopCoord, long shadowTipPriPointer, PRIImagePoint shadowTipCoord);
    
    public native static PRIGroundPoint getHeightOffsetTopToShadowTip(long pointer, PRIImagePoint structureTopCoord, PRIImagePoint shadowTipCoord);
    public native static PRIGroundPoint getHeightOffsetTopToShadowTip(long structureTopPriPointer, PRIImagePoint structureTopCoord, long shadowTipPriPointer, PRIImagePoint shadowTipCoord);

    /**
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param baseCoord The image coordinate of the base of the structure whose
     * height is to be measured.
     * @param shadowTipCoord The image coordinate of the tip of the shadow that 
     * corresponds to the top of the structure.
     * @return The estimated height of the structure in meters.
     **/
    public native static double getHeightOffsetBaseToShadowTip(long pointer, PRIImagePoint structureBaseCoord, PRIImagePoint shadowTipCoord);
    public native static double getHeightOffsetBaseToShadowTip(long structureBasePriPointer, PRIImagePoint structureBaseCoord, long shadowTipPriPointer, PRIImagePoint shadowTipCoord);

    /**
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @param baseCoord The image coordinate of the base of the structure whose
     * height is to be measured.
     * @param topCoord The image coordinate of the top of the structure whose
     * height is to be measured.
     * @return The estimated height of the structure in meters.
     **/
    public native static double getHeightOffsetBaseToTop(long pointer, PRIImagePoint structureBaseCoord, PRIImagePoint structureTopCoord);
    public native static double getHeightOffsetBaseToTop(long structureBasePriPointer, PRIImagePoint structureBaseCoord, long structureTopPriPointer, PRIImagePoint structureTopCoord);
   
   /**
     * Test whether or not a file is a PRI image.
     * @param path Full or relative path on disk to where the image to test is
     * located.
     * @return True if the image is a PRI that can be exploited, false otherwise.
     **/
    public native static boolean isPRI(String path);

    /**
     * Test whether or not a file is a PRI image.
     * @param inputStream InputStream object that contains PRI file data. This
     * stream is assumed to currently set to the beginning of the file, so the
     * next call to read() will read the first byte of the PRI NITF file header.
     * @return Positive long value representing a pointer to a PRI object stored
     * in native memory if the file is a PRI, -1 otherwise. If this method
     * returns a positive long value, you MUST then call
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * at some later time or the native code will leak the memory for the
     * tested PRI.
     **/
    public native static long isPRI(InputStream inputStream);

    /**
     * Retrieves the corners for an image if it is a PRI.
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to the
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * method, as that may cause a crash when the native code attempts to
     * dereference a pointer that has already been deleted.
     * @return PRICorners object containing the image's corners and it's width
     * and height. If the image isn't a PRI, or an error occurs, this method
     * will return null.
     **/
    public native static PRICorners getCorners(long pointer);

    /**
     * Returns the elevation, in meters HAE, at the given image pixel.
     */
    public native static double getElevation(long pointer, double x, double y);

    public native static int getNumElevationSegments(long pointer);
    public native static ElevationSegmentData getElevationSegmentData(long pointer, int idx);

    // -------- Common Methods ------------------------------

    /**
     * Loads a PRI file from disk into memory.
     * @param path Full or relative path on disk to the file to be loaded
     * into memory.
     * @return Positive long value representing a pointer to a PRI object stored
     * in native memory if the file is a PRI, -1 otherwise. If this method
     * returns a positive long value, you MUST then call
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * at some later time or the native code will leak the memory for the
     * loaded PRI.
     **/
    public native static long loadImage(String path);

    /**
     * Loads a PRI file from an InputStream into memory.
     * @param inputStream InputStream object that contains PRI file data. This
     * stream is assumed to currently set to the beginning of the file, so the
     * next call to read() will read the first byte of the PRI NITF file header.
     * @return Positive long value representing a pointer to a PRI object stored
     * in native memory if the file is a PRI, -1 otherwise. If this method
     * returns a positive long value, you MUST then call
     * {@link com.iai.pri.PRIJNI#clearImageCache(long) clearImageCache()}
     * at some later time or the native code will leak the memory for the
     * loaded PRI.
     **/
    public native static long loadImage(InputStream inputStream);

    /**
     * Tells native code to delete the object represented by a given pointer.
     * The user is responsible for deleting these pointers when they become
     * unnecessary. The native code will not delete this data without being
     * told to do so.
     * @param pointer Long representing a pointer to a PRI file that has been
     * loaded into memory. This pointer can be acquired by calling the
     * {@link com.iai.pri.PRIJNI#loadImage(String) loadImage()} method. DO NOT
     * call this method using a long that has already been passed to this
     * method previously, as that may cause a crash when the native code
     * attempts to delete a pointer that has already been deleted.
     **/
    public native static void clearImageCache(long pointer);
}
