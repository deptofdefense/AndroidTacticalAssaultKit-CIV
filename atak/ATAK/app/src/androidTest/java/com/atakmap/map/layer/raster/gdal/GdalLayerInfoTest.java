
package com.atakmap.map.layer.raster.gdal;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.androidtest.util.FileUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.io.MockIOProvider;
import com.atakmap.map.gdal.GdalLibrary;
import com.atakmap.map.layer.raster.DatasetDescriptor;
import com.atakmap.map.layer.raster.DatasetDescriptorSpiArgs;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.Set;

public class GdalLayerInfoTest extends ATAKInstrumentedTest {
    @BeforeClass
    public static void setup() {
        Context testContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        File cacheDir = testContext.getCacheDir();

        File gdalDataDir = new File(cacheDir, "gdal");
        GdalLibrary.init(gdalDataDir);

        MockIOProvider mockIOProvider = new MockIOProvider();
        IOProviderFactory.registerProvider(mockIOProvider);
    }

    @Test
    public void testNoFile() {
        File f1 = new File("/sdcard/atak/notExist1");
        File f2 = new File("/sdcard/atak/notExist2");
        Set<DatasetDescriptor> descs = GdalLayerInfo.INSTANCE
                .create(new DatasetDescriptorSpiArgs(f1, f2));
        assertNull(descs);
    }

    @Test
    public void testEmptyFile() {
        try (FileUtils.AutoDeleteFile f1 = FileUtils.AutoDeleteFile
                .createTempFile();
                FileUtils.AutoDeleteFile f2 = FileUtils.AutoDeleteFile
                        .createTempFile()) {

            Set<DatasetDescriptor> descs = GdalLayerInfo.INSTANCE
                    .create(new DatasetDescriptorSpiArgs(f1.file, f2.file));
            assertNull(descs);
        }
    }

    @Test
    public void testGoodFiles() {
        // TODO create data or read existing data that is well-formed rather than temp file
        try (FileUtils.AutoDeleteFile f1 = FileUtils.AutoDeleteFile
                .createTempFile()) {
            File workingDir = InstrumentationRegistry.getInstrumentation()
                    .getTargetContext().getCacheDir();
            Set<DatasetDescriptor> descs = GdalLayerInfo.INSTANCE
                    .create(new DatasetDescriptorSpiArgs(f1.file, workingDir));
            assertNotNull(descs);
            // TODO create/use actual data and update the assert
        }
    }

}
