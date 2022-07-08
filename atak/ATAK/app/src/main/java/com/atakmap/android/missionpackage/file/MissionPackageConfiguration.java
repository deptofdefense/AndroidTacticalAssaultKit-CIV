
package com.atakmap.android.missionpackage.file;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.util.ArrayList;
import java.util.List;

/**
 * Parameters describing the Mission Package. Only a single parameter of a given name is allowed
 * Required: name - Name or label for this Mission Package uid- Unique ID (UUID) for this Mission
 * Package Note, optional parameters may be included for extensibility remarks - comments about the
 * package
 * 
 * 
 */
@Root
public class MissionPackageConfiguration implements Parcelable {

    private static final String TAG = "MissionPackageConfiguration";

    public final static String PARAMETER_NAME = "name";
    public final static String PARAMETER_UID = "uid";
    public final static String PARAMETER_REMARKS = "remarks";

    /**
     * Omit or false to have Mission Package persist on receiver, If true, the Mission Package .zip
     * will be automatically deleted after extraction on the receiver. Note, this is not
     * currently processed for locally imported packages, rather only for packages received
     * over the network (e.g. sent by another ATAK user)
     */
    public final static String PARAMETER_OnReceiveDelete = "onReceiveDelete";

    /**
     * Omit or true to have Mission Package imported on receiver, If true, the Mission Package .zip
     * will be automatically imported based on content inspection of zipped files/data. If false
     * data will be extracted but not imported. Note, this is not currently processed for locally
     * imported packages, rather only for packages received over the network (e.g. sent by another
     * ATAK user)
     */
    public final static String PARAMETER_OnReceiveImport = "onReceiveImport";

    /**
     * Any content with this parameter set to "true" will be deleted from the map/filesystem
     * once the Mission Package is deleted
     * i.e. Remove temporary marker created specifically for Mission Package export
     */
    public final static String PARAMETER_DeleteWithPackage = "deleteWithPackage";

    /**
     * Enum driven by packages instructions for onReceiveDelete & onReceiveImport
     */
    public enum ImportInstructions {
        ImportDelete,
        ImportNoDelete,
        NoImportDelete,
        NoImportNoDelete;

        public boolean isImport() {
            return this == ImportDelete || this == ImportNoDelete;
        }

        public boolean isDelete() {
            return this == ImportDelete || this == NoImportDelete;
        }
    }

    /**
     * A custom intent may be sent on the receiving device once the Mission Package has been
     * extracted. The entire Manifest is sent as an extra in the intent, including any custom
     * parameters contained in the Manifest Configuration. Note, PARAMETER_OnReceiveDelete and
     * PARAMETER_OnReceiveImport are processed prior to generating the intent
     */
    public final static String PARAMETER_OnReceiveAction = "onReceiveAction";

    // Note name/uid are required for Mission Package, but not required
    // for MissionPackageContent subclass
    @ElementList(entry = "Parameter", inline = true, required = false)
    protected List<NameValuePair> _parameters;

    public MissionPackageConfiguration() {
        _parameters = new ArrayList<>();
    }

    public MissionPackageConfiguration(MissionPackageConfiguration copy) {
        _parameters = new ArrayList<>();
        for (NameValuePair p : copy.getParameters())
            _parameters.add(new NameValuePair(p));
    }

    public boolean isValid() {
        return _parameters != null && _parameters.size() > 1 &&
                hasParameter(PARAMETER_NAME) && hasParameter(PARAMETER_UID);
    }

    public void clear() {
        _parameters.clear();
    }

    /**
     * Get the import instructions
     * @return
     */
    public ImportInstructions getImportInstructions() {
        boolean bDelete = false;
        NameValuePair p = getParameter(
                MissionPackageConfiguration.PARAMETER_OnReceiveDelete);
        if (p != null && p.isValid())
            bDelete = Boolean.parseBoolean(p
                    .getValue());

        boolean bImport = true;
        p = getParameter(
                MissionPackageConfiguration.PARAMETER_OnReceiveImport);
        if (p != null && p.isValid())
            bImport = Boolean.parseBoolean(p
                    .getValue());

        if (bImport && bDelete)
            return ImportInstructions.ImportDelete;
        else if (bImport && !bDelete)
            return ImportInstructions.ImportNoDelete;
        else if (!bImport && bDelete)
            return ImportInstructions.NoImportDelete;
        else
            return ImportInstructions.NoImportNoDelete;
    }

