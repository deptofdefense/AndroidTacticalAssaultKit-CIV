
package com.atakmap.coremap.cot.event;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Locale;

import android.os.Parcel;
import android.os.Parcelable;

import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.coremap.maps.coords.GeoPoint;

import com.atakmap.coremap.filesystem.FileSystemUtils;

/**
 * Represents a Cursor on Target event
 * 
 * http://cot.mitre.org
 */
public class CotEvent implements Parcelable {

    private final static int _OPTIONAL_DETAIL_BIT = 1;
    private final static int _OPTIONAL_OPEX_BIT = 1 << 1;
    private final static int _OPTIONAL_ACCESS_BIT = 1 << 2;
    private final static int _OPTIONAL_QOS_BIT = 1 << 3;

    final static CotContentHandler cotHandler = new CotContentHandler();

    public static final String TAG = "CotEvent";
    static PrintWriter fileWriter = null;

    // required
    private String _uid;
    private String _type;
    private String _vers;
    private CotPoint _point;
    private CoordinatedTime _time;
    private CoordinatedTime _start;
    private CoordinatedTime _stale;
    private String _how;

    // optional
    private CotDetail _detail;
    private String _opex;
    private String _qos;
    private String _access;

    /**
     * Default <i>version</i> attribute value (2.0)
     */
    public static final String VERSION_2_0 = "2.0";

    /**
     * A good default and value of a machine generated <i>how</i> attribute
     */
    public static final String HOW_MACHINE_GENERATED = "m-g";

    /**
     * Value of a human generated <i>how</i> attribute
     */
    public static final String HOW_HUMAN_GARBAGE_IN_GARBAGE_OUT = "h-g-i-g-o";

    /**
     * Create a default state event. The default state is missing the required attributes 'uid',
     * 'type', 'time', 'start', and 'stale'; The required attributes must be set before the event is
     * valid (isValid()). The required inner point tag is defaulted to 0, 0 (CotPoint.ZERO_POINT) and no
     * detail tag exists.
     */
    public CotEvent() {
        _vers = VERSION_2_0;
        _point = CotPoint.ZERO; // for backwards compatibility bug #1029
    }

    /**
     * Copy constructor.
     */
    public CotEvent(final String _uid, final String _type, final String _vers,
            final CotPoint _point, final CoordinatedTime _time,
            final CoordinatedTime _start, final CoordinatedTime _stale,
            final String _how,
            final CotDetail _detail,
            final String _opex,
            final String _qos, final String _access) {

        this._uid = _uid;
        this._type = _type == null ? null : _type.trim();
        this._vers = _vers == null ? null : _vers.trim();
        this._point = _point;
        if (_detail != null) {
            this._detail = new CotDetail(_detail);
        }
        this._time = _time;
        this._start = _start;
        this._stale = _stale;
        this._how = _how == null ? null : _how.trim();
        this._qos = _qos;
        this._opex = _opex;
        this._access = _access;
    }

    /**
     * Copy constructor. That is not crazy long
     */
    public CotEvent(final CotEvent event) {

        this._uid = event._uid;
        this._type = event._type;
        this._vers = event._vers;
        if (event._point != null) {
            this._point = new CotPoint(event._point);
        }
        if (event._detail != null) {
            this._detail = new CotDetail(event._detail);
        }
        if (event._time != null) {
            this._time = new CoordinatedTime(event._time.getMilliseconds());
        }
        if (event._start != null) {
            this._start = new CoordinatedTime(event._start.getMilliseconds());
        }
        if (event._stale != null) {
            this._stale = new CoordinatedTime(event._stale.getMilliseconds());
        }

        this._how = event._how;
        this._qos = event._qos;
        this._opex = event._opex;
        this._access = event._access;
    }

    /**
     * Determine if the event is valid
     * 
     * @return the validity of the event.
     */
    public boolean isValid() {
        return _uid != null &&
                !_uid.trim().equals("") &&
                _type != null &&
                !_type.trim().equals("") &&
                _time != null &&
                _start != null &&
                _stale != null &&
                _how != null &&
                !_how.trim().equals("") &&
                _point != null &&
                (_point.getLat() >= -90 && _point.getLat() <= 90) &&
                (_point.getLon() >= -180 && _point.getLon() <= 180);
    }

