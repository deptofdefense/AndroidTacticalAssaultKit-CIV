
package com.atakmap.map.layer.raster;

/**
 * Attempts to determine a given File objects imagery file type. When reasonable logic/libraries
 * specific to each type are used to analyse objects. Otherwise the filename suffix is the default
 * in determining type. Notes - Suffix processing assumes the common imagery zip file naming
 * convention of name+type+zip; "NY_MOSAIC.sid.zip" - MIME types are currently not used but left as
 * a hook; incomplete.
 */
public class ImageryFileType extends ImageryFileTypeBase {
}
