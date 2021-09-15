
package com.atakmap.net;

import javax.net.ssl.KeyManager;

public interface KeyManagerFactoryIFace {
    KeyManager[] getKeyManagers(String Server);
}
