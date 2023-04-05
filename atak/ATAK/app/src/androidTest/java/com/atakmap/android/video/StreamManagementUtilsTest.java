
package com.atakmap.android.video;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Test;
import static org.junit.Assert.*;

public class StreamManagementUtilsTest extends ATAKInstrumentedTest {

    @Test
    public void testUdp() {
        final ConnectionEntry ce = StreamManagementUtils
                .createConnectionEntryFromUrl("test1", "udp://239.1.1.1:4001");
        assertNotNull(ce);
        assertEquals(ConnectionEntry.Protocol.UDP, ce.getProtocol());
        assertEquals(4001, ce.getPort());
        assertEquals("test1", ce.getAlias());
        assertEquals("239.1.1.1", ce.getAddress());
    }

    @Test
    public void testUdpWithoutPort() {
        final ConnectionEntry ce = StreamManagementUtils
                .createConnectionEntryFromUrl("test1", "udp://239.1.1.1");
        assertNotNull(ce);
        assertEquals(ConnectionEntry.Protocol.UDP, ce.getProtocol());
        assertEquals(1234, ce.getPort());
        assertEquals("test1", ce.getAlias());
        assertEquals("239.1.1.1", ce.getAddress());
    }

    @Test
    public void testUrlEmpty() {
        final ConnectionEntry ce = StreamManagementUtils
                .createConnectionEntryFromUrl("test1", "");
        assertNull(ce);
    }

    @Test
    public void testRtspWithoutPort() {
        final ConnectionEntry ce = StreamManagementUtils
                .createConnectionEntryFromUrl("test1",
                        "rtsp://192.168.1.1:3001/pathname");
        assertNotNull(ce);
        assertEquals(ConnectionEntry.Protocol.RTSP, ce.getProtocol());
        assertEquals(3001, ce.getPort());
        assertEquals("test1", ce.getAlias());
        assertEquals("192.168.1.1", ce.getAddress());
        assertEquals("/pathname", ce.getPath());
    }

    @Test
    public void deepTestUdp() {
        String[] examples = new String[] {
                "udp://231.1.1.1:3500",
                "udp://231.1.1.1:3500/",
                "udp://@231.1.1.1:3500",
                // not technically correct but a robust parser should be able to make these work
                //"udp:/231.1.1.1:3500",
                //"udp:////231.1.1.1:3500",
                "udp://231.1.1.1:3500//",
                //"udp://231.1.1.1:3500:",
                //"udp://231.1.1.1:3500:/"
        };

        for (String s : examples) {
            final ConnectionEntry ce = StreamManagementUtils
                    .createConnectionEntryFromUrl("test1", s);
            assertNotNull(ce);
            assertEquals("Test: " + s, ConnectionEntry.Protocol.UDP,
                    ce.getProtocol());
            assertEquals("Test: " + s, 3500, ce.getPort());
            assertEquals("Test: " + s, "test1", ce.getAlias());
            assertEquals("Test: " + s, "231.1.1.1", ce.getAddress());
        }

    }

    @Test
    public void deepTestUdp2() {
        String[] examples = new String[] {
                "udp://231.1.1.1/",
                "udp://231.1.1.1:/"
        };
        for (String s : examples) {
            final ConnectionEntry ce = StreamManagementUtils
                    .createConnectionEntryFromUrl("test1", s);
            assertNotNull(ce);
            assertEquals("Test: " + s, ConnectionEntry.Protocol.UDP,
                    ce.getProtocol());
            assertEquals("Test: " + s, 1234, ce.getPort());
            assertEquals("Test: " + s, "test1", ce.getAlias());
            assertEquals("Test: " + s, "231.1.1.1", ce.getAddress());
        }
    }

