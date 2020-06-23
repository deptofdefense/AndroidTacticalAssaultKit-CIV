
package com.atakmap.android.filesharing.android.service;

import com.atakmap.coremap.maps.time.CoordinatedTime;

import java.util.Date;

/**
 * DB object for a a File Transaction, based partially on FileInfo
 * 
 * 
 */
public class FileTransferLog {

    public enum TYPE {
        SEND,
        RECV,
        IMPORT
    }

    static final String ID_LABEL = "id";
    static final String TYPE_LABEL = "type";
    static final String NAME_LABEL = "name";
    static final String DESCRIPTION_LABEL = "desc";
    static final String SIZE_LABEL = "size";
    static final String TIME_LABEL = "time";

    private static final String PRIMARY_KEY_TYPE = "INTEGER PRIMARY KEY ASC";
    private static final String INTEGER_TYPE = "INTEGER";
    private static final String TEXT_TYPE = "TEXT";

    public static final String[][] META_DATA_LABELS = {
            {
                    ID_LABEL, PRIMARY_KEY_TYPE
            },
            {
                    TYPE_LABEL, TEXT_TYPE
            },
            {
                    NAME_LABEL, TEXT_TYPE
            },
            {
                    DESCRIPTION_LABEL, TEXT_TYPE
            },
            {
                    SIZE_LABEL, INTEGER_TYPE
            },
            {
                    TIME_LABEL, INTEGER_TYPE
            }
    };

    public Object getFromMetaDataLabel(String label) {
        if (ID_LABEL.equals(label)) {
            return id();
        } else if (TYPE_LABEL.equals(label)) {
            return type();
        } else if (NAME_LABEL.equals(label)) {
            return name();
        } else if (SIZE_LABEL.equals(label)) {
            return sizeInBytes();
        } else if (TIME_LABEL.equals(label)) {
            return getTime();
        } else if (DESCRIPTION_LABEL.equals(label)) {
            return description();
        }
        return null;
    }

    private int id;
    private String type;
    private String name;
    private String description;
    private long sizeInBytes;
    private long time = -1;

    public FileTransferLog(TYPE type, String name, String description,
            long sizeInBytes) {
        this(type, name, description, sizeInBytes, (new CoordinatedTime())
                .getMilliseconds());
    }

    public FileTransferLog(TYPE type, String name, String description,
            long sizeInBytes, long time) {
        // use the DB to fill-out ID!
        this(-1, type, name, description, sizeInBytes, time);
    }

    /**
     * Constructor that expects the file contents in the form of a byte array.
     */
    FileTransferLog(int id, TYPE type, String name, String description,
            long sizeInBytes,
            long time) {
        setId(id);
        setType(type);
        setName(name);
        setDescription(description);
        setSizeInBytes(sizeInBytes);
        setTime(time);
    }

    public void setId(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void setType(TYPE type) {
        this.type = type.toString();
    }

    public TYPE type() {
        return TYPE.valueOf(type);
    }

    public void setSizeInBytes(long sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    public long sizeInBytes() {
        return sizeInBytes;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public long getTime() {
        return time;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return type + " (" + name + ") size=" + sizeInBytes + " bytes, time="
                + new Date(time);
    }

    /**
     * Compare to "this" If either has id of -1, ids are not compared
     */
    @Override
    public boolean equals(Object rhs) {
        if (!(rhs instanceof FileTransferLog))
            return false;

        FileTransferLog rhsfi = (FileTransferLog) rhs;
        return equals(rhsfi, (id != -1 && rhsfi.id() != -1));
    }

    /**
     * Compare to "this" and optionally compare ID fields
     * 
     * @param rhs the object to compare this to
     * @param checkID if to compare the id fields
     * @return true if they are equal based on the criteria
     */
    public boolean equals(Object rhs, boolean checkID) {
        if (!(rhs instanceof FileTransferLog))
            return false;

        FileTransferLog rhsInfo = (FileTransferLog) rhs;
        // check id, name and size (quicker) first
        return !(checkID && (id != rhsInfo.id())) &&
                name.equals(rhsInfo.name()) &&
                type.equals(rhsInfo.type().name()) &&
                description.equals(rhsInfo.description()) &&
                sizeInBytes == rhsInfo.sizeInBytes() &&
                time == rhsInfo.getTime();

    }

    @Override
    public int hashCode() {
        return (type + name + id).hashCode();
    }

    public interface Listener {
        void onEvent(FileTransferLog log, boolean added);
    }
}
