
package com.atakmap.map.layer.feature;

import static org.junit.Assert.assertTrue;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.android.androidtest.util.FileUtils;
import com.atakmap.coremap.io.DatabaseInformation;
import com.atakmap.coremap.io.IOProvider;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.io.MockProvider;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.Databases;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class PersistentDataSourceFeatureDataStoreTest
        extends ATAKInstrumentedTest {
    @Test
    public void constructor_invokes_ioprovider() throws IOException {
        final boolean[] invoked = {
                false
        };
        final IOProvider provider = new MockProvider("provider",
                new File("/")) {
            @Override
            public DatabaseIface createDatabase(DatabaseInformation info) {
                invoked[0] = true;
                return Databases.openOrCreateDatabase(info.uri.getPath());
            }
        };
        IOProviderFactory.registerProvider(provider);
        try {
            try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                    .createTempFile()) {
                f.file.delete();
                f.file.mkdirs();
                PersistentDataSourceFeatureDataStore2 fds = new PersistentDataSourceFeatureDataStore2(
                        f.file);
                assertTrue(invoked[0]);
                fds.dispose();
            }
        } finally {
        }
    }

    @Test
    public void custom_io_reopen() throws IOException {
        final boolean[] invoked = {
                false
        };
        final IOProvider provider = new MockProvider("provider",
                new File("/")) {
            @Override
            public DatabaseIface createDatabase(DatabaseInformation info) {
                invoked[0] = true;
                return Databases.openOrCreateDatabase(info.uri.getPath());
            }
        };
        IOProviderFactory.registerProvider(provider);
        try {
            try (FileUtils.AutoDeleteFile f = FileUtils.AutoDeleteFile
                    .createTempFile()) {
                f.file.delete();
                f.file.mkdirs();

                PersistentDataSourceFeatureDataStore2 fds;
                fds = new PersistentDataSourceFeatureDataStore2(f.file);
                assertTrue(invoked[0]);
                fds.dispose();

                // reopen
                invoked[0] = false;
                fds = new PersistentDataSourceFeatureDataStore2(f.file);
                assertTrue(invoked[0]);
                fds.dispose();
            }
        } finally {
        }
    }
}
