
package com.atakmap.database;

import gov.tak.api.annotation.DontObfuscate;

@DontObfuscate
public interface RowIterator extends AutoCloseable {
    public boolean moveToNext();

    public void close();

    public boolean isClosed();
}
