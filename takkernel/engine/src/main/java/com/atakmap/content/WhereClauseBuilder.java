package com.atakmap.content;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import com.atakmap.database.CursorIface;

public final class WhereClauseBuilder {
    private StringBuilder selection;
    private List<BindArgument> args;

    public WhereClauseBuilder() {
        this.selection = new StringBuilder();
        this.args = new LinkedList<BindArgument>();
    }

    public void beginCondition() {
        if (this.selection.length() > 0)
            this.selection.append(" AND ");
    }

    public void append(String s) {
        this.selection.append(s);
    }

    public void appendIn(String col, Collection<String> vals) {
        Collection<BindArgument> bindArgs = new ArrayList<BindArgument>(vals.size());
        for(String s : vals) {
            if(s != null)
                bindArgs.add(new BindArgument(s));
            else
                bindArgs.add(new BindArgument());
        }
        
        this.appendIn2(col, bindArgs);
    }
    
    public void appendIn2(String col, Collection<BindArgument> vals) {
        int wildcards = 0;
        for(BindArgument arg : vals) {
            if(isWildcard(arg))
                wildcards++;
        }
        final int numVals = vals.size();
        if(wildcards == 0) {
            this.appendIn(col, numVals);
            this.args.addAll(vals);
        } else if(numVals == 1) {
            this.selection.append(col);
            this.selection.append(" LIKE ?");
            this.args.addAll(vals);
        } else {
            this.selection.append("(");
            if(wildcards == numVals) {
                this.selection.append(col);
                this.selection.append(" LIKE ?");
                for(int i = 1; i < numVals; i++) {
                    this.selection.append(" OR ");
                    this.selection.append(col);
                    this.selection.append(" LIKE ?");
                }
                this.args.addAll(vals);
            } else {
                Collection<BindArgument> nonWC = new ArrayList<BindArgument>(numVals-wildcards);
                
                this.selection.append("(");
                this.selection.append(col);
                this.selection.append(" LIKE ?");
                for(BindArgument arg : vals) {
                    if(isWildcard(arg)) {
                        this.args.add(arg);
                        wildcards--;
                        if(wildcards > 0) {
                            this.selection.append(" OR ");
                            this.selection.append(col);
                            this.selection.append(" LIKE ?");
                        }
                    } else {
                        nonWC.add(arg);
                    }
                }
                this.selection.append(") OR (");
                
                this.appendIn(col, nonWC.size());
                this.args.addAll(nonWC);
                this.selection.append(")");
            }
            this.selection.append(")");
        }
    }
    
    public void appendIn(String col, int numArgs) {
        if(numArgs == 1) {
            selection.append(col);
            selection.append(" = ?");
        } else {
            selection.append(col);
            selection.append(" IN (");
            if(numArgs > 0)
                selection.append("?");
            for(int i = 1; i < numArgs; i++)
                selection.append(", ?");
            selection.append(")");
        }
    }
    
    public void addArg(int arg) {
        this.args.add(new BindArgument(arg));
    }
    
    public void addArg(long arg) {
        this.args.add(new BindArgument(arg));
    }
    
    public void addArg(double arg) {
        this.args.add(new BindArgument(arg));
    }
    
    public void addArg(String arg) {
        if(arg != null)
            this.args.add(new BindArgument(arg));
        else
            this.args.add(new BindArgument());
    }
    
    public void addArg(byte[] arg) {
        if(arg != null)
            this.args.add(new BindArgument(arg));
        else
            this.args.add(new BindArgument());
    }
    
    public void addArgs(Collection<String> args) {
        for(String s : args)
            this.addArg(s);
    }
    
    public void addArgs2(Collection<BindArgument> args) {
        for(BindArgument arg : args)
            this.args.add(arg);
    }

    public boolean hasSelection() {
        return (this.selection.length() > 0);
    }

    public String getSelection() {
        if (this.selection.length() < 1)
            return null;
        return this.selection.toString();
    }
    
    public StringBuilder getSelectionBuffer() {
        return this.selection;
    }

    public String[] getArgs() {
        if(this.args.isEmpty())
            return null;
        return this.getArgsList().toArray(new String[this.args.size()]);
    }
    
    public List<String> getArgsList() {
        List<String> retval = new LinkedList<String>();
        for(BindArgument arg : this.args) {
            switch(arg.getType()) {
                case CursorIface.FIELD_TYPE_NULL :
                    retval.add(null);
                    break;
                case CursorIface.FIELD_TYPE_STRING :
                    retval.add((String)arg.getValue());
                    break;
                default :
                    retval.add(arg.getValue().toString());
                    break;
            }
        }
        return retval;
    }
    
    public List<BindArgument> getBindArgs() {
        return this.args;
    }

    public void clear() {
        this.selection.setLength(0);
        this.args.clear();
    }
    
    /**************************************************************************/

    public static Collection<String> stringify(Collection<?> args) {
        LinkedList<String> retval = new LinkedList<String>();
        for(Object o : args)
            retval.add((o != null) ? o.toString() : null);
        return retval;
    }
    
    public static boolean isWildcard(String arg) {
        return (arg != null && arg.indexOf('%') >= 0);
    }
    
    public static boolean isWildcard(BindArgument arg) {
        return (arg.getType() == CursorIface.FIELD_TYPE_STRING && ((String)arg.getValue()).indexOf('%') >= 0);
    }
}
