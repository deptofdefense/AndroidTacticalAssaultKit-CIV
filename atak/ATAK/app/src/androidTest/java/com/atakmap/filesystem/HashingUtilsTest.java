
package com.atakmap.filesystem;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class HashingUtilsTest extends ATAKInstrumentedTest {

    private final static String TAG = "FileSystemUtilsTest";

    private final static String TEST_STRING = "Test";
    private final static String TEST_STRING2 = "The quick brown fox jumps over the lazy dog";

    @Test
    public void FileSystemUtils_md5() {
        // due to a bug in the previous implementation, it would drop the leading zero from the md5 sum.
        assertEquals("md5", "cbc6611f5540bd0809a388dc95a615b",
                HashingUtils.md5sum(TEST_STRING));
        assertEquals("md5", "9e107d9d372bb6826bd81d3542a419d6",
                HashingUtils.md5sum(TEST_STRING2));
    }

    @Test
    public void FileSystemUtils_sha1() {
        assertEquals("sha1", "640ab2bae07bedc4c163f679a746f7ab7fb5d1fa",
                HashingUtils.sha1sum(TEST_STRING));
        assertEquals("sha1", "2fd4e1c67a2d28fced849ee1bb76e7391b93eb12",
                HashingUtils.sha1sum(TEST_STRING2));
    }

    @Test
    public void FileSystemUtils_sha256() {
        assertEquals("sha256",
                "532eaabd9574880dbf76b9b8cc00832c20a6ec113d682299550d7a6e0f345e25",
                HashingUtils.sha256sum(TEST_STRING));
        assertEquals("sha256",
                "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592",
                HashingUtils.sha256sum(TEST_STRING2));
    }
}
