
package com.atakmap.android.video.manager;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.math.MathUtils;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.android.video.ConnectionEntry.Protocol;
import com.atakmap.android.video.ConnectionEntry.Source;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.atakmap.coremap.maps.time.CoordinatedTime;
import com.atakmap.coremap.xml.XMLUtils;
import com.atakmap.net.AtakAuthenticationCredentials;
import com.atakmap.net.AtakAuthenticationDatabase;

import com.atakmap.util.zip.IoUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Handles serialization and deserialization of connection entry XML
 */
public class VideoXMLHandler {

    private static final String TAG = "VideoXMLHandler";
    private static final String XML_HEADER = "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>";

    private final DocumentBuilder _docBuilder;

    public VideoXMLHandler() {
        DocumentBuilder builder = null;
        try {
            DocumentBuilderFactory factory = XMLUtils
                    .getDocumenBuilderFactory();

            builder = factory.newDocumentBuilder();
        } catch (Exception e) {
            Log.e(TAG, "Failed to create document builder", e);
        }
        _docBuilder = builder;
    }

    /**
     * Parse a connection entry or list of connection entries
     * Works for both cases
     * Synchronized to prevent reading and writing at the same time
     *
     * @param file Connection entry file
     * @return List of connection entries
     */
    public synchronized List<ConnectionEntry> parse(File file) {
        List<ConnectionEntry> ret = new ArrayList<>();
        if (_docBuilder == null)
            return ret;
        try (FileInputStream is = IOProviderFactory.getInputStream(file)) {
            ret = parse(_docBuilder.parse(is));
            for (ConnectionEntry e : ret)
                e.setLocalFile(file);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse connection entry file: " + file, e);
        }
        return ret;
    }

    public synchronized List<ConnectionEntry> parse(String xml) {
        List<ConnectionEntry> ret = new ArrayList<>();
        if (_docBuilder == null)
            return ret;
        InputStream is = null;
        try {
            is = new ByteArrayInputStream(
                    xml.getBytes(FileSystemUtils.UTF8_CHARSET));
            ret = parse(_docBuilder.parse(is));
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse connection entry XML", e);
        } finally {
            try {
                if (is != null)
                    is.close();
            } catch (Exception ignore) {
            }
        }
        return ret;
    }

