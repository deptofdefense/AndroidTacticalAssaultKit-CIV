
package com.atakmap.android.filesharing.android.service;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.filesystem.HashingUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Date;

public class FileInfo {

    public static final String ID_LABEL = "id";
    public static final String FILENAME_LABEL = "filename";
    public static final String CONTENT_TYPE_LABEL = "contentType";
    public static final String SIZE_LABEL = "size";
    public static final String UPDATE_TIME_LABEL = "updateTime";
    public static final String USERNAME_LABEL = "username";
    public static final String USERLABEL_LABEL = "userlabel";
    public static final String DOWNLOAD_URL_LABEL = "link";
    public static final String SHA256SUM_LABEL = "sha256sum";
    public static final String DESTINATION_PATH_LABEL = "destinationPath";
    public static final String FILE_METADATA = "fileMetadata";

    private static final String PRIMARY_KEY_TYPE = "INTEGER PRIMARY KEY ASC";
    private static final String INTEGER_TYPE = "INTEGER";
    private static final String TEXT_TYPE = "TEXT";

    public static final String[][] META_DATA_LABELS = {
            {
                    ID_LABEL, PRIMARY_KEY_TYPE
            },
            {
                    FILENAME_LABEL, TEXT_TYPE
            },
            {
                    CONTENT_TYPE_LABEL, TEXT_TYPE
            },
            {
                    SIZE_LABEL, INTEGER_TYPE
            },
            {
                    UPDATE_TIME_LABEL, INTEGER_TYPE
            },
            {
                    USERNAME_LABEL, TEXT_TYPE
            },
            {
                    USERLABEL_LABEL, TEXT_TYPE
            },
            {
                    DOWNLOAD_URL_LABEL, TEXT_TYPE
            },
            {
                    SHA256SUM_LABEL, TEXT_TYPE
            },
            {
                    DESTINATION_PATH_LABEL, TEXT_TYPE
            },
            {
                    FILE_METADATA, TEXT_TYPE
            }
    };

    public Object getFromMetaDataLabel(String label) {
        if (ID_LABEL.equals(label)) {
            return id();
        } else if (FILENAME_LABEL.equals(label)) {
            return fileName();
        } else if (CONTENT_TYPE_LABEL.equals(label)) {
            return contentType();
        } else if (SIZE_LABEL.equals(label)) {
            return sizeInBytes();
        } else if (UPDATE_TIME_LABEL.equals(label)) {
            return updateTime();
        } else if (USERNAME_LABEL.equals(label)) {
            return userName();
        } else if (USERLABEL_LABEL.equals(label)) {
            return userLabel();
        } else if (DOWNLOAD_URL_LABEL.equals(label)) {
            return downloadUrl();
        } else if (SHA256SUM_LABEL.equals(label)) {
            return sha256sum() == null ? computeSha256sum() : sha256sum();
        } else if (DESTINATION_PATH_LABEL.equals(label)) {
            return destinationPath();
        } else if (FILE_METADATA.equals(label)) {
            return fileMetadata();
        }
        return null;
    }

    private int id;
    private String fileName; // name of file
    private String contentType;
    private int sizeInBytes;
    private Long updateTime = null;
    private String username; // user who created (or sent) the file
    private String userlabel; // label given to the file by the user
    private String sha256sum;

    /**
     * Destination on the client file system to which file will be downloaded. Empty string means
     * "default location"
     */
    private String destinationPath; // local path of file
    private String downloadUrl; // download URL

    private String fileMetadata;

    /**
     * Default Constructor
     */
    public FileInfo() {
    }

    public FileInfo(File file, String fileMetadata) {
        this(file, null, null, null, fileMetadata);
    }

