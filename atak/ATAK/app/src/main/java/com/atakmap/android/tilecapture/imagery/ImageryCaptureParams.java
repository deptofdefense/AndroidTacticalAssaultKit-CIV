
package com.atakmap.android.tilecapture.imagery;

import com.atakmap.android.tilecapture.TileCaptureParams;

import java.io.File;

/**
 * Parameters for imagery capture of multiple tiles
 *
 * See the {@link TileCaptureParams} super-class for info on
 * each attribute
 */
public class ImageryCaptureParams extends TileCaptureParams {

    // Output directory to save tiles
    public File outputDirectory;
}