    private List<ConnectionEntry> parse(Document dom) {
        List<ConnectionEntry> ret = new ArrayList<>();
        if (dom == null)
            return ret;
        NodeList children = dom.getChildNodes();
        if (children == null)
            return ret;
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element))
                continue;
            List<ConnectionEntry> entries = parse((Element) n);
            if (entries != null)
                ret.addAll(entries);
        }
        return ret;
    }

    /**
     * Serialize a single connection entry to an XML file
     * Synchronized to prevent reading and writing at the same time
     *
     * @param entry Connection entry
     * @param file XML file
     * @return File or null if failed
     */
    public synchronized File write(ConnectionEntry entry, File file) {
        FileOutputStream fos = null;
        try {
            String xml = serialize(entry, null);
            if (FileSystemUtils.isEmpty(xml))
                return null;
            if (IOProviderFactory.exists(file))
                FileSystemUtils.delete(file);
            fos = IOProviderFactory.getOutputStream(file);
            FileSystemUtils.write(fos, xml);
            fos = null;

            // Save any passphrase to the auth database
            if (entry.getProtocol() == Protocol.SRT
                    && entry.getPassphrase() != null
                    && entry.getPassphrase().length() > 0)
                AtakAuthenticationDatabase.saveCredentials(
                        AtakAuthenticationCredentials.TYPE_videoPassword,
                        entry.getUID(), "", entry.getPassphrase(), false);

            return file;
        } catch (Exception e) {
            Log.e(TAG, "Failed to serialize connection entry: " + entry, e);
        } finally {
            IoUtils.close(fos);
        }
        return null;
    }

    private static List<ConnectionEntry> parse(Element el) {
        if (el == null)
            return null;

        if (el.getTagName().equalsIgnoreCase("videoConnections")) {
            List<ConnectionEntry> ret = new ArrayList<>();
            NodeList multiple = el.getChildNodes();
            for (int i = 0; i < multiple.getLength(); i++) {
                Node n = multiple.item(i);
                if (!(n instanceof Element))
                    continue;
                ConnectionEntry entry = parseFeed((Element) n);
                if (entry != null)
                    ret.add(entry);
            }
            return ret;
        } else {
            ConnectionEntry entry = parseFeed(el);
            if (entry != null)
                return Collections.singletonList(entry);
        }
        return null;
    }

    private static ConnectionEntry parseFeed(Element feed) {
        if (!feed.getTagName().equalsIgnoreCase("feed"))
            return null;
        Protocol proto = null;
        String alias = null;
        String uid = null;
        String address = null;
        int port = -1;
        int roverPort = -1;
        boolean ignoreKLV = false;
        String preferredMacAddress = null;
        String preferredInterfaceAddress = null;
        String path = null;
        int buffer = -1;
        int timeout = 5000;
        int rtspReliable = 0;
        NodeList children = feed.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element))
                continue;
            Element el = (Element) n;
            String k = el.getTagName();
            String v = el.getTextContent();

            // XXX - break break break break break break... Yuck
            // Switch blocks could've been nice if the breaks were implicit
            // after one or more lines of code per case
            switch (k) {
                case "protocol":
                    proto = Protocol.fromString(v);
                    break;
                case "alias":
                    alias = v;
                    break;
                case "uid":
                    uid = v;
                    break;
                case "address":
                    address = v;
                    break;
                case "port":
                    port = parseInt(v);
                    break;
                case "roverPort":
                    roverPort = parseInt(v);
                    break;
                case "ignoreEmbeddedKLV":
                    ignoreKLV = Boolean.parseBoolean(v);
                    break;
                case "preferredMacAddress":
                    preferredMacAddress = v;
                    break;
                case "preferredInterfaceAddress":
                    preferredInterfaceAddress = v;
                    break;
                case "path":
                    path = v;
                    break;
                case "buffer":
                    buffer = parseInt(v);
                    break;
                case "timeout":
                    timeout = parseInt(v);
                    break;
                case "rtspReliable":
                    rtspReliable = parseInt(v);
                    break;
            }
        }
        if (proto == null || FileSystemUtils.isEmpty(alias)
                || FileSystemUtils.isEmpty(uid))
            return null;
        Source source = proto == Protocol.FILE || proto == Protocol.DIRECTORY
                ? Source.LOCAL_STORAGE
                : Source.EXTERNAL;
        String pass = "";
        if (proto == Protocol.SRT) {
            AtakAuthenticationCredentials creds = AtakAuthenticationDatabase
                    .getCredentials(
                            AtakAuthenticationCredentials.TYPE_videoPassword,
                            uid);
            if (creds != null)
                pass = creds.password;
        }
        ConnectionEntry entry = new ConnectionEntry(alias, address,
                port, roverPort, path, proto, timeout,
                buffer, rtspReliable, pass, source);
        entry.setMacAddress(preferredMacAddress);
        entry.setPreferredInterfaceAddress(preferredInterfaceAddress);
        entry.setUID(uid);
        entry.setIgnoreEmbeddedKLV(ignoreKLV);
        return entry;
    }

    private static int parseInt(String v) {
        return MathUtils.parseInt(v, -1);
    }

    /**
     * Serialize a connection entry to XML
     *
     * @param e Connection entry
     * @param sb String builder (null if this is a singular entry)
     * @return Feed XML
     */
    public static String serialize(ConnectionEntry e, StringBuilder sb) {
        boolean single = false;
        if (sb == null) {
            sb = new StringBuilder();
            sb.append(XML_HEADER).append("\n");
            single = true;
        }
        sb.append("<feed>\n");
        add(sb, "protocol", e.getProtocol().toString());
        add(sb, "alias", e.getAlias());
        add(sb, "uid", e.getUID());
        add(sb, "address", e.getAddress());
        add(sb, "port", e.getPort());
        add(sb, "roverPort", e.getRoverPort());
        add(sb, "ignoreEmbeddedKLV", e.getIgnoreEmbeddedKLV());
        add(sb, "preferredMacAddress", e.getMacAddress());
        add(sb, "preferredInterfaceAddress", e.getPreferredInterfaceAddress());
        add(sb, "path", e.getPath());
        add(sb, "buffer", e.getBufferTime());
        add(sb, "timeout", e.getNetworkTimeout());
        add(sb, "rtspReliable", e.getRtspReliable());
        sb.append("</feed>\n");
        return single ? sb.toString() : null;
    }

    public static String serialize(List<ConnectionEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append(XML_HEADER).append("\n");
        sb.append("<videoConnections>\n");
        for (ConnectionEntry entry : entries)
            serialize(entry, sb);
        sb.append("</videoConnections>\n");
        return sb.toString();
    }

    private static void add(StringBuilder sb, String k, Object v) {
        sb.append("<").append(k);
        if (v != null) {
            String str = String.valueOf(v);
            if (!FileSystemUtils.isEmpty(str)) {
                sb.append(">");
                if (v instanceof String)
                    str = CotEvent.escapeXmlText(str);
                sb.append(str);
                sb.append("</").append(k).append(">\n");
                return;
            }
        }
        sb.append("/>\n");
    }

    public static CotEvent toCotEvent(ConnectionEntry ce) {
        CotEvent cotEvent = new CotEvent();
        cotEvent.setUID(ce.getUID());
        cotEvent.setType("b-i-v");
        cotEvent.setVersion("2.0");
        cotEvent.setHow("m-g");
        CoordinatedTime time = new CoordinatedTime();
        cotEvent.setTime(time);
        cotEvent.setStart(time);
        cotEvent.setStale(time.addHours(1));

        CotDetail detail = new CotDetail("detail");

        CotDetail callsign = new CotDetail("contact");
        callsign.setAttribute("callsign", ce.getAlias());

        detail.addChild(callsign);

        CotDetail link = new CotDetail("link");
        link.setAttribute("uid", ce.getUID());
        link.setAttribute("production_time", new CoordinatedTime().toString());
        link.setAttribute("relationship", "p-p");
        link.setAttribute("parent_callsign", MapView.getMapView()
                .getDeviceCallsign());

        detail.addChild(link);

        CotDetail vid = new CotDetail("__video");
        vid.addChild(toCotDetail(ce));
        detail.addChild(vid);
        cotEvent.setDetail(detail);
        return cotEvent;
    }

    public static CotDetail toCotDetail(ConnectionEntry ce) {
        // add the xml as a detail
        CotDetail aliasDetail = new CotDetail("ConnectionEntry");

        // attach each part of the connection entry as it's own string
        // it's much easier to turn back into a ConnectionEntry Object than
        // trying to decode the string val of the Connection Entry
        aliasDetail.setAttribute("address", ce.getAddress());
        aliasDetail.setAttribute("uid", ce.getUID());
        aliasDetail.setAttribute("alias", ce.getAlias());
        aliasDetail.setAttribute("port", String.valueOf(ce.getPort()));
        aliasDetail
                .setAttribute("roverPort", String.valueOf(ce.getRoverPort()));
        aliasDetail.setAttribute("rtspReliable",
                String.valueOf(ce.getRtspReliable()));
        aliasDetail.setAttribute("ignoreEmbeddedKLV",
                String.valueOf(ce.getIgnoreEmbeddedKLV()));
        aliasDetail.setAttribute("path", ce.getPath());
        aliasDetail.setAttribute("protocol", ce.getProtocol().toString());
        aliasDetail.setAttribute("networkTimeout",
                String.valueOf(ce.getNetworkTimeout()));
        aliasDetail.setAttribute("bufferTime",
                String.valueOf(ce.getBufferTime()));

        return aliasDetail;
    }
}
