
package com.atakmap.map.gdal;

import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.coremap.io.DefaultIOProvider;
import com.atakmap.coremap.io.IOProviderFactory;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;

public class VSIFileFileSystemHandlerTest extends ATAKInstrumentedTest {
    @BeforeClass
    public static void init() {
        Context testContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        final File cacheDir = testContext.getCacheDir();

        File gdalDataDir = new File(cacheDir, "gdal");
        GdalLibrary.init(gdalDataDir);
    }

    @Test
    public void testRoundtripRelativePath() {
        final DebugFileIOProvider dbgprovider = new DebugFileIOProvider(
                new DefaultIOProvider());
        IOProviderFactory.registerProvider(dbgprovider);
        try {
            final String relativePath = "relative/path/to/file.dat";
            org.gdal.gdal.gdal
                    .Open(VSIFileFileSystemHandler.PREFIX + relativePath);

            ArrayList<DebugFileIOProvider.InvocationDebugRecord> records = new ArrayList<>();
            dbgprovider.getInvocationRecord(records);
            final DebugFileIOProvider.InvocationDebugRecord expectedRecord = new DebugFileIOProvider.InvocationDebugRecord();
            expectedRecord.methodName = "getChannel";
            expectedRecord.parameters.put("f", new File(relativePath));
            expectedRecord.parameters.put("mode", "r");

            assertTrue(records.contains(expectedRecord));
        } finally {
        }
    }

    @Test
    public void testRoundtripAbsolutePath() {
        final DebugFileIOProvider dbgprovider = new DebugFileIOProvider(
                new DefaultIOProvider());
        IOProviderFactory.registerProvider(dbgprovider);
        try {
            final String absolutePath = "/absolute/path/to/file.dat";
            org.gdal.gdal.gdal
                    .Open(VSIFileFileSystemHandler.PREFIX + absolutePath);

            ArrayList<DebugFileIOProvider.InvocationDebugRecord> records = new ArrayList<>();
            dbgprovider.getInvocationRecord(records);

            final DebugFileIOProvider.InvocationDebugRecord expectedRecord = new DebugFileIOProvider.InvocationDebugRecord();
            expectedRecord.methodName = "getChannel";
            expectedRecord.parameters.put("f", new File(absolutePath));
            expectedRecord.parameters.put("mode", "r");

            assertTrue(records.contains(expectedRecord));
        } finally {
        }
    }
}
