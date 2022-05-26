
package com.atakmap.android.statesaver;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.math.MathUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static com.atakmap.android.statesaver.StateSaver.*;

/**
 * Query parameters specifically intended for use with the {@link StateSaver}
 */
public class StateSaverQueryParameters {

    // Properties that can be ignored via "ignoredProperties" bit flags
    public final static int PROPERTY_EVENT = 0x01;

    // CoT UIDs to whitelist (null/empty = query all)
    public Set<String> uids;

    // CoT types to whitelist
    public Set<String> types;

    // Only query visible events
    public boolean visibleOnly;

    // Timestamp range
    public long minimumTimestamp = -1;
    public long maximumTimestamp = -1;

    // Ordering of query results (null = default ordering)
    public Collection<Order> order;

    // Properties to exclude from the query result
    // i.e. PROPERTY_EVENT = exclude CoT event XML from result
    public int ignoredProperties;

    // Limit and offset the number of query results
    public int limit;
    public int offset;

    // Types of result orders
    public static class Order {

        // Order by query order
        public static final class Default extends Order {
            public Default(boolean ascending) {
                super(ascending);
            }

            public Default() {
                super(true);
            }

            @Override
            public String toString() {
                return COLUMN_QUERY_ORDER;
            }
        }

        // Order by ID value
        public static final class ID extends Order {
            public ID(boolean ascending) {
                super(ascending);
            }

            public ID() {
                super(true);
            }

            @Override
            public String toString() {
                return COLUMN_ID;
            }
        }

        // Order by last update time
        public static final class Time extends Order {
            public Time(boolean ascending) {
                super(ascending);
            }

            public Time() {
                super(true);
            }

            @Override
            public String toString() {
                return COLUMN_LAST_UPDATE;
            }
        }

        public final boolean ascending;

        public Order(boolean ascending) {
            this.ascending = ascending;
        }

        public Order() {
            this(true);
        }
    }

    /**
     * Execute a query using these parameters on the given database
     * @param db State saver database
     * @return Cursor
     */
    CursorIface executeQuery(DatabaseIface db) {

        StringBuilder sql = new StringBuilder("SELECT "
                + COLUMN_ID + ", "
                + COLUMN_UID + ", "
                + COLUMN_TYPE + ", "
                + COLUMN_VISIBLE + ", "
                + COLUMN_LAST_UPDATE + ", "
                + COLUMN_QUERY_ORDER);
        List<Object> args = new ArrayList<>();

        if (!MathUtils.hasBits(this.ignoredProperties,
                StateSaverQueryParameters.PROPERTY_EVENT))
            sql.append(", ").append(COLUMN_EVENT);

        sql.append(" FROM ").append(TABLE_COTEVENTS);

        // Filtering
        StringBuilder whereClause = new StringBuilder();
        appendWhere(COLUMN_TYPE, this.types, whereClause, args);
        appendWhere(COLUMN_UID, this.uids, whereClause, args);

        if (this.minimumTimestamp >= 0 || this.maximumTimestamp >= 0) {
            if (whereClause.length() > 0)
                whereClause.append(" AND ");
            whereClause.append(COLUMN_LAST_UPDATE);
            if (this.minimumTimestamp >= 0 && this.maximumTimestamp >= 0) {
                whereClause.append(" BETWEEN ? AND ?");
                args.add(this.minimumTimestamp);
                args.add(this.maximumTimestamp);
            } else if (this.minimumTimestamp >= 0) {
                whereClause.append(" >= ?");
                args.add(this.minimumTimestamp);
            } else {
                whereClause.append(" <= ?");
                args.add(this.maximumTimestamp);
            }
        }

        if (whereClause.length() > 0)
            sql.append(" WHERE ").append(whereClause);

        // Ordering
        sql.append(" ORDER BY ");
        if (this.order != null && !this.order.isEmpty()) {
            for (StateSaverQueryParameters.Order order : this.order)
                sql.append(order).append(" ")
                        .append(order.ascending ? "ASC" : "DESC").append(", ");
            sql.delete(sql.length() - 2, sql.length());
        } else
            sql.append(COLUMN_QUERY_ORDER).append(" ASC");

        // Limiting
        if (this.limit > 0) {
            sql.append(" LIMIT ?");
            args.add(this.limit);
            if (this.offset > 0) {
                sql.append(" OFFSET ?");
                args.add(this.offset);
            }
        }

        int i = 0;
        String[] argsArr = new String[args.size()];
        for (Object a : args)
            argsArr[i++] = String.valueOf(a);

        return db.query(sql.toString(), argsArr);
    }

    private static void appendWhere(String column, Collection<String> args,
            StringBuilder ret, List<Object> retArgs) {
        if (FileSystemUtils.isEmpty(args))
            return;

        StringBuilder argBuilder = new StringBuilder();
        for (String a : args) {
            if (FileSystemUtils.isEmpty(a))
                continue;
            if (argBuilder.length() > 0)
                argBuilder.append(", ");
            argBuilder.append("?");
            retArgs.add(a);
            if (args.size() == 1)
                break;
        }

        if (argBuilder.length() == 0)
            return;

        if (ret.length() > 0)
            ret.append(" AND ");

        ret.append(column);

        if (args.size() == 1)
            ret.append(" = ");
        else
            ret.append(" IN (");
        ret.append(argBuilder);
        if (args.size() > 1)
            ret.append(")");
    }
}
