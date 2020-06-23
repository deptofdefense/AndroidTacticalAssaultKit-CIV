
package com.atakmap.android.importfiles.resource;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.cot.event.CotPoint;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;

import java.util.ArrayList;
import java.util.List;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.UUID;

/**
 * Configuration of a remote resource e.g. KML or imagery on a network server Converts to/from XML
 * and CoT
 * 
 * 
 */
public class RemoteResource implements Parcelable {

    private static final String TAG = "RemoteResource";

    public static final String COT_TYPE = "b-i-r-r"; // (bits-imagery-remote-resource)
    private static final String COT_HOW = "h-g";

    /**
     * KML has some special treatment, e.g. interval refreshing of the file OTHER has basic remote
     * import support
     * 
     * 
     */
    public enum Type {
        KML,
        OTHER
    }

    @Element
    private String name;

    @Element
    private String type;

    @Element
    private String url;

    @Element(required = false)
    private String localPath;

    @Element(required = false)
    private String md5;

    @Element
    private boolean deleteOnExit;

    @Element(required = false)
    private Long refreshSeconds;

    /**
     * Based on Video ConnectionEntry to track if this resource came from internal or external SD
     * card
     * 
     * 
     */
    public enum Source {
        LOCAL_STORAGE,
        EXTERNAL
    }

    private Source source = Source.LOCAL_STORAGE;

    /**
     * Java epoch millis Not serialized to XML, used to hold state while ATAK is running
     */
    private long lastRefreshed;

    @ElementList(entry = "ChildResource", inline = true, required = false)
    private List<ChildResource> childResources;

    public void clearChildren() {
        if (childResources != null)
            childResources.clear();
    }

    public void addChild(ChildResource child) {
        if (child == null)
            Log.w(TAG, "Discarding null child");

        List<ChildResource> children = getChildren();
        if (!children.contains(child))
            children.add(child);
    }

    public List<ChildResource> getChildren() {
        if (childResources == null)
            childResources = new ArrayList<>();

        return childResources;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public boolean isKML() {
        return isKML(getType());
    }

    public static boolean isKML(String t) {
        return RemoteResource.Type.KML.toString().equals(t) ||
                "KMZ".equalsIgnoreCase(t);
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public boolean isDeleteOnExit() {
        return deleteOnExit;
    }

    public void setDeleteOnExit(boolean deleteOnExit) {
        this.deleteOnExit = deleteOnExit;
    }

    public long getRefreshSeconds() {
        return refreshSeconds == null ? 0 : refreshSeconds;
    }

    public void setRefreshSeconds(long refreshSeconds) {
        this.refreshSeconds = refreshSeconds;
    }

    public long getLastRefreshed() {
        return lastRefreshed;
    }

    public void setLastRefreshed(long lastRefreshed) {
        this.lastRefreshed = lastRefreshed;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }

    public boolean isValid() {
        return !(FileSystemUtils.isEmpty(name) ||
                FileSystemUtils.isEmpty(type) || FileSystemUtils.isEmpty(url));

    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RemoteResource) {
            RemoteResource c = (RemoteResource) o;
            return this.equals(c);
        } else {
            return super.equals(o);
        }
    }

    public boolean equals(RemoteResource c) {
        // Only match on user editable values (plus type which is not)
        if (!FileSystemUtils.isEquals(getName(), c.getName()))
            return false;

        if (!FileSystemUtils.isEquals(getUrl(), c.getUrl()))
            return false;

        if (isDeleteOnExit() != c.isDeleteOnExit())
            return false;

        if (getRefreshSeconds() != c.getRefreshSeconds())
            return false;

        if (!FileSystemUtils.isEquals(getType(), c.getType()))
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return 31 * ((getName() == null ? 0 : getName().hashCode())
                + (getUrl() == null ? 0
                        : getUrl().hashCode()));
    }

    @Override
    public String toString() {
        return String.format(LocaleUtil.getCurrent(), "%s %s %s %s %d %d",
                getName(),
                getType(), getUrl(),
                getLocalPath(),
                getRefreshSeconds(), getChildren().size());
    }

    /**
     * converts this AssetMarker and all the information associated with it into a CotEvent
     */
    public CotEvent toCot(String callsign, CotPoint point) {
        if (!isValid()) {
            Log.w(TAG, "Unable to convert invalid resource to CoT");
            return null;
        }

        CotEvent cotEvent = new CotEvent();
        cotEvent.setUID(UUID.randomUUID().toString());
        cotEvent.setType(COT_TYPE);
        cotEvent.setHow(COT_HOW);
        cotEvent.setVersion("2.0");
        if (point != null)
            cotEvent.setPoint(point);
        else
            cotEvent.setPoint(CotPoint.ZERO);
        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addHours(1));

        CotDetail detail = new CotDetail("detail");

        CotDetail callsignDetail = new CotDetail("contact");
        callsignDetail.setAttribute("callsign", callsign);
        detail.addChild(callsignDetail);

        CotDetail rrs = new CotDetail("RemoteResources");

        // add the xml as a detail
        CotDetail rr = new CotDetail("RemoteResource");

        rr.setAttribute("name", getName());
        rr.setAttribute("url", getUrl());
        rr.setAttribute("type", getType());
        rr.setAttribute("deleteOnExit", String.valueOf(isDeleteOnExit()));
        rr.setAttribute("refreshSeconds", String.valueOf(getRefreshSeconds()));

        rrs.addChild(rr);
        detail.addChild(rrs);
        cotEvent.setDetail(detail);

        return cotEvent;
    }