    public FileInfo(String sha256, File file, String contentType,
            String fileMetadata) {
        setId(-1); // use the DB to fill-out this field!
        setFileName(file.getName());
        setContentType(contentType);
        setSizeInBytes((int) IOProviderFactory.length(file));
        setUpdateTime(IOProviderFactory.lastModified(file));
        setDestinationPath(file.getParent());
        setSha256sum(sha256);
        setFileMetadata(fileMetadata);
    }

    public FileInfo(File file, String userName, String userLabel,
            String contentType,
            String fileMetadata) {
        setId(-1); // use the DB to fill-out this field!
        setFileName(file.getName());
        setContentType(contentType);
        setSizeInBytes((int) IOProviderFactory.length(file));
        setUpdateTime(IOProviderFactory.lastModified(file));
        setDestinationPath(file.getParent());
        computeSha256sum();
        setUserName(userName);
        setUserLabel(userLabel);
        setFileMetadata(fileMetadata);
    }

    /**
     * Constructor that expects the file contents in the form of a byte array.
     */
    public FileInfo(
            int id,
            String fileName,
            String contentType,
            int sizeInBytes,
            Long updateTime,
            String username,
            String userlabel,
            String destinationPath,
            String downloadUrl,
            String sha256sum,
            String fileMetadata) {
        setId(id);
        setFileName(fileName);
        setContentType(contentType);
        setSizeInBytes(sizeInBytes);
        setDestinationPath(destinationPath);
        setUpdateTime(updateTime);
        setUserName(username);
        setUserLabel(userlabel);
        setDownloadUrl(downloadUrl);
        setSha256sum(sha256sum);
        setFileMetadata(fileMetadata);
    }

