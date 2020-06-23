
package com.atakmap.android.cot.detail;

import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.comms.CommsMapComponent.ImportResult;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

/**
 *
 *
 * e.g.  <takv platform="ATAK" version="3.5.21056" device="SAMSUNG SM-N910V" os="23"/>
 * Where
 *  "ATAK" is hardcoded
 *  "3.5" is from the manifest versionName
 *  "." is hardcoded (between versionName and versionCode)
 *  "21056" is from the manifest versionCode
 */
public class TakVersionDetailHandler extends CotDetailHandler {

    private final static String TAG = "TakVersionDetailHandler";

    public final static String VERSION_DETAIL = "takv";

    public final static String ATTR_PLATFORM = "platform";
    private final static String META_PLATFORM = "takv_platform";

    public final static String ATTR_VERSION = "version";
    private final static String META_VERSION = "takv_version";

    public final static String ATTR_DEVICE = "device";
    private final static String META_DEVICE = "takv_device";

    public final static String ATTR_OS = "os";
    private final static String META_OS = "takv_os";

    private static final CharSequence tak = "tak";
    private static final CharSequence TAK = "TAK";

    public enum Platform {
        ATAK,
        WinTAK,
        iTAK
    }

    TakVersionDetailHandler() {
        super(VERSION_DETAIL);
    }

    @Override
    public ImportResult toItemMetadata(MapItem item, CotEvent event,
            CotDetail detail) {
        String platform = detail.getAttribute(ATTR_PLATFORM);
        if (!FileSystemUtils.isEmpty(platform))
            item.setMetaString(META_PLATFORM, platform);

        String version = detail.getAttribute(ATTR_VERSION);
        if (!FileSystemUtils.isEmpty(version))
            item.setMetaString(META_VERSION, version);

        String device = detail.getAttribute(ATTR_DEVICE);
        if (!FileSystemUtils.isEmpty(device))
            item.setMetaString(META_DEVICE, device);

        String os = detail.getAttribute(ATTR_OS);
        if (!FileSystemUtils.isEmpty(os))
            item.setMetaString(META_OS, os);

        return ImportResult.SUCCESS;
    }

    @Override
    public boolean toCotDetail(MapItem item, CotEvent event, CotDetail detail) {
        //version sending currently only occurs for self SA, and is handled by CotMapComponent
        return false;
    }

    public static String getVersion(PointMapItem marker) {
        if (marker == null || !marker.hasMetaValue(META_PLATFORM))
            return null;

        String s = marker.getMetaString(META_PLATFORM, "");
        Platform p = getPlatform(s);
        if (p == null) {
            //was not proper casing, or was unknown
            s = prettyPrintPlatform(s);
        }

        if (marker.hasMetaValue(META_VERSION))
            s += " " + marker.getMetaString(META_VERSION, "");

        return s;
    }

    public static String getVersion() {
        if (FileSystemUtils.isEmpty(ATAKConstants.getVersionNameManifest())) {
            Log.w(TAG, "Manifest version not yet set");
            return "";
        }

        return ATAKConstants.getVersionNameManifest() + "."
                + ATAKConstants.getVersionCode();
    }

    public static String getDevice(PointMapItem marker) {
        if (marker == null || !marker.hasMetaValue(META_DEVICE))
            return null;

        return marker.getMetaString(META_DEVICE, "");
    }

    /**
     * Given a marker, return the devices OS.
     * @param marker the marker
     * @return null if no os was provided or the OS as a string.
     */
    public static String getDeviceOs(PointMapItem marker) {
        if (marker == null || !marker.hasMetaValue(META_OS))
            return null;

        return marker.getMetaString(META_OS, "");
    }

    public static String getDeviceOS() {
        if (FileSystemUtils.isEmpty(ATAKConstants.getDeviceOS())) {
            Log.w(TAG, "OS version not yet set");
            return "";
        }

        return ATAKConstants.getDeviceOS();
    }

    public static String getDeviceDescription() {
        String value = ATAKConstants.getDeviceManufacturer();
        if (!FileSystemUtils.isEmpty(value))
            value += " ";
        value += ATAKConstants.getDeviceModel();
        if (!FileSystemUtils.isEmpty(value))
            value = value.toUpperCase(LocaleUtil.getCurrent());
        return value;
    }

    private static Platform getPlatform(String platform) {
        if (FileSystemUtils.isEmpty(platform))
            return null;

        try {
            return Platform.valueOf(platform);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Unknown platform: " + platform);
        }

        return null;
    }

    private static String prettyPrintPlatform(String platform) {
        if (FileSystemUtils.isEmpty(platform))
            return null;

        //go all lower case
        String s = platform.toLowerCase(LocaleUtil.getCurrent());

        //see if includes "tak"
        if (!s.contains(tak))
            return platform;

        //first letter uppercase
        String c = s.substring(0, 1);

        s = c.toUpperCase(LocaleUtil.getCurrent()) + s.substring(1);

        //go TAK
        s = s.replace(tak, TAK);
        return s;
    }
}
