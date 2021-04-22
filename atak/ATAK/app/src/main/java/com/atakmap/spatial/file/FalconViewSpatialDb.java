
package com.atakmap.spatial.file;

import android.util.SparseArray;

import com.atakmap.android.util.ATAKUtilities;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;
import com.atakmap.database.StatementIface;
import com.atakmap.map.layer.feature.DataSourceFeatureDataStore;
import com.atakmap.map.layer.feature.falconview.FalconViewFeatureDataSource;
import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.Database;
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Table;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spatial database interface for LPT/DRW files
 */
public class FalconViewSpatialDb extends SpatialDbContentSource implements
        FalconViewFeatureDataSource.DBCreator {

    private static final String TAG = "FalconViewSpatialDb";

    public static final String LPT = "LPT", DRW = "DRW";
    public static final String MIME_TYPE = "application/x-msaccess";

    public FalconViewSpatialDb(DataSourceFeatureDataStore database,
            String ext) {
        super(database, ext.toUpperCase(LocaleUtil.getCurrent()),
                ext.toLowerCase(LocaleUtil.getCurrent()));
        // Hook up to the data source within the map engine
        FalconViewFeatureDataSource.initDBCreator(this);
    }

    @Override
    public String getFileDirectoryName() {
        return FileSystemUtils.OVERLAYS_DIRECTORY;
    }

    @Override
    public String getFileMimeType() {
        return MIME_TYPE;
    }

    @Override
    public String getIconPath() {
        return ATAKUtilities.getResourceUri(getIconId());
    }

    @Override
    public int getIconId() {
        return getGroupName().equals(LPT) ? R.drawable.ic_falconview_lpt
                : R.drawable.ic_falconview_drw;
    }

    @Override
    protected String getProviderHint(File file) {
        return "falconview";
    }

    @Override
    public String getContentType() {
        return getGroupName();
    }

    @Override
    public int processAccept(File file, int depth) {
        // Only accept files that end in either .lpt or .drw
        if (IOProviderFactory.isFile(file) && IOProviderFactory.canRead(file)) {
            if (FileSystemUtils.checkExtension(file, getType()))
                return PROCESS_ACCEPT;
        } else if (IOProviderFactory.isDirectory(file)) {
            return PROCESS_RECURSE;
        }

        return PROCESS_REJECT;
    }

    @Override
    public DatabaseIface createDatabase(File file) {
        try {
            // Open new MS Access database
            return new MSDatabase(file);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open FV database: " + file, e);
        }
        return null;
    }

    private static class MSDatabase implements DatabaseIface {

        private final Database _db;

        MSDatabase(File file) throws Exception {
            DatabaseBuilder b = new DatabaseBuilder();
            b.setChannel(IOProviderFactory.getChannel(file, "r"));
            b.setReadOnly(true);
            _db = b.open();
        }

        @Override
        public void execute(String sql, String[] args) {
            // Unsupported
        }

        @Override
        public CursorIface query(String sql, String[] args) {
            return compileQuery(sql);
        }

        @Override
        public StatementIface compileStatement(String sql) {
            // Unsupported
            return null;
        }

        @Override
        public QueryIface compileQuery(String sql) {
            String lower = sql.toLowerCase(LocaleUtil.getCurrent());

            // Currently only supports SELECT * from <table> statements
            if (lower.startsWith("select ") && lower.contains(" from ")) {

                // Extract table name
                int fromIdx = lower.indexOf(" from ");
                int fromSpace = lower.indexOf(' ', fromIdx + 6);
                if (fromSpace == -1)
                    fromSpace = lower.length();
                String tableName = sql.substring(fromIdx + 6, fromSpace);

                // Get the table and convert to a cursor interface
                try {
                    Table table = _db.getTable(tableName);
                    return new MSQuery(table);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to get table: " + tableName);
                }
            }
            return null;
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }

        @Override
        public void close() {
            try {
                _db.close();
            } catch (Exception e) {
                Log.e(TAG, "Failed to close database", e);
            }
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
    }

    private static class MSQuery implements QueryIface {

        private final Table _table;
        private final SparseArray<Column> _columnByNum = new SparseArray<>();
        private final Map<String, Column> _columnByName = new HashMap<>();

        private Map<String, Object> _row;
        private boolean _closed;

        MSQuery(Table table) {
            _table = table;

            // Map columns by display index (used by native to query row data)
            // and name for fast lookup
            List<Column> cols = table.getColumns();
            for (Column col : cols) {
                _columnByNum.put(col.getDisplayIndex(), col);
                _columnByName.put(col.getName(), col);
            }
        }

        private <T> T getValue(int columnIndex, Class<T> clazz, T fallback) {
            String name = getColumnName(columnIndex);
            try {
                return clazz.cast(_row.get(name));
            } catch (Exception e) {
                Log.e(TAG, "Failed to get column \"" + name + "\" as "
                        + clazz.getSimpleName(), e);
            }
            return fallback;
        }

        @Override
        public int getColumnIndex(String columnName) {
            Column col = _columnByName.get(columnName);
            return col != null ? col.getColumnNumber() : -1;
        }

        @Override
        public String getColumnName(int columnIndex) {
            Column col = _columnByNum.get(columnIndex);
            return col != null ? col.getName() : null;
        }

        @Override
        public String[] getColumnNames() {
            String[] names = new String[getColumnCount()];
            for (int i = 0; i < names.length; i++) {
                String name = getColumnName(i);
                names[i] = name != null ? name : "";
            }
            return names;
        }

        @Override
        public int getColumnCount() {
            return _columnByNum.size();
        }

        @Override
        public byte[] getBlob(int columnIndex) {
            String name = getColumnName(columnIndex);
            try {
                return (byte[]) _row.get(name);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get column \"" + name
                        + "\" as byte array", e);
            }
            return null;
        }

        @Override
        public String getString(int columnIndex) {
            String str = getValue(columnIndex, String.class, "");
            return str != null ? str : "";
        }

        @Override
        public int getInt(int columnIndex) {
            return getValue(columnIndex, Short.class, (short) 0);
        }

        @Override
        public long getLong(int columnIndex) {
            return getValue(columnIndex, Integer.class, 0);
        }

        @Override
        public double getDouble(int columnIndex) {
            return getValue(columnIndex, Double.class, 0d);
        }

        @Override
        public int getType(int columnIndex) {
            Object o = getValue(columnIndex, Object.class, null);
            if (o instanceof String)
                return FIELD_TYPE_STRING;
            else if (o instanceof Integer || o instanceof Long)
                return FIELD_TYPE_INTEGER;
            else if (o instanceof Float || o instanceof Double)
                return FIELD_TYPE_FLOAT;
            else if (o instanceof byte[])
                return FIELD_TYPE_BLOB;
            return FIELD_TYPE_NULL;
        }

        @Override
        public boolean isNull(int columnIndex) {
            return getValue(columnIndex, Object.class, null) == null;
        }

        @Override
        public boolean moveToNext() {
            try {
                _row = _table.getNextRow();
            } catch (Exception e) {
                _row = null;
                Log.e(TAG, "Failed to get next row", e);
            }
            return _row != null;
        }

        @Override
        public void close() {
            _closed = true;
        }

        @Override
        public boolean isClosed() {
            return _closed;
        }

        @Override
        public void reset() {
        }

        @Override
        public void bind(int idx, byte[] value) {
        }

        @Override
        public void bind(int idx, int value) {
        }

        @Override
        public void bind(int idx, long value) {
        }

        @Override
        public void bind(int idx, double value) {
        }

        @Override
        public void bind(int idx, String value) {
        }

        @Override
        public void bindNull(int idx) {
        }

        @Override
        public void clearBindings() {
        }
    }
}