    public static RemoteResource fromCoT(CotEvent from) {
        CotDetail remoteResources = from.getDetail().getFirstChildByName(0,
                "RemoteResources");
        if (remoteResources == null) {
            Log.w(TAG,
                    "Unable to create RemoteResource from CoT without RemoteResources detail");
            return null;
        }

        CotDetail remoteResource = remoteResources.getFirstChildByName(0,
                "RemoteResource");
        if (remoteResource == null) {
            Log.w(TAG,
                    "Unable to create RemoteResource from CoT without RemoteResource detail");
            return null;
        }

        String name = remoteResource.getAttribute("name");
        String url = remoteResource.getAttribute("url");
        String type = remoteResource.getAttribute("type");
        boolean deleteOnExit = Boolean.parseBoolean(remoteResource
                .getAttribute("deleteOnExit"));
        long refreshSeconds = Long.parseLong(remoteResource
                .getAttribute("refreshSeconds"));

        RemoteResource resource = new RemoteResource();
        resource.setName(name);
        resource.setUrl(url);
        resource.setType(type);
        resource.setDeleteOnExit(deleteOnExit);
        resource.setRefreshSeconds(refreshSeconds);

        return resource;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(name);
        parcel.writeString(url);
        parcel.writeString(type);
        parcel.writeByte((byte) (deleteOnExit ? 1 : 0));
        parcel.writeLong(refreshSeconds);
        parcel.writeString(localPath);
        parcel.writeString(md5);
        parcel.writeByte((byte) (source == Source.LOCAL_STORAGE ? 1 : 0));
        parcel.writeLong(lastRefreshed);
        int cnt = getChildren().size();
        parcel.writeInt(cnt);
        for (ChildResource child : getChildren())
            parcel.writeParcelable(child, flags);
        // TODO remove logging after testing
        // Log.d(TAG, "writeToParcel child count: " + cnt);
    }

    public static final Parcelable.Creator<RemoteResource> CREATOR = new Parcelable.Creator<RemoteResource>() {
        @Override
        public RemoteResource createFromParcel(Parcel in) {
            RemoteResource ret = new RemoteResource();
            ret.setName(in.readString());
            ret.setUrl(in.readString());
            ret.setType(in.readString());
            ret.setDeleteOnExit(in.readByte() == 0 ? false : true);
            ret.setRefreshSeconds(in.readLong());
            ret.setLocalPath(in.readString());
            ret.setMd5(in.readString());
            ret.setSource(in.readByte() == 1 ? Source.LOCAL_STORAGE
                    : Source.EXTERNAL);
            ret.setLastRefreshed(in.readLong());
            int size = in.readInt();
            for (int i = 0; i < size; i++) {
                ChildResource child = in
                        .readParcelable(ChildResource.class
                                .getClassLoader());
                if (child == null) {
                    Log.w(TAG, "Failed to read in child RemoteResource");
                    continue;
                }

                ret.addChild(child);
            }
            // TODO remove logging after testing
            // Log.d(TAG, "readParcelable child count: " + size);
            return ret;
        }

        @Override
        public RemoteResource[] newArray(int size) {
            return new RemoteResource[size];
        }
    };
}
