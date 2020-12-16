
package com.atakmap.map.layer.raster;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import com.atakmap.coremap.locale.LocaleUtil;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;


import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.io.ZipVirtualFile;
import com.atakmap.map.layer.raster.pfps.PfpsMapTypeFrame;
import com.atakmap.android.maps.tilesets.TilesetInfo;

/**
 * Attempts to determine a given File objects imagery file type. When reasonable logic/libraries
 * specific to each type are used to analyse objects. Otherwise the filename suffix is the default
 * in determining type. Notes - Suffix processing assumes the common imagery zip file naming
 * convention of name+type+zip; "NY_MOSAIC.sid.zip" - MIME types are currently not used but left as
 * a hook; incomplete.
 */
public class ImageryFileType extends ImageryFileTypeBase {
}