    @Test
    public void deepTestRtsp() {
        String[] examples = new String[] {
                "rtsp://192.168.1.1:8500/some/path-to/file",
                "rtsp://192.168.1.1:8500/some/path-to/file?quality=high&source=local",
                "rtsp://192.168.1.1/some/path-to/file",
                //"rtsp://192.168.1.1?",
                "rtsp://192.168.1.1:?",
                //"rtsp://user:pass@192.168.1.1/some/path-to/file"
        };

        for (String s : examples) {
            final ConnectionEntry ce = StreamManagementUtils
                    .createConnectionEntryFromUrl("test1", s);
            assertNotNull(ce);
            assertEquals("Test: " + s, ConnectionEntry.Protocol.RTSP,
                    ce.getProtocol());
            assertEquals("Test: " + s, "192.168.1.1", ce.getAddress());
        }
    }

    @Test
    public void testRtsp() {
        ConnectionEntry ce1 = StreamManagementUtils
                .createConnectionEntryFromUrl("big buck bunny",
                        "rtsp://3.84.6.190/vod/mp4:BigBuckBunny_115k.mov");

        ConnectionEntry ce2 = StreamManagementUtils
                .createConnectionEntryFromUrl("big buck bunny",
                        "rtsp://3.84.6.190:554/vod/mp4:BigBuckBunny_115k.mov");
        assertNotNull(ce2);
        assertNotNull(ce1);
        assertEquals("test missing port", ce1, ce2);
        assertEquals("test port", 554, ce2.getPort());
        assertEquals("test address", "3.84.6.190", ce1.getAddress());
        assertEquals("test path", "/vod/mp4:BigBuckBunny_115k.mov",
                ce1.getPath());

    }

    @Test
    public void testHttp() {
        ConnectionEntry ce1 = StreamManagementUtils
                .createConnectionEntryFromUrl("name",
                        "http://192.168.1.1:8081");
        assertNotNull(ce1);
        assertEquals("http:address", "192.168.1.1", ce1.getAddress());
        assertEquals("http:port", 8081, ce1.getPort());
        assertEquals("http:path", "", ce1.getPath());
    }

    @Test
    public void testHttpWithUsernameAndPassword() {
        ConnectionEntry ce1 = StreamManagementUtils
                .createConnectionEntryFromUrl("name",
                        "http://username:password@192.168.1.1:8081/test.mjpg");
        assertNotNull(ce1);
        assertEquals("http:address", "username:password@192.168.1.1",
                ce1.getAddress());
        assertEquals("http:port", 8081, ce1.getPort());
        assertEquals("http:path", "/test.mjpg", ce1.getPath());
    }

    @Test
    public void testRtmpWithToken() {
        ConnectionEntry ce = StreamManagementUtils.createConnectionEntryFromUrl(
                "name",
                "rtmp://demo.unifiedvideo.com/live/4?token=e02532a591f14f16a233521ab907a150");

        assertNotNull(ce);
        assertEquals(ce.getProtocol(), ConnectionEntry.Protocol.RTMP);
        assertEquals(
                "rtmp://demo.unifiedvideo.com:1935/live/4?token=e02532a591f14f16a233521ab907a150&timeout=5",
                ConnectionEntry.getURL(ce));
    }

    @Test
    public void testUrlWithQuery() {
        ConnectionEntry ce = StreamManagementUtils.createConnectionEntryFromUrl(
                "name",
                "rtmp://demo.unifiedvideo.com/?token=e02532a591f14f16a233521ab907a150&token2=shb");
        assertNotNull(ce);
        assertEquals(ce.getProtocol(), ConnectionEntry.Protocol.RTMP);
        assertEquals(
                "rtmp://demo.unifiedvideo.com:1935/?token=e02532a591f14f16a233521ab907a150&token2=shb&timeout=5",
                ConnectionEntry.getURL(ce));
    }

    @Test
    public void testSrtExample() {
        ConnectionEntry ce = StreamManagementUtils.createConnectionEntryFromUrl(
                "srt",
                "srt://192.168.1.1:2000?parameter1=value1&parameter2=value2");
        assertNotNull(ce);
        assertEquals("192.168.1.1", ce.getAddress());
        assertEquals(2000, ce.getPort());
        assertEquals("srt", ce.getProtocol().toString());
        assertEquals(ce.getPath(), "?parameter1=value1&parameter2=value2");
    }
}
