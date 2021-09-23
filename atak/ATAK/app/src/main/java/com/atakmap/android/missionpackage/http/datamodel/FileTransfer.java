
package com.atakmap.android.missionpackage.http.datamodel;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.filesystem.HashingUtils;

import java.io.File;

/**
 * Provides parcelable and CoT representation of File Transfer details
 * 
 * 
 */
public class FileTransfer implements Parcelable {
    public static final String TAG = "FileTransfer";

    public static final String COT_TYPE = "b-f-t-r"; // binary-file-transfer-request
    public static final String COT_HOW = "h-e"; //

    /**
     * local path on sender or receiver, may not be the same e.g. if file already existed and was
     * renamed on receiver Also for zipped files, it is a directory path to save file to, otherwise
     * it is path to actual file
     */
    // file info
    private final String _name; // label given by user
    private String _sha256;
    private long _size;

    // sender info
    private final String _connectString; // connectString used to build download Url
    private final String _senderLocalPath;
    private final String _senderUID;
    private final String _senderCallsign;

    private final String _uid; // of this fileshare

    public FileTransfer(String name, String senderUID, String senderCallsign,
            String fileShareUID,
            String senderLocalPath, String link, long size, String sha256) {
        _name = name;
        _senderUID = senderUID;
        _senderCallsign = senderCallsign;
        _uid = fileShareUID;
        _senderLocalPath = senderLocalPath;
        _connectString = link;
        _size = size;

        _sha256 = sha256;

        if (FileSystemUtils.isFile(_senderLocalPath)) {
            _size = IOProviderFactory.length(new File(_senderLocalPath));
            if (FileSystemUtils.isEmpty(_sha256)) {
                computeHash();
            }
        }
    }

    public String getLocalPath() {
        return _senderLocalPath;
    }

    public String getConnectString() {
        return _connectString;
    }

    public long getSize() {
        return _size;
    }

    public String getSHA256(boolean bCompute) {
        if (bCompute && FileSystemUtils.isEmpty(_sha256)
                && FileSystemUtils.isFile(_senderLocalPath)) {
            computeHash();
        }

        return _sha256;
    }

    public String getSenderUID() {
        return _senderUID;
    }

    public String getSenderCallsign() {
        return _senderCallsign;
    }

    public String getName() {
        return _name;
    }

    public String getUID() {
        return _uid;
    }

    public static FileTransfer fromQuery(Context context,
            MissionPackageQueryResult result,
            String serverConnectString, String filesharePath) {
        if (result == null || !result.isValid()) {
            Log.w(TAG,
                    "Unable to create FileTransfer from invalid query result");
            return null;
        }

        if (FileSystemUtils.isEmpty(serverConnectString)) {
            Log.w(TAG,
                    "Cannot create FileTransfer without valid serverConnectString");
            return null;
        }

        String fileShareUID = result.getUID();
        long size = result.getSize();

        String sha256 = result.getHash();

        String name = result.getName();
        String filename = result.getName();
        String senderUID = null;
        String senderCallsign = context.getString(R.string.MARTI_sync_server);

        String localPath = getLocalPath(filesharePath, filename);

        // No ack for TAK server manual downloads

        return new FileTransfer(name, senderUID, senderCallsign, fileShareUID,
                localPath, serverConnectString, size, sha256);
    }

    /**
     * Location to store local copy of downloaded file
     * 
     * @return the local path to store the downloaded file.
     */
    private static String getLocalPath(String filesharePath, String filename) {
        String filepath = FileSystemUtils
                .sanitizeWithSpacesAndSlashes(filesharePath
                        + File.separator + filename);

        File dest = new File(filepath);
        if (IOProviderFactory.exists(dest)) {
            filepath = FileSystemUtils.getRandomFilepath(filepath);
            Log.d(TAG, "File already exists in fileshare, renaming to: "
                    + filepath);
        }

        return filepath;
    }

    public boolean isValid() {
        // Note require path, but dont require file to exist b/c it does not exist
        // on receiver of shared file transfer at time CoT is de-serializaed, rather
        // not until the HTTP GET completes...

        return !FileSystemUtils.isEmpty(_senderLocalPath)
                && !FileSystemUtils.isEmpty(_uid)
                && !FileSystemUtils.isEmpty(_connectString)
                //Not required when querying/downloading from TAK Server
                //&& !FileSystemUtils.isEmpty(_senderUID)
                && !FileSystemUtils.isEmpty(_senderCallsign)
                && !FileSystemUtils.isEmpty(_name)
                && !FileSystemUtils.isEmpty(_sha256);
    }

    private void computeHash() {
        _sha256 = HashingUtils.sha256sum(new File(_senderLocalPath));
    }

    public boolean verify(File file) {
        return HashingUtils.verify(file, _size, _sha256);
    }

    @Override
    public String toString() {
        return String.format("%s %s, %s SHA256=%s from %s", _name,
                _senderLocalPath,
                _connectString, _sha256,
                _senderCallsign);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (isValid()) {
            dest.writeString(_name);
            dest.writeString(_senderLocalPath);
            dest.writeString(_connectString);
            dest.writeString(_senderUID);
            dest.writeString(_senderCallsign);
            dest.writeString(_uid);
            dest.writeString(_sha256);
            dest.writeLong(_size);
            dest.writeByte((byte) 0);
        }
    }

    public static final Parcelable.Creator<FileTransfer> CREATOR = new Parcelable.Creator<FileTransfer>() {
        @Override
        public FileTransfer createFromParcel(Parcel in) {
            return new FileTransfer(in);
        }

        @Override
        public FileTransfer[] newArray(int size) {
            return new FileTransfer[size];
        }
    };

    protected FileTransfer(Parcel in) {
        _name = in.readString();
        _senderLocalPath = in.readString();
        _connectString = in.readString();
        _senderUID = in.readString();
        _senderCallsign = in.readString();
        _uid = in.readString();
        _sha256 = in.readString();
        _size = in.readLong();

        boolean hasParcelable = (in.readByte() == 0 ? false : true);
        //if (hasParcelable)
        //    _ack = in.readParcelable(CoTAck.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
