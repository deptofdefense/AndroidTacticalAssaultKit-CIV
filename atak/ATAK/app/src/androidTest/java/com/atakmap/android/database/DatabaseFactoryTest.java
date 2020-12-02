
package com.atakmap.android.database;

import android.net.Uri;
import androidx.annotation.NonNull;

import androidx.test.runner.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.coremap.io.FileIOProviderFactory;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class DatabaseFactoryTest extends ATAKInstrumentedTest {

    boolean unregistered = false;
    DatabaseProvider dp = new DatabaseProvider() {
        @NonNull
        @Override
        public String getPrefix() {
            return "androidTest";
        }

        @Override
        public boolean isDatabase(String path) { return true; }

        @Override
        public DatabaseIface create(DatabaseInformation object) {
            assertTrue(object.getUri().toString()
                    .contains("/sdcard/test.sqlite"));
            return new DatabaseIface() {
                @Override
                public void execute(String sql, String[] args) {
                }

                @Override
                public CursorIface query(String sql, String[] args) {
                    return null;
                }

                @Override
                public StatementIface compileStatement(String sql) {
                    return null;
                }

                @Override
                public QueryIface compileQuery(String sql) {
                    return null;
                }

                @Override
                public boolean isReadOnly() {
                    return false;
                }

                @Override
                public void close() {

                }

                @Override
                public int getVersion() {
                    return 0;
                }

                @Override
                public void setVersion(int version) {

                }

                @Override
                public void beginTransaction() {

                }

                @Override
                public void setTransactionSuccessful() {

                }

                @Override
                public void endTransaction() {

                }

                @Override
                public boolean inTransaction() {
                    return false;
                }
            };
        }
    };

    /**
     * Method called at the beginning of each test
     */
    @Before
    public void setUp() {
        DatabaseFactory.registerProvider(dp);
    }

    /**
     * Method called after each test finishes
     */
    @After
    public void tearDown() {
        DatabaseFactory.unregisterProvider(dp);
    }

    @Test
    public void registerProvider() {

        DatabaseIface di = DatabaseFactory.create(
                new DatabaseInformation(
                        Uri.fromFile(new File("/sdcard/test.sqlite")),
                        new ProviderChangeRequestedListener() {
                            @Override
                            public void onProviderChangeRequested(
                                    DatabaseProvider provider, int change) {
                            }
                        }));

    }

    @Test
    public void unregisterProvider() {

        DatabaseIface di = DatabaseFactory.create(
                new DatabaseInformation(
                        Uri.fromFile(new File("/sdcard/test.sqlite")),
                        new ProviderChangeRequestedListener() {
                            @Override
                            public void onProviderChangeRequested(
                                    DatabaseProvider provider, int change) {
                                unregistered = true;
                            }
                        }));
        DatabaseFactory.unregisterProvider(dp);
        assertTrue(unregistered);
    }

    /**
     * Tests database check for the most recently added provider
     */
    @Test
    public void isDatabase() {
        class IsDatabaseTestProvider implements DatabaseProvider {
            public boolean isDatabaseInvoked = false;
            public String isDatabasePath = null;
            public final boolean isDatabaseResult;
            public IsDatabaseTestProvider(boolean result) { this.isDatabaseResult = result; }
            public String getPrefix() { return "isdatabase"; }
            public boolean isDatabase(String path) {
                isDatabaseInvoked = true;
                isDatabasePath = path;
                return isDatabaseResult;
            }

            @Override
            public DatabaseIface create(DatabaseInformation object)
            {
                return null;
            }
        };

        final IsDatabaseTestProvider success = new IsDatabaseTestProvider(true);
        final IsDatabaseTestProvider fail = new IsDatabaseTestProvider(false);
        File sqlFile = null;

        // provide coverage over the successful `isDatabase` path
        DatabaseFactory.registerProvider(success);
        try
        {
            sqlFile = createTestFile();
            final String path = sqlFile.getPath();
            boolean isdb = DatabaseFactory.isDatabase(sqlFile.getPath());
            // ensure that our provider was invoked
            assertTrue(success.isDatabaseInvoked);
            // ensure that the path roundtripped
            assertEquals(path, success.isDatabasePath);
            // ensure that the return value roundtripped
            assertEquals(success.isDatabaseResult, isdb);
        } catch (IOException ignored) {
        } finally {
            DatabaseFactory.unregisterProvider(success);
            if (FileIOProviderFactory.exists(sqlFile)) {
                try {
                    sqlFile.delete();
                } catch (Exception ignored) {
                }
            }
        }

        // provide coverage over the failure `isDatabase` path
        DatabaseFactory.registerProvider(fail);
        try {
            final String path = "/path/to/invalid/database";
            boolean isdb = DatabaseFactory.isDatabase(path);
            // ensure that our provider was invoked
            assertTrue(fail.isDatabaseInvoked);
            // ensure that the path roundtripped
            assertEquals(path, fail.isDatabasePath);
            // ensure that the return value roundtripped
            assertEquals(fail.isDatabaseResult, isdb);
        } finally {
            DatabaseFactory.unregisterProvider(fail);
        }
    }

    /**
     * Creates an empty SQLite test file
     *
     * @return A created file
     */
    private File createTestFile() throws IOException {
        File f = new File("/sdcard/test.sqlite");
        FileOutputStream fos = FileIOProviderFactory.getOutputStream(f);
        fos.close();
        return f;
    }
}
