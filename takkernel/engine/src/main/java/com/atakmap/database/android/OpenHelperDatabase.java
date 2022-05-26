package com.atakmap.database.android;

import android.database.sqlite.SQLiteException;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.DatabaseWrapper;

import java.io.File;

final class OpenHelperDatabase extends DatabaseWrapper {
    File file;
    boolean open;

    OpenHelperDatabase(File file, DatabaseIface impl) {
        super(impl);
        this.open = true;
    }

    public void reopenReadWrite() {
        this.filter = IOProviderFactory.createDatabase((File)null);
        if(this.filter == null)
            throw new SQLiteException("Failed to open database");
        this.open = true;
    }
    public boolean isOpen() {
        return this.open;
    }

    @Override
    public void close() {
        if(this.open) {
            super.close();
            this.open = false;
        }
    }
}