    /**
     * Create the event from its Parcel representation.
     * 
     * @param source the source Parcel
     */
    public CotEvent(final Parcel source) {
        // required
        _uid = _readString(source);
        _type = _readString(source);
        _vers = _readString(source);
        _how = _readString(source);
        _point = CotPoint.CREATOR.createFromParcel(source);
        _time = CoordinatedTime.CREATOR.createFromParcel(source);
        _start = CoordinatedTime.CREATOR.createFromParcel(source);
        _stale = CoordinatedTime.CREATOR.createFromParcel(source);

        // optional
        int optionalBits = source.readInt();
        if ((optionalBits & _OPTIONAL_OPEX_BIT) != 0) {
            _opex = source.readString();
        }
        if ((optionalBits & _OPTIONAL_QOS_BIT) != 0) {
            _qos = source.readString();
        }
        if ((optionalBits & _OPTIONAL_ACCESS_BIT) != 0) {
            _access = source.readString();
        }
        if ((optionalBits & _OPTIONAL_DETAIL_BIT) != 0) {
            _detail = CotDetail.CREATOR.createFromParcel(source);
        }
    }

    /**
     * Retrieves a date as a formatted string useful for using as part of a file name.
     * 
     * @return a logging date/time string based on the current system time in yyyyMMdd_HHmm_ss
     *         format.
     */
    public static String getLogDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmm_ss",
                Locale.US);
        return sdf.format(CoordinatedTime.currentDate());
    }

    /**
     * Location to log invalid CoT messages. For the purposes of ATAK this should be
     * FileSystemUtils.getItem("cot");
     */
    synchronized public static void setLogInvalid(boolean log, File directory) {
        // if logging is enabled, create the file to log to.

        if (log) {
            // trying to enable logging twice, use the existing
            // file to log to instead of creating a new one
            if (fileWriter != null)
                return;

            // File f = FileSystemUtils.getItem("cot");
            if (!IOProviderFactory.exists(directory))
                if (!IOProviderFactory.mkdir(directory))
                    Log.w(TAG, "Failed to create directory");

            try {
                File lf = new File(directory, "cot_" + getLogDateString()
                        + ".log");
                fileWriter = new PrintWriter(new OutputStreamWriter(
                        IOProviderFactory.getOutputStream(lf),
                        FileSystemUtils.UTF8_CHARSET.name()));
            } catch (IOException uee) {
                Log.e(TAG, "error: ", uee);
            }
        } else {
            if (fileWriter != null)
                fileWriter.close();
            fileWriter = null;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {

        if (!isValid()) {
            Log.w(TAG, "Cannot parcel invalid Cot Event");
            return;
        }

        // required first
        _writeString(dest, _uid);
        _writeString(dest, _type);
        _writeString(dest, _vers);
        _writeString(dest, _how);
        _point.writeToParcel(dest, flags);
        _time.writeToParcel(dest, flags);
        _start.writeToParcel(dest, flags);
        _stale.writeToParcel(dest, flags);

        // optional last
        int optionalBits = 0;
        if (_opex != null && !_opex.equals("")) {
            optionalBits |= _OPTIONAL_OPEX_BIT;
        }
        if (_qos != null && !_qos.equals("")) {
            optionalBits |= _OPTIONAL_QOS_BIT;
        }
        if (_detail != null) {
            optionalBits |= _OPTIONAL_DETAIL_BIT;
        }
        if (_access != null) {
            optionalBits |= _OPTIONAL_ACCESS_BIT;
        }

        dest.writeInt(optionalBits);
        if ((optionalBits & _OPTIONAL_OPEX_BIT) != 0) {
            dest.writeString(_opex);
        }
        if ((optionalBits & _OPTIONAL_QOS_BIT) != 0) {
            dest.writeString(_qos);
        }
        if ((optionalBits & _OPTIONAL_ACCESS_BIT) != 0) {
            dest.writeString(_access);
        }
        if ((optionalBits & _OPTIONAL_DETAIL_BIT) != 0) {
            _detail.writeToParcel(dest, flags);
        }
    }

    /**
     * Get this 'version' attribute
     * 
     * @return a String indicating the version number
     */
    public String getVersion() {
        return _vers;
    }

    /**
     * Get this event 'type' attribute
     * 
     * @return the type attribute
     */
    public String getType() {
        return _type;
    }

    /**
     * Get this event UID
     * 
     * @return the uid
     */
    public String getUID() {
        return _uid;
    }

    /**
     * Get this event point as a CotPoint
     * 
     * @return the cot point.
     */
    public CotPoint getCotPoint() {
        return _point;
    }

    /**
     * From the CotPoint, derive the correct GeoPoint. (convienence method)
     * 
     * @return the GeoPoint derived from a CotPoint or null if the CotPoint is null.
     */
    public GeoPoint getGeoPoint() {
        if (_point != null)
            return _point.toGeoPoint();
        else
            return null;
    }

    /**
     * Get this event root detail
     * 
     * @return
     */
    public CotDetail getDetail() {
        return _detail;
    }

    /**
     * Find a detail element
     * Convenience method for {@link CotDetail#getFirstChildByName(int, String)}
     *
     * @param startIndex Child index to begin searching
     * @param name Detail name
     * @return CoT detail or null if not found
     */
    public CotDetail findDetail(int startIndex, String name) {
        if (_detail == null)
            return null;
        return _detail.getFirstChildByName(startIndex, name);
    }

    public CotDetail findDetail(String name) {
        return findDetail(0, name);
    }

    /**
     * Get this event time
     *
     * @return the time as a coordinated time.
     */
    public CoordinatedTime getTime() {
        return _time;
    }

    /**
     * Get this event start time
     *
     * @return the event start time as a coordinated time.
     */
    public CoordinatedTime getStart() {
        return _start;
    }

    /**
     * Get this event stale time
     * 
     * @return the stale time as a coordinated time.
     */
    public CoordinatedTime getStale() {
        return _stale;
    }

    /**
     * Get this event how
     * 
     * @return the HOW
     */
    public String getHow() {
        return _how;
    }

    /**
     * @return the OPEX
     */
    public String getOpex() {
        return _opex;
    }

    /**
     * @return the QoS
     */
    public String getQos() {
        return _qos;
    }

    /**
     * @return the Access flag
     */
    public String getAccess() {
        return _access;
    }

    /**
     * Given a string buffer, produce a well formed CoT message.
     * @param b the string buffer.
     */
    public void buildXml(final StringBuffer b) {
        try {
            this.buildXmlImpl(b);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Given a string buffer, produce a well formed CoT message.
     * @param b the string builder.
     */
    public void buildXml(final StringBuilder b) {
        try {
            this.buildXmlImpl(b);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return the xml representation as part of an appendable.
     */
    public void buildXml(Appendable b) throws IOException {
        this.buildXmlImpl(b);
    }

    private void buildXmlImpl(Appendable b) throws IOException {
        b.append(
                "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><event");
        if (_vers != null && !_vers.equals("")) {
            b.append(" version='");
            b.append(_vers);
            b.append("'");
        }
        b.append(" uid='");
        b.append(escapeXmlText(_uid));
        b.append("' type='");
        b.append(_type);
        b.append("' time='");
        b.append(_time.toString());
        b.append("' start='");
        b.append(_start.toString());
        b.append("' stale='");
        b.append(_stale.toString());
        b.append("' how='");
        b.append(_how);
        b.append("'");
        if (_opex != null) {
            b.append(" opex='");
            b.append(_opex);
            b.append("'");
        }
        if (_qos != null) {
            b.append(" qos='");
            b.append(_qos);
            b.append("'");
        }
        if (_access != null) {
            b.append(" access='");
            b.append(_access);
            b.append("'");
        }
        b.append(">");
        if (_point != null) {
            _point.buildXml(b);
        }
        if (_detail != null) {
            _detail.buildXml(b);
        }
        b.append("</event>");
    }

    /**
     * Parse a event from an XML string
     * 
     * @param xml
     * @return a CoT Event that can either be valid or invalid.
     */
    public static CotEvent parse(final String xml) {
        CotEvent e = cotHandler.parseXML(xml);

        //If the CotEvent is not valid, we should probably record it to a file if CotLogging is
        // enabled.
        synchronized (CotEvent.class) {
            if (fileWriter != null) {
                if (!e.isValid()) {
                    try {
                        fileWriter.println(xml);
                    } catch (Exception ex) {
                        // instead of synchronizing this to death, just catch the
                        // potential npe.
                    }
                }
            }
        }
        return e;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        buildXml(sb);
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    private static void _writeString(Parcel dest, String v) {
        if (v == null) {
            v = "";
        }
        dest.writeString(v);
    }

    private static String _readString(Parcel source) {
        String v = source.readString();
        return v.equals("") ? null : v;
    }

    public static String escapeXmlText(final String innerText) {

        if (innerText == null) {
            return "";
        }

        final int len = innerText.length();

        boolean found = false;
        for (int i = 0; i < len && !found; ++i) {
            final char ch = innerText.charAt(i);
            switch (ch) {
                case '&':
                case '<':
                case '>':
                case '"':
                case '\'':
                case '\n':
                    found = true;
                default:
            }

        }
        if (!found)
            return innerText;

        final StringBuilder sb = new StringBuilder((int) (len * 1.5));
        for (int i = 0; i < len; ++i) {
            final char ch = innerText.charAt(i);
            switch (ch) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                case '\n':
                    sb.append("&#10;");
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    public static final Parcelable.Creator<CotEvent> CREATOR = new Parcelable.Creator<CotEvent>() {

        @Override
        public CotEvent createFromParcel(Parcel source) {
            return new CotEvent(source);
        }

        @Override
        public CotEvent[] newArray(int size) {
            return new CotEvent[size];
        }
    };

    /**
     * Set the version attribute. If not set, the value by default is "2.0".
     * 
     * @param vers the version if it is other than 2.0
     */
    public void setVersion(String vers) {
        if (vers != null)
            vers = vers.trim();

        if (vers == null || vers.equals("")) {
            throw new IllegalArgumentException("version may not be nothing");
        }
        _vers = vers;
    }

    /**
     * Set the CoT type (e.g. a-f-G).
     * 
     * @param type the type of the CoT event
     */
    public void setType(String type) {
        if (type != null)
            type = type.trim();

        if (type == null || type.equals("")) {
            throw new IllegalArgumentException("type may not be nothing");
        }
        _type = type;
    }

    /**
     * Set the unique identifier for the object the event describes
     * 
     * @param uid the unique identifier.   Should be opaque and not used for interpretation.
     */
    public void setUID(String uid) {
        if (uid == null || uid.trim().equals("")) {
            throw new IllegalArgumentException("uid may not be nothing");
        }
        _uid = uid;
    }

    /**
     * Set the point tag details
     * 
     * @throws IllegalArgumentException if point is null
     * @param point the point
     */
    public void setPoint(CotPoint point) {
        if (point == null) {
            throw new IllegalArgumentException(
                    "point attribute may not be null");
        }
        _point = point;
    }

    /**
     * Set the detail tag. This must be named "detail" or be null.
     * 
     * @throws IllegalArgumentException if the CotDetail element name isn't "detail"
     * @param detail the detail tag
     */
    public void setDetail(CotDetail detail) {
        if (detail != null && detail.getElementName() != null
                && !detail.getElementName().equals("detail")) {
            throw new IllegalArgumentException(
                    "detail tag must be named 'detail' (got '"
                            + detail.getElementName() + "'");
        }
        _detail = detail;

    }

    /**
     * Set the time this event was generated
     * 
     * @param time the time based on coordinated time.
     */
    public void setTime(final CoordinatedTime time) {

        if (time == null) {
            throw new IllegalArgumentException(
                    "time attribute must not be null");
        }
        _time = time;
    }

    /**
     * Set the time this event starts scope
     * 
     * @param start the start time of the event.
     */
    public void setStart(final CoordinatedTime start) {
        if (start == null) {
            throw new IllegalArgumentException(
                    "start attribute must not be null");
        }
        _start = start;
    }

    /**
     * Set the time this event leaves from scope
     * 
     * @param stale the stale time of the event
     */
    public void setStale(final CoordinatedTime stale) {
        if (stale == null) {
            throw new IllegalArgumentException(
                    "stale attribute must not be null");
        }
        _stale = stale;
    }

    /**
     * Set the 'how' attribute of the event (e.g. m-g)
     * 
     * @param how the how for the event.
     */
    public void setHow(String how) {
        if (how != null)
            how = how.trim();

        if (how == null || how.equals("")) {
            // we used to be less permissive and throw a IllegalStateException if the 
            // how field was incorrect - now we should just flag it as machine-generated-garbage
            how = "m-g-g";
        }
        _how = how;
    }

    /**
     * Set the 'opex' attribute of the event
     * 
     * @param opex
     */
    public void setOpex(String opex) {
        _opex = opex;
    }

    /**
     * Set the 'qos' (quality of service) attribute of the event
     * 
     * @param qos
     */
    public void setQos(String qos) {
        _qos = qos;
    }

    /**
     * Set the 'access' attribute of the event
     * 
     * @param access
     */
    public void setAccess(String access) {
        _access = access;
    }

}
