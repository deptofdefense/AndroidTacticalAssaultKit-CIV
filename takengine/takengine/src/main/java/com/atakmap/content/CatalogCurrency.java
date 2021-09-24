
package com.atakmap.content;

import java.io.File;

public interface CatalogCurrency {
    String getName();

    int getAppVersion();

    byte[] getAppData(File file);

    boolean isValidApp(File f, int appVersion, byte[] appData);
}
