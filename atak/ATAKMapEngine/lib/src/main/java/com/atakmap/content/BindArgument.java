package com.atakmap.content;

import java.util.Collection;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.database.Bindable;
import com.atakmap.database.CursorIface;
import com.atakmap.database.DatabaseIface;
import com.atakmap.database.QueryIface;

/** @deprecated to be removed without replacement */
@Deprecated
@DeprecatedApi(since = "4.1.1", forRemoval = true, removeAt = "4.4")
public final class BindArgument {

    private int type;
    private Object value;

    public BindArgument(int val) {
        this(CursorIface.FIELD_TYPE_INTEGER, Integer.valueOf(val));
    }
    
    public BindArgument(long val) {
        this(CursorIface.FIELD_TYPE_INTEGER, Long.valueOf(val));
    }
    
    public BindArgument(double val) {
        this(CursorIface.FIELD_TYPE_FLOAT, Double.valueOf(val));
    }
    
    public BindArgument(String val) {
        this(CursorIface.FIELD_TYPE_STRING, val);
    }
    
    public BindArgument(byte[] val) {
        this(CursorIface.FIELD_TYPE_BLOB, val);
    }
    
    public BindArgument() {
        this(CursorIface.FIELD_TYPE_NULL, null);
    }

    private BindArgument(int type, Object val) {
        this.setImpl(type, val);
    }

    public void set(int val) {
        this.setImpl(CursorIface.FIELD_TYPE_INTEGER, Integer.valueOf(val));
    }
    
    public void set(long val) {
        this.setImpl(CursorIface.FIELD_TYPE_INTEGER, Long.valueOf(val));
    }
    
    public void set(double val) {
        this.setImpl(CursorIface.FIELD_TYPE_FLOAT, Double.valueOf(val));
    }
    
    public void set(String val) {
        this.setImpl((val != null) ? CursorIface.FIELD_TYPE_STRING : CursorIface.FIELD_TYPE_NULL, val);
    }
    
    public void set(byte[] val) {
        this.setImpl((val != null) ? CursorIface.FIELD_TYPE_BLOB : CursorIface.FIELD_TYPE_NULL, val);
    }
    
    public void clear() {
        this.setImpl(CursorIface.FIELD_TYPE_NULL, null);
    }
    
    private void setImpl(int type, Object val) {
        this.type = type;
        this.value = val;
    }
    
    public int getType() {
        return this.type;
    }

    public Object getValue() {
        return this.value;
    }
    
    public void bind(Bindable stmt, int idx) {
        switch(this.type) {
            case CursorIface.FIELD_TYPE_INTEGER :
                stmt.bind(idx, ((Number)this.value).longValue());
                break;
            case CursorIface.FIELD_TYPE_FLOAT :
                stmt.bind(idx, ((Number)this.value).doubleValue());
                break;
            case CursorIface.FIELD_TYPE_STRING :
                stmt.bind(idx, (String)this.value);
                break;
            case CursorIface.FIELD_TYPE_BLOB :
                stmt.bind(idx, (byte[])value);
                break;
            case CursorIface.FIELD_TYPE_NULL :
                stmt.bindNull(idx);
                break;
            default :
                throw new IllegalStateException();
        }
    }
    
    @Override
    public String toString() {
        return "BindArgument {" + this.value + "}";
    }
    
    /**************************************************************************/
    
    /**
     * Performs a query on the specified database with arguments of arbitrary
     * type.
     * 
     * @param database  The database
     * @param sql       The query SQL
     * @param args      The arguments
     * 
     * @return  The query result
     */
    public static CursorIface query(DatabaseIface database, CharSequence sql, BindArgument[] args) {
        QueryIface query = null;
        try {
            query = database.compileQuery(sql.toString());
            if(args != null) {
                for(int i = 0; i < args.length; i++)
                    args[i].bind(query, i+1);
            }
            final CursorIface retval = query;
            query = null;
            return retval;
        } finally {
            if(query != null)
                query.close();
        }
    }
    
    /**
     * Performs a query on the specified database with arguments of arbitrary
     * type.
     * 
     * @param database  The database
     * @param sql       The query SQL
     * @param args      The arguments
     * 
     * @return  The query result
     */
    public static CursorIface query(DatabaseIface database, CharSequence sql, Collection<BindArgument> args) {
        QueryIface query = null;
        try {
            query = database.compileQuery(sql.toString());
            if(args != null) {
                int idx = 1;
                for(BindArgument arg : args)
                    arg.bind(query, idx++);
            }
            final CursorIface retval = query;
            query = null;
            return retval;
        } finally {
            if(query != null)
                query.close();
        }
    }
}