    /**
     * Set the import instructions (to be processed by receiver of package)
     *
     * @param bImport   true to run extract files against Import Manager
     * @param bDelete   true to delete .zip after extraction
     * @param onReceiveAction Intent action to broadcast on receiver after extraction/deletion
     */
    public void setImportInstructions(boolean bImport, boolean bDelete,
            String onReceiveAction) {

        setParameter(new NameValuePair(
                MissionPackageConfiguration.PARAMETER_OnReceiveImport,
                Boolean.toString(bImport)));
        setParameter(new NameValuePair(
                MissionPackageConfiguration.PARAMETER_OnReceiveDelete,
                Boolean.toString(bDelete)));

        if (!FileSystemUtils.isEmpty(onReceiveAction)) {
            setParameter(new NameValuePair(
                    MissionPackageConfiguration.PARAMETER_OnReceiveAction,
                    onReceiveAction));
        }
    }

    public boolean hasParameter(String name) {
        return getParameter(name) != null;
    }

    public NameValuePair getParameter(String name) {
        if (FileSystemUtils.isEmpty(name))
            return null;

        for (NameValuePair p : _parameters) {
            if (p != null && p.isValid() && p.getName().equals(name))
                return p;
        }

        return null;
    }

    /**
     * Get a parameter's value and auto-cast to the provided return type
     * @param name Parameter name
     * @param <T> Return type
     * @return Parameter value or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameterValue(String name) {
        try {
            NameValuePair nvp = getParameter(name);
            return nvp != null ? (T) nvp.getValue() : null;
        } catch (Exception e) {
            // In the event the value does not match the return type, just null
            return null;
        }
    }

    public List<NameValuePair> getParameters() {
        return _parameters;
    }

    public void setParameter(NameValuePair parameter) {

        if (parameter == null || !parameter.isValid()) {
            Log.w(TAG,
                    "Ignoring invalid parameter: "
                            + (parameter == null ? "" : parameter.toString()));
            return;
        }

        for (NameValuePair p : _parameters) {
            if (p != null && parameter.getName().equals(p.getName())) {
                // found a match, just update existing parameter
                p.setValue(parameter.getValue());
                Log.d(TAG, "Updated parameter: " + p);
                return;
            }
        }

        _parameters.add(parameter);
    }

    public void setParameter(String name, String value) {
        setParameter(new NameValuePair(name, value));
    }

    public void removeParameter(String name) {
        NameValuePair p = getParameter(name);
        if (p == null)
            return;

        _parameters.remove(p);
    }

    @Override
    public int hashCode() {
        return ((_parameters == null) ? 0 : _parameters.hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MissionPackageConfiguration) {
            MissionPackageConfiguration c = (MissionPackageConfiguration) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(MissionPackageConfiguration rhsc) {
        // technically they could be invalid and equal, but we interested in valid ones
        if (!isValid() || !rhsc.isValid())
            return false;

        if (!FileSystemUtils.isEquals(_parameters, rhsc._parameters))
            return false;

        return true;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        if (isValid()) {
            parcel.writeInt(_parameters.size());
            if (_parameters.size() > 0) {
                for (NameValuePair parameter : _parameters)
                    parcel.writeParcelable(parameter, flags);
            }
        } else
            Log.w(TAG, "cannot parcel invalid: " + this);
    }

    protected MissionPackageConfiguration(Parcel in) {
        this();
        readFromParcel(in);
    }

    private void readFromParcel(Parcel in) {
        int count = in.readInt();
        for (int cur = 0; cur < count; cur++) {
            setParameter(in.readParcelable(NameValuePair.class
                    .getClassLoader()));
        }
    }

    public static final Parcelable.Creator<MissionPackageConfiguration> CREATOR = new Parcelable.Creator<MissionPackageConfiguration>() {
        @Override
        public MissionPackageConfiguration createFromParcel(Parcel in) {
            return new MissionPackageConfiguration(in);
        }

        @Override
        public MissionPackageConfiguration[] newArray(int size) {
            return new MissionPackageConfiguration[size];
        }
    };

    @Override
    public String toString() {
        return _parameters == null ? "" : "" + _parameters.size();
    }
}
