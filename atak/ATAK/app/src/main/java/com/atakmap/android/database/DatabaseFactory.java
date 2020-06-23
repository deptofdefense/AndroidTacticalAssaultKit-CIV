
package com.atakmap.android.database;

import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;

import java.util.ArrayList;
import java.util.List;

import com.atakmap.coremap.log.Log;

public class DatabaseFactory {

    private static final String TAG = "DatabaseFactory";

    private static final List<ProviderChangeRequestedListener> providerChangeRequestedListeners = new ArrayList<>();

    private static final List<DatabaseProvider> databaseProviders = new ArrayList<>();

    private static final DatabaseProvider DEFAULT = new DefaultDatabaseProvider();

    static {
        databaseProviders.add(DEFAULT);
    }

    private static boolean destroying = false;

    /**
     * Will register the Spi as the new default DatabaseProvider SPI for the system and
     * it is responsible for notifying all of the current users that the SPI they are
     * using is no longer the default.  If this SPI exists but it is not the default,
     * then it will be made the default and the notification process will occur.
     *
     * This allows them to make the decision to continue to use the existing SPI or
     * close the database and make use of the new SPI.
     *
     * @param databaseProvider is the provider that will be made the default for the system
     * even if it previously was registered.
     *
     * @throws IllegalStateException if the database prefix is the same as another previously registered
     * database prefix.
     */
    public static synchronized void registerProvider(
            final DatabaseProvider databaseProvider) {
        if (destroying)
            return;

        databaseProviders.remove(databaseProvider);

        for (DatabaseProvider existingProvider : databaseProviders) {
            if (databaseProvider.getPrefix()
                    .equals(existingProvider.getPrefix())) {
                throw new IllegalStateException(
                        "cannot use duplicative prefixes for the database provider= '"
                                + existingProvider.getPrefix() + "'");
            }
        }

        databaseProviders.add(0, databaseProvider);

        final List<ProviderChangeRequestedListener> pcrlList = new ArrayList<>(
                providerChangeRequestedListeners);

        for (ProviderChangeRequestedListener pcrl : pcrlList) {
            try {
                pcrl.onProviderChangeRequested(databaseProvider,
                        ProviderChangeRequestedListener.NEW_DEFAULT);
            } catch (Exception e) {
                Log.e(TAG,
                        "severe error occured registering: " + databaseProvider,
                        e);
            }
        }

    }

    /**
     * Will unregister the databaseProvider and make sure that all open ProductChangeRequestedListeners
     * for open DatabaseIface objects are notified.
     * @param databaseProvider the provider that will be unregistered.
     *
     */
    public static synchronized void unregisterProvider(
            final DatabaseProvider databaseProvider) {

        if (destroying)
            return;

        databaseProviders.remove(databaseProvider);

        final List<ProviderChangeRequestedListener> pcrlList = new ArrayList<>(
                providerChangeRequestedListeners);
        for (ProviderChangeRequestedListener pcrl : pcrlList)
            try {
                pcrl.onProviderChangeRequested(databaseProvider,
                        ProviderChangeRequestedListener.REMOVED);
            } catch (Exception e) {
                Log.e(TAG, "severe error occured unregistering: "
                        + databaseProvider, e);
            }
    }

    /**
     * Creates a DatabaseIface from provided DatabaseInformation.
     *
     * @param dbaseInformation the key information required for creating DatabaseIface object.
     *                         The ProviderChangeRequestedListener must be implemented for a
     *                         DatabaseIface to be returned so that the user of the provided
     *                         database interface can do the right things when the database
     *                         provider no longer is available.
     *
     * @return  An instance of the DatabaseIface if successfuly,
     *          <code>null</code> otherwise.
     */
    public static synchronized DatabaseIface create(
            final DatabaseInformation dbaseInformation) {
        if (dbaseInformation.pcrl == null)
            return null;

        for (DatabaseProvider dp : databaseProviders) {
            DatabaseIface iface = dp.create(dbaseInformation);
            if (iface != null) {
                providerChangeRequestedListeners.add(dbaseInformation.pcrl);
                return new DatabaseIfaceWrapper(iface, dbaseInformation.pcrl);
            }
        }
        return null;

    }

    /**
     * If the DatabaseFactory has been notified that the Application is closing, do not
     * notify users of the factory that any providers have changed.     Notification of provider
     * changes should only happen when the provider is loaded or unloaded.
     */
    public static synchronized void notifyOnDestroy() {
        destroying = true;
    }

    private static class DatabaseIfaceWrapper implements DatabaseIface {
        final DatabaseIface impl;
        final ProviderChangeRequestedListener pcrl;

        DatabaseIfaceWrapper(final DatabaseIface impl,
                final ProviderChangeRequestedListener pcrl) {
            this.impl = impl;
            this.pcrl = pcrl;
        }

        @Override
        public void execute(final String sql, final String[] args) {
            impl.execute(sql, args);
        }

        @Override
        public CursorIface query(final String sql, final String[] args) {
            return impl.query(sql, args);
        }

        @Override
        public StatementIface compileStatement(final String sql) {
            return impl.compileStatement(sql);
        }

        @Override
        public QueryIface compileQuery(final String sql) {
            return impl.compileQuery(sql);
        }

        @Override
        public boolean isReadOnly() {
            return impl.isReadOnly();
        }

        @Override
        public void close() {
            impl.close();
            providerChangeRequestedListeners.remove(pcrl);
        }

        @Override
        public int getVersion() {
            return impl.getVersion();
        }

        @Override
        public void setVersion(final int version) {
            impl.setVersion(version);
        }

        @Override
        public void beginTransaction() {
            impl.beginTransaction();
        }

        @Override
        public void setTransactionSuccessful() {
            impl.setTransactionSuccessful();

        }

        @Override
        public void endTransaction() {
            impl.endTransaction();
        }

        @Override
        public boolean inTransaction() {
            return impl.inTransaction();
        }
    }

}
