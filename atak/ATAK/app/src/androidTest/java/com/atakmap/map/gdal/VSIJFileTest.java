
package com.atakmap.map.gdal;

import static org.gdal.gdalconst.gdalconstConstants.GA_Update;
import static org.gdal.gdalconst.gdalconstConstants.GDT_Byte;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.androidtest.util.FileUtils;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.io.MockProvider;
import com.atakmap.map.gdal.MockVSIJFileFileSystemHandler.ExceptionalHandlerController;

import org.gdal.gdal.Band;
import org.gdal.gdal.Dataset;
import org.gdal.gdal.Driver;
import org.gdal.gdal.gdal;
import org.gdal.ogr.DataSource;
import org.gdal.ogr.Feature;
import org.gdal.ogr.Layer;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class VSIJFileTest extends ATAKInstrumentedTest {
    private static File cacheDir;

    private static final String vsi_prefix = "/vsijfile/";
    private static final String vsi_error = "/vsijfileerror/";

    private static final String kml_placemark = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            +
            "<kml xmlns=\"http://www.opengis.net/kml/2.2\" xmlns:gx=\"http://www.google.com/kml/ext/2.2\" xmlns:kml=\"http://www.opengis.net/kml/2.2\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n"
            +
            "<Document>\n" +
            "\t<name>placemark.kml</name>\n" +
            "\t<Style id=\"s_ylw-pushpin_hl\">\n" +
            "\t\t<IconStyle>\n" +
            "\t\t\t<scale>1.3</scale>\n" +
            "\t\t\t<Icon>\n" +
            "\t\t\t\t<href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href>\n"
            +
            "\t\t\t</Icon>\n" +
            "\t\t\t<hotSpot x=\"20\" y=\"2\" xunits=\"pixels\" yunits=\"pixels\"/>\n"
            +
            "\t\t</IconStyle>\n" +
            "\t</Style>\n" +
            "\t<Style id=\"s_ylw-pushpin\">\n" +
            "\t\t<IconStyle>\n" +
            "\t\t\t<scale>1.1</scale>\n" +
            "\t\t\t<Icon>\n" +
            "\t\t\t\t<href>http://maps.google.com/mapfiles/kml/pushpin/ylw-pushpin.png</href>\n"
            +
            "\t\t\t</Icon>\n" +
            "\t\t\t<hotSpot x=\"20\" y=\"2\" xunits=\"pixels\" yunits=\"pixels\"/>\n"
            +
            "\t\t</IconStyle>\n" +
            "\t</Style>\n" +
            "\t<StyleMap id=\"m_ylw-pushpin\">\n" +
            "\t\t<Pair>\n" +
            "\t\t\t<key>normal</key>\n" +
            "\t\t\t<styleUrl>#s_ylw-pushpin</styleUrl>\n" +
            "\t\t</Pair>\n" +
            "\t\t<Pair>\n" +
            "\t\t\t<key>highlight</key>\n" +
            "\t\t\t<styleUrl>#s_ylw-pushpin_hl</styleUrl>\n" +
            "\t\t</Pair>\n" +
            "\t</StyleMap>\n" +
            "\t<Placemark>\n" +
            "\t\t<name>Untitled Placemark</name>\n" +
            "\t\t<LookAt>\n" +
            "\t\t\t<longitude>-82.94645200672045</longitude>\n" +
            "\t\t\t<latitude>35.35709403386433</latitude>\n" +
            "\t\t\t<altitude>0</altitude>\n" +
            "\t\t\t<heading>1.309356404280996</heading>\n" +
            "\t\t\t<tilt>0</tilt>\n" +
            "\t\t\t<range>41178.76581780329</range>\n" +
            "\t\t\t<gx:altitudeMode>relativeToSeaFloor</gx:altitudeMode>\n" +
            "\t\t</LookAt>\n" +
            "\t\t<styleUrl>#m_ylw-pushpin</styleUrl>\n" +
            "\t\t<Point>\n" +
            "\t\t\t<gx:drawOrder>1</gx:drawOrder>\n" +
            "\t\t\t<coordinates>-82.94645200672045,35.35709403386433,0</coordinates>\n"
            +
            "\t\t</Point>\n" +
            "\t</Placemark>\n" +
            "</Document>\n" +
            "</kml>\n";

    private static final ExceptionalHandlerController controller = new ExceptionalHandlerController();

    @BeforeClass
    public static void setup() {
        Context testContext = InstrumentationRegistry.getInstrumentation()
                .getTargetContext();
        cacheDir = testContext.getCacheDir();

        File gdalDataDir = new File(cacheDir, "gdal");
        GdalLibrary.init(gdalDataDir);
        gdal.SetConfigOption("LIBKML_RESOLVE_STYLE", "yes");
        org.gdal.ogr.ogr.RegisterAll();

        VSIJFileFilesystemHandler handler = MockVSIJFileFileSystemHandler
                .createSuccessfulHandler(vsi_prefix);
        VSIJFileFilesystemHandler.installFilesystemHandler(handler);

        VSIJFileFilesystemHandler exHandler = MockVSIJFileFileSystemHandler
                .createExceptionalHandler(vsi_error, controller);
        VSIJFileFilesystemHandler.installFilesystemHandler(exHandler);
    }

    private static void createTestData(String filePath) {
        Driver driver = gdal.GetDriverByName("GTiff");
        Dataset ds = driver.Create(filePath, 10, 10, 1, GDT_Byte);
        if (ds == null) {
            return;
        }
        byte[] data = new byte[100];
        for (byte i = 0; i < data.length; i++) {
            data[i] = i;
        }
        ds.GetRasterBand(1).WriteRaster(0, 0, 10, 10, data);
        ds.delete();
    }

    @Test
    public void testHappyPath() {
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(testFilename);

            if (doFilesDiffer(vsi_prefix, testFilename)) {
                fail();
            }
        }
    }

    /**
     * Will open given filepath both with plain GDAL and with the vsi interface.
     * Fails test with fail() if files don't match through the two interfaces.
     *
     * @param testFilename path of file to test
     */
    private boolean doFilesDiffer(String prefixToTest, String testFilename) {
        Dataset vsiData = gdal.Open(prefixToTest + testFilename);
        if (vsiData == null) {
            return true;
        }
        Band vsiBand = vsiData.GetRasterBand(1);
        byte[] vsiArr = new byte[100];
        vsiBand.ReadRaster(0, 0, 10, 10, vsiArr);
        vsiData.delete();

        Dataset regularData = gdal.Open(testFilename);
        if (regularData == null) {
            return true;
        }
        Band regBand = regularData.GetRasterBand(1);
        byte[] regArr = new byte[100];
        regBand.ReadRaster(0, 0, 10, 10, regArr);
        regularData.delete();

        for (int i = 0; i < regArr.length; i++) {
            if (vsiArr[i] != regArr[i] || vsiArr[i] != i) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testNonExistentFile() {
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(testFilename);

            Dataset data = gdal
                    .Open(vsi_prefix + testFilename + "_nonexistent");
            assertNull(data);
        }
    }

    @Test
    public void testInvalidVSIPath() {
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(testFilename);

            Dataset data = gdal.Open("/vsijfle" + testFilename);
            assertNull(data);
        }
    }

    @Test
    public void testWritingWithInterface() {
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_prefix + testFilename);

            if (doFilesDiffer(vsi_prefix, testFilename)) {
                fail();
            }
        }
    }

    @Test
    public void testUpdateFileMode() {
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_prefix + testFilename);

            if (doFilesDiffer(vsi_prefix, testFilename)) {
                fail();
            }

            Dataset ds = gdal.Open(vsi_prefix + testFilename, GA_Update);

            byte[] newData = new byte[100];
            Arrays.fill(newData, (byte) 1);
            ds.GetRasterBand(1).WriteRaster(0, 0, 10, 10, newData);
            ds.delete(); // call delete method so updated dataset is written to disk

            ds = gdal.Open(vsi_prefix + testFilename);
            byte[] readData = new byte[100];
            ds.GetRasterBand(1).ReadRaster(0, 0, 10, 10, readData);
            ds.delete();

            for (int i = 0; i < 100; i++) {
                if (newData[i] != readData[i]) {
                    fail("data doesn't match");
                }
            }
        }
    }

    @Test
    public void testJavaExceptionOpen() {
        controller.resetAll();
        controller.setOpenMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNull(data);
        }
    }

    @Test
    public void testJavaExceptionStat() {
        controller.resetAll();
        controller.setStatMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we expect it to work even if stat throws an exception
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when stat throws an exception
        }
    }

    @Test
    public void testJavaExceptionRead1() {
        controller.resetAll();
        controller.setRead1Mask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (!doFilesDiffer(vsi_error, testFilename)) {
                // the files should differ because we can't read files
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNull(data);
        }
    }

    @Test
    public void testJavaExceptionRead2() {
        controller.resetAll();
        controller.setRead2Mask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // read with 2 arguments isn't used by our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when read (with 2 arguments) throws an exception
        }
    }

    @Test
    public void testJavaExceptionRead3() {
        controller.resetAll();
        controller.setRead3Mask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // read with 3 arguments isn't used by our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when read (with 3 arguments) throws an exception
        }
    }

    @Test
    public void testJavaExceptionWrite1() {
        controller.resetAll();
        controller.setWrite1Mask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (!doFilesDiffer(vsi_error, testFilename)) {
                // the files should differ since we can't write them in the first place
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNull(data);
        }
    }

    @Test
    public void testJavaExceptionWrite2() {
        controller.resetAll();
        controller.setWrite2Mask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we don't use write with 2 parameters in our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when write (with 2 parameters) throws an exception
        }
    }

    @Test
    public void testJavaExceptionWrite3() {
        controller.resetAll();
        controller.setWrite3Mask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we don't use write with 3 parameters in our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when write (with 3 parameters) throws an exception
        }
    }

    @Test
    public void testJavaExceptionPosition0() {
        controller.resetAll();
        controller.setPosition0Mask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (!doFilesDiffer(vsi_error, testFilename)) {
                // we expect files to differ because gdal requires Tell to work
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNull(data);
        }
    }

    @Test
    public void testJavaExceptionPosition1() {
        controller.resetAll();
        controller.setPosition1Mask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (!doFilesDiffer(vsi_error, testFilename)) {
                // we expect files to differ because gdal requires Seek to work
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNull(data);
        }
    }

    @Test
    public void testJavaExceptionSize() {
        controller.resetAll();
        controller.setSizeMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (!doFilesDiffer(vsi_error, testFilename)) {
                // we expect files to differ because gdal requires size for Seek
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNull(data);
        }
    }

    @Test
    public void testJavaExceptionTruncate() {
        controller.resetAll();
        controller.setTruncateMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we don't use truncate in our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when truncate throws an exception
        }
    }

    @Test
    public void testJavaExceptionForce() {
        controller.resetAll();
        controller.setForceMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we don't use force in our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when force throws an exception
        }
    }

    @Test
    public void testJavaExceptionTransferTo() {
        controller.resetAll();
        controller.setTransfertoMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we don't use transferTo in our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when transferTo throws an exception
        }
    }

    @Test
    public void testJavaExceptionTransferFrom() {
        controller.resetAll();
        controller.setTransferfromMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we don't use transferFrom in our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when transferFrom throws an exception
        }
    }

    @Test
    public void testJavaExceptionMap() {
        controller.resetAll();
        controller.setMapMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we don't use map in our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when map throws an exception
        }
    }

    @Test
    public void testJavaExceptionLock() {
        controller.resetAll();
        controller.setLockMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we don't use lock in our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when lock throws an exception
        }
    }

    @Test
    public void testJavaExceptionTryLock() {
        controller.resetAll();
        controller.setTryLockMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // we don't use tryLock in our code
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when tryLock throws an exception
        }
    }

    @Test
    public void testJavaExceptionImplCloseChannel() {
        controller.resetAll();
        controller.setImplclosechannelMask();
        try (FileUtils.AutoDeleteFile testFile = FileUtils.AutoDeleteFile
                .createTempFile("-test.tiff")) {
            String testFilename = testFile.getPath();
            createTestData(vsi_error + testFilename);
            if (doFilesDiffer(vsi_error, testFilename)) {
                // it still works when close throws an exception
                fail();
            }

            Dataset data = gdal.Open(vsi_error + testFilename);
            assertNotNull(data); // it works even when implCloseChannel throws an exception
        }
    }

    /*********************************************************************************************/

    @Test
    public void test_kml_no_vsi() throws IOException {
        try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                .createTempFile(".kml")) {
            // generate KML placemark file
            try (FileWriter writer = IOProviderFactory.getFileWriter(f.file)) {
                writer.write(kml_placemark);
            }

            DataSource ds = null;
            try {
                String path = f.getPath();
                if (!IOProviderFactory.isDefault())
                    path = VSIFileFileSystemHandler.PREFIX + path;
                ds = org.gdal.ogr.ogr.Open(path);
                assertNotNull(ds);
                int numLayers = ds.GetLayerCount();
                for (int i = 0; i < numLayers; i++) {
                    Layer layer = ds.GetLayer(i);
                    assertNotNull(layer);
                    long numFeatures = layer.GetFeatureCount();
                    long iteratedFeatures = 0L;
                    layer.ResetReading();
                    do {
                        Feature feature = layer.GetNextFeature();
                        if (feature == null)
                            break;
                        iteratedFeatures++;
                    } while (true);
                    assertEquals(numFeatures, iteratedFeatures);
                }
            } finally {
                if (ds != null)
                    ds.delete();
            }
        }
    }

    @Test
    public void test_kml_custom_provider() throws IOException {
        //final IOProvider provider = new MockFileIOProvider();
        final IOProvider provider = new MockProvider("mock",
                new File("/sdcard/vsimock"));
        IOProviderFactory.registerProvider(provider);
        try {
            test_kml_no_vsi();
        } finally {
        }
    }

    /*********************************************************************************************/
}