    public void setId(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    /**
     * Set the filename for the FileInfo object.   The fileName is sanitized.
     */
    public void setFileName(final String fileName) {
        this.fileName = FileSystemUtils.sanitizeWithSpaces(fileName);
    }

    public String fileName() {
        return fileName;
    }

    public File file() {
        return new File(destinationPath, fileName);
    }

    /**
     * Hex representation
     * 
     * @param sha256 the sha256 for the specific file
     */
    public void setSha256sum(String sha256) {
        this.sha256sum = sha256;
    }

    /**
     * If explicitly set, this returns the value to which it was set. Otherwise, this returns the
     * value computed by computeMd5sum().
     * 
     * @return Hex representation of md5sum
     */
    public String sha256sum() {
        return sha256sum == null ? computeSha256sum() : sha256sum;
    }

    /**
     * Computes MD5 from file data.
     * 
     * @return the md5 for the file created by calling file()
     */
    public String computeSha256sum() {
        sha256sum = HashingUtils.sha256sum(file());
        return sha256sum;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    public void setSizeInBytes(int sizeInBytes) {
        this.sizeInBytes = sizeInBytes;
    }

    public int sizeInBytes() {
        return sizeInBytes;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public Long updateTime() {
        return updateTime;
    }

    public void setUserLabel(String label) {
        this.userlabel = label;
    }

    public String userLabel() {
        return userlabel;
    }

    public void setUserName(String username) {
        this.username = username;
    }

    public String userName() {
        return username;
    }

    /**
     * Sets the destination.   The destination is sanitized to only contain alphanumeric and '.' and '-' and '_' and ' ' and '/'.
     */
    public void setDestinationPath(String destinationPath) {
        this.destinationPath = FileSystemUtils
                .sanitizeWithSpacesAndSlashes(destinationPath);
    }

    public String destinationPath() {
        return destinationPath;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String downloadUrl() {
        return downloadUrl;
    }

    public void setFileMetadata(String metadata) {
        this.fileMetadata = metadata;
    }

    public String fileMetadata() {
        return fileMetadata;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject fileJson = new JSONObject();
        fileJson.put(ID_LABEL, id());
        fileJson.put(FILENAME_LABEL, fileName());
        fileJson.put(CONTENT_TYPE_LABEL, contentType());
        fileJson.put(SIZE_LABEL, sizeInBytes());
        fileJson.put(UPDATE_TIME_LABEL, updateTime());
        fileJson.put(USERNAME_LABEL, userName());
        fileJson.put(USERLABEL_LABEL, userLabel());
        if (sha256sum == null) {
            fileJson.put(SHA256SUM_LABEL, computeSha256sum());
        } else {
            fileJson.put(SHA256SUM_LABEL, sha256sum());
        }
        fileJson.put(DESTINATION_PATH_LABEL, destinationPath());
        fileJson.put(DOWNLOAD_URL_LABEL, downloadUrl());
        fileJson.put(FILE_METADATA, fileMetadata());
        return fileJson;
    }

    public static FileInfo fromJSON(JSONObject file) throws JSONException {
        FileInfo newFile = new FileInfo();
        newFile.setId(((Long) file.get(ID_LABEL)).intValue());
        newFile.setFileName((String) file.get(FILENAME_LABEL));
        newFile.setContentType((String) file.get(CONTENT_TYPE_LABEL));
        newFile.setSizeInBytes(((Long) file.get(SIZE_LABEL)).intValue());
        // (byte[]) null, /* file data */
        newFile.setUpdateTime((Long) file.get(UPDATE_TIME_LABEL));
        newFile.setUserName((String) file.get(USERNAME_LABEL));
        newFile.setUserLabel((String) file.get(USERLABEL_LABEL));
        newFile.setDestinationPath((String) file.get(DESTINATION_PATH_LABEL));
        newFile.setDownloadUrl((String) file.get(DOWNLOAD_URL_LABEL));
        newFile.setSha256sum((String) file.get(SHA256SUM_LABEL));
        newFile.setFileMetadata((String) file.get(FILE_METADATA));
        return newFile;
    }

    public String toString() {
        return fileName + " (" + contentType + ") size=" + sizeInBytes
                + " bytes, time="
                + new Date(updateTime) + ", url=" + downloadUrl + ", by "
                + username;
    }

    /**
     * Compare to "this" If either has id of -1, ids are not compared
     */
    @Override
    public boolean equals(Object rhs) {
        if (!(rhs instanceof FileInfo))
            return false;

        FileInfo rhsfi = (FileInfo) rhs;
        return equals(rhsfi, (id != -1 && rhsfi.id() != -1));
    }

    /**
     * Compare to "this" and optionally compare ID fields
     * 
     * @param rhs the Fileinfo object o compare with
     * @param checkID if the id check is to be performed.
     * @return boolean if the FileInfo objects are equal
     */
    public boolean equals(Object rhs, boolean checkID) {
        if (!(rhs instanceof FileInfo))
            return false;

        FileInfo rhsInfo = (FileInfo) rhs;
        // check id, name and size (quicker) first
        if (checkID && (id != rhsInfo.id()))
            return false;

        if (!fileName.equals(rhsInfo.fileName()))
            return false;

        if (sizeInBytes != rhsInfo.sizeInBytes())
            return false;

        // now check if MD5 matches
        if (!sha256sum().equals(rhsInfo.sha256sum()))
            return false;

        // Note disabled these checks to support MP load and import
        // where we don't know the username/label...

        // if(username == null && rhsInfo.userName() != null)
        // return false;
        // if(username != null && rhsInfo.userName() == null)
        // return false;
        // if(username != null && rhsInfo.userName() != null)
        // {
        // if(!username.equals(rhsInfo.userName()))
        // return false;
        // }
        //
        // if(userlabel == null && rhsInfo.userLabel() != null)
        // return false;
        // if(userlabel != null && rhsInfo.userLabel() == null)
        // return false;
        // if(userlabel != null && rhsInfo.userLabel() != null)
        // {
        // if(!userlabel.equals(rhsInfo.userLabel()))
        // return false;
        // }

        return FileSystemUtils.isEquals(fileMetadata, rhsInfo.fileMetadata);

    }

    @Override
    public int hashCode() {
        return (fileName + id).hashCode();
    }
}
