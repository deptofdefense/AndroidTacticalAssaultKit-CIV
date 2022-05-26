
package com.atakmap.comms;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NetConnectStringTest extends ATAKInstrumentedTest {

    @org.junit.Test
    public void getProto() {
        final NetConnectString ncs = new NetConnectString("udp",
                "192.168.130.1", 3456);
        assertEquals("udp", ncs.getProto());

    }

    @org.junit.Test
    public void getHost() {
        final NetConnectString ncs = new NetConnectString("udp",
                "192.168.130.1", 3456);
        assertEquals("192.168.130.1", ncs.getHost());
    }

    @org.junit.Test
    public void getPort() {
        final NetConnectString ncs = new NetConnectString("udp",
                "192.168.130.1", 3456);
        assertEquals(3456, ncs.getPort());
    }

    @org.junit.Test
    public void getCallsign() {
        final NetConnectString ncs = new NetConnectString("udp",
                "192.168.130.1", 3456);
        ncs.setCallsign("testCallsign");
        assertEquals("testCallsign", ncs.getCallsign());
    }

    @org.junit.Test
    public void setCallsign() {
        final NetConnectString ncs = new NetConnectString("udp",
                "192.168.130.1", 3456);
        ncs.setCallsign("testCallsign");
        assertEquals("testCallsign", ncs.getCallsign());
    }

    @org.junit.Test
    public void fromString() {
        final NetConnectString ncs = NetConnectString
                .fromString("udp://192.168.130.1:3456");
        final NetConnectString cmp = new NetConnectString("udp",
                "192.168.130.1", 3456);
        assertEquals(ncs, cmp);
    }

    @org.junit.Test
    public void matches() {
        final NetConnectString ncs = NetConnectString
                .fromString("udp://192.168.130.1:3456");
        assertTrue(ncs.matches("udp", "192.168.130.1", 3456));
    }
}
