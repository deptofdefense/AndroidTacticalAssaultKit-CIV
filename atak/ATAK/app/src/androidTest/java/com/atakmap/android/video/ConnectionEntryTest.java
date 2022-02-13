
package com.atakmap.android.video;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class ConnectionEntryTest extends ATAKInstrumentedTest {

    @Test
    public void copy() {
        ConnectionEntry ce = new ConnectionEntry();
        ConnectionEntry copy = ce.copy();
        Assert.assertNotEquals(ce.getUID(), copy.getUID());
    }

    @Test
    public void testCopy() {
        ConnectionEntry ce = new ConnectionEntry();
        ce.setUID("test-uid");
        ConnectionEntry copy = new ConnectionEntry();
        copy.copy(ce);
        Assert.assertEquals(ce.getUID(), copy.getUID());
    }

    @Test
    public void getIgnoreEmbeddedKLV() {
        ConnectionEntry ce = new ConnectionEntry();
        ce.setIgnoreEmbeddedKLV(true);
        Assert.assertTrue(ce.getIgnoreEmbeddedKLV());
        ce.setIgnoreEmbeddedKLV(false);
        Assert.assertFalse(ce.getIgnoreEmbeddedKLV());
    }

    @Test
    public void getRoverPort() {
        ConnectionEntry ce = new ConnectionEntry();
        ce.setRoverPort(5000);
        Assert.assertEquals(5000, ce.getRoverPort());
        ce.setRoverPort(5005);
        Assert.assertEquals(5005, ce.getRoverPort());
    }

    @Test
    public void getAlias() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        Assert.assertEquals("myalias", ce.getAlias());
        Assert.assertEquals("239.255.0.1", ce.getAddress());
        Assert.assertEquals(ConnectionEntry.Protocol.UDP, ce.getProtocol());
        Assert.assertEquals(2000, ce.getPort());

        ce.setAlias("myalias2");
        Assert.assertEquals("myalias2", ce.getAlias());
    }

    @Test
    public void getUID() {
        ConnectionEntry ce = new ConnectionEntry();
        ce.setUID("test-uid");
        Assert.assertEquals("test-uid", ce.getUID());
    }

    @Test
    public void getAddress() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        Assert.assertEquals("239.255.0.1", ce.getAddress());
        ce.setAddress("239.1.1.1");
        Assert.assertEquals("239.1.1.1", ce.getAddress());
    }

    @Test
    public void getMacAddress() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        ce.setMacAddress("01:22:AB:EC:AA");
        Assert.assertEquals("01:22:AB:EC:AA", ce.getMacAddress());
    }

    @Test
    public void getPort() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        Assert.assertEquals(2000, ce.getPort());
        ce.setPort(1234);
        Assert.assertEquals(1234, ce.getPort());
    }

    @Test
    public void getPath() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "rtsp://192.168.1.1:2000/mypath");
        Assert.assertEquals("/mypath", ce.getPath());
        ce.setPath("/mypath2");
        Assert.assertEquals("/mypath2", ce.getPath());

    }

    @Test
    public void getProtocol() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        Assert.assertEquals(ConnectionEntry.Protocol.UDP, ce.getProtocol());
        ce = new ConnectionEntry("myalias", "rtsp://239.255.0.1:2000/mypath");
        Assert.assertEquals(ConnectionEntry.Protocol.RTSP, ce.getProtocol());
        ce.setProtocol(ConnectionEntry.Protocol.UDP);
        Assert.assertEquals(ConnectionEntry.Protocol.UDP, ce.getProtocol());
    }

    @Test
    public void getNetworkTimeout() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        ce.setNetworkTimeout(10006);
        Assert.assertEquals(10006, ce.getNetworkTimeout());
    }

    @Test
    public void getBufferTime() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        ce.setBufferTime(10006);
        Assert.assertEquals(10006, ce.getBufferTime());
    }

    @Test
    public void getRtspReliable() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        ce.setRtspReliable(1);
        Assert.assertEquals(1, ce.getRtspReliable());
        ce.setRtspReliable(0);
        Assert.assertEquals(0, ce.getRtspReliable());
    }

    @Test
    public void getSource() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        Assert.assertEquals(ce.getSource(),
                ConnectionEntry.Source.LOCAL_STORAGE);
    }

    @Test
    public void isRemote() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        ce.setProtocol(ConnectionEntry.Protocol.HTTP);
        Assert.assertTrue(ce.isRemote());
        ce.setProtocol(ConnectionEntry.Protocol.FILE);
        Assert.assertFalse(ce.isRemote());
        ce.setProtocol(ConnectionEntry.Protocol.DIRECTORY);
        Assert.assertFalse(ce.isRemote());
    }

    @Test
    public void setLocalFile() {
        File f = new File("/scard/file");
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        ce.setLocalFile(f);
        Assert.assertEquals(f, ce.getLocalFile());
    }

    @Test
    public void setTemporary() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        ce.setTemporary(true);
        Assert.assertTrue(ce.isTemporary());
    }

    @Test
    public void getURL() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "udp://239.255.0.1:2000");
        Assert.assertEquals("239.255.0.1:2000", ConnectionEntry.getURL(ce));
    }

    @Test
    public void getRTSPReliableFromUri() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "rtsp://192.168.1.100:554/axis-media/media.amp?tcp");
        Assert.assertEquals(1, ce.getRtspReliable());
        ce = new ConnectionEntry("myalias",
                "rtsp://192.168.1.100:554/axis-media/media.amp");
        Assert.assertEquals(0, ce.getRtspReliable());
    }

    @Test
    public void getRTSPReliableFromUri2() {
        ConnectionEntry ce = new ConnectionEntry("myalias",
                "rtsp://192.168.1.100:554/axis-media/media.amp?joe&tcp");
        Assert.assertEquals(1, ce.getRtspReliable());
    }

    @Test
    public void getRtspGetUserPassFromUri1() {
        String[] userPassIp = ConnectionEntry.getUserPassIp(
                "username:password@192.168.1.100:554/axis-media/media.amp");
        Assert.assertEquals("username", userPassIp[0]);
        Assert.assertEquals("password", userPassIp[1]);
        Assert.assertEquals("192.168.1.100", userPassIp[2]);
    }

    @Test
    public void getRtspGetUserPassFromUri2() {

        String[] userPassIp = ConnectionEntry.getUserPassIp(
                "rtsp://username:@192.168.1.100:554/axis-media/media.amp");
        Assert.assertEquals("username", userPassIp[0]);
        Assert.assertEquals("", userPassIp[1]);
        Assert.assertEquals("192.168.1.100", userPassIp[2]);
    }

    @Test
    public void getRtspGetUserPassFromUri3() {
        String[] userPassIp = ConnectionEntry.getUserPassIp(
                "rtsp://@192.168.1.100:554/axis-media/media.amp");
        Assert.assertEquals("", userPassIp[0]);
        Assert.assertEquals("", userPassIp[1]);
        Assert.assertEquals("192.168.1.100", userPassIp[2]);

    }

    @Test
    public void getRtspGetUserPassFromUri4() {
        String[] userPassIp = ConnectionEntry.getUserPassIp(
                "rtsp://192.168.1.100:554/axis-media/media.amp");
        Assert.assertEquals("", userPassIp[0]);
        Assert.assertEquals("", userPassIp[1]);
        Assert.assertEquals("192.168.1.100", userPassIp[2]);

    }

    @Test
    public void getRtspGetUserPassFromUri5() {
        String[] userPassIp = ConnectionEntry.getUserPassIp(
                "rtsp://:@192.168.1.100:554/axis-media/media.amp");
        Assert.assertEquals("", userPassIp[0]);
        Assert.assertEquals("", userPassIp[1]);
        Assert.assertEquals("192.168.1.100", userPassIp[2]);

    }

    @Test
    public void connectionEntryFromBadUri() {
        ConnectionEntry ce = new ConnectionEntry("test",
                "gopher://eighties-4eva.net/video-stream");
        Assert.assertEquals(ce.getProtocol().toString(), "raw");
    }
}
