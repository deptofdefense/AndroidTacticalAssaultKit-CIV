
package com.atakmap.net;

import com.atakmap.comms.NetConnectString;

import javax.net.ssl.KeyManager;

public interface KeyManagerFactoryIFace {
    KeyManager[] getKeyManagers(String Server);
    KeyManager[] getKeyManagers(NetConnectString netConnectString);
}
