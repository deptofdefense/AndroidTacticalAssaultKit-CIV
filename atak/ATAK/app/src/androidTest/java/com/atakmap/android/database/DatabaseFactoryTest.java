
package com.atakmap.android.database;

import android.net.Uri;
import androidx.annotation.NonNull;

import androidx.test.runner.AndroidJUnit4;

import com.atakmap.android.androidtest.ATAKInstrumentedTest;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
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
        public DatabaseIface create(DatabaseInformation object) {
            assertTrue(object.getUri(dp).toString()
                    .contains("androidTesttest.sqlite"));
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

    @Test
    public void registerProvider() {
        DatabaseFactory.registerProvider(dp);

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
        try {
            DatabaseFactory.registerProvider(dp);
        } catch (Exception ignore) {
        }
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
}
