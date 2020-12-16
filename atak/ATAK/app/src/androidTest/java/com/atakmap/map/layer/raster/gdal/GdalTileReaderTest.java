
package com.atakmap.map.layer.raster.gdal;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.io.MockIOProvider;
import com.atakmap.map.gdal.GdalLibrary;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

public class GdalTileReaderTest extends ATAKInstrumentedTest {
    @BeforeClass
    public static void setup() {
        Context testContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        File cacheDir = testContext.getCacheDir();

        MockIOProvider mockIOProvider = new MockIOProvider();
        IOProviderFactory.registerProvider(mockIOProvider);

        File gdalDataDir = new File(cacheDir, "gdal");
        GdalLibrary.init(gdalDataDir);
    }

    @Test
    public void testHappyPath() {
        // TODO test with valid tileset data
    }

}
