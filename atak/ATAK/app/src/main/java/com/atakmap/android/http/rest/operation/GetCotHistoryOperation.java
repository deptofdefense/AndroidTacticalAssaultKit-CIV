
package com.atakmap.android.http.rest.operation;

import android.content.Context;
import android.os.Bundle;

import com.atakmap.android.http.rest.request.GetCotHistoryRequest;
import com.atakmap.comms.http.HttpUtil;
import com.atakmap.comms.http.TakHttpClient;
import com.atakmap.comms.http.TakHttpException;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import com.atakmap.spatial.kml.KMLUtil;
import com.foxykeep.datadroid.exception.ConnectionException;
import com.foxykeep.datadroid.exception.DataException;
import com.foxykeep.datadroid.requestmanager.Request;

import org.apache.http.HttpStatus;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Generic operation to GET last CoT Event for a given UID
 */
public class GetCotHistoryOperation extends HTTPOperation {
    private static final String TAG = "GetCotHistoryOperation";

    public static final String PARAM_REQUEST = GetCotHistoryOperation.class
            .getName() + ".PARAM_REQUEST";
    public static final String PARAM_RESPONSE = GetCotHistoryOperation.class
            .getName() + ".PARAM_RESPONSE";
    private static final long ALL_EVENTS = -1;

    @Override
    public Bundle execute(Context context, Request request)
            throws ConnectionException,
            DataException {

        // Get request data
        GetCotHistoryRequest queryRequest = null;

        try {
            queryRequest = GetCotHistoryRequest.fromJSON(
                    new JSONObject(request.getString(
                            GetCotHistoryOperation.PARAM_REQUEST)));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to serialize JSON", e);
        }

        if (queryRequest == null) {
            throw new DataException("Unable to serialize query request");
        }

        if (!queryRequest.isValid()) {
            throw new DataException(
                    "Unable to serialize invalid query request");
        }

        return query(queryRequest);
    }

    private Bundle query(GetCotHistoryRequest req)
            throws ConnectionException,
            DataException {

        TakHttpClient client = null;
        try {
            client = TakHttpClient.GetHttpClient(req.getBaseUrl());

            StringBuilder queryUrl = new StringBuilder(
                    client.getUrl("/api/cot/xml/"))
                            .append(req.getUID())
                            .append("/all");

            boolean firstParam = true;
            if (req.getStartTime() > -1) {
                queryUrl.append("?start=");
                queryUrl.append(KMLUtil.KMLDateTimeFormatterMillis.get()
                        .format(req.getStartTime()));
                firstParam = false;
            }
            if (req.getEndTime() > -1) {
                queryUrl.append(firstParam ? "?" : "&");
                queryUrl.append("end=");
                queryUrl.append(KMLUtil.KMLDateTimeFormatterMillis.get()
                        .format(req.getEndTime()));
            }
            String squeryUrl = FileSystemUtils.sanitizeURL(queryUrl.toString());

            String xml = client.getGZip(squeryUrl, req.getMatcher(),
                    HttpUtil.MIME_XML);

            Bundle output = new Bundle();
            output.putParcelable(PARAM_REQUEST, req);
            if (req.getParseEvents()) {
                // Parse Cot events list
                List<CotEvent> cotEvents = parseCotEvents(xml, req.getMatcher(),
                        ALL_EVENTS, null);
                if (FileSystemUtils.isEmpty(cotEvents)) {
                    Log.e(TAG, "Failed to parse empty events list: " + xml,
                            new IOException());
                    return null;
                }
                Log.d(TAG, "Parsed CoT history of size: " + xml.length());
                output.putSerializable(PARAM_RESPONSE, cotEvents
                        .toArray(new CotEvent[0]));
            } else {
                // Include the XML string in the response for parsing elsewhere
                output.putString(PARAM_RESPONSE, xml);
            }
            output.putInt(NetworkOperation.PARAM_STATUSCODE, HttpStatus.SC_OK);
            return output;
        } catch (TakHttpException e) {
            Log.e(TAG, "Failed to query CoT Events: " + req.getUID(), e);
            throw new ConnectionException(e.getMessage(), e.getStatusCode());
        } catch (Exception e) {
            Log.e(TAG, "Failed to query CoT Event: " + req.getUID(), e);
            throw new ConnectionException(e.getMessage(),
                    NetworkOperation.STATUSCODE_UNKNOWN);
        } finally {
            try {
                if (client != null)
                    client.shutdown();
            } catch (Exception e) {
                Log.e(TAG, "Failed to shutdown the client", e);
            }
        }
    }

    /**
     * Parse events
     * Optionally filter out those before start time
     * Optionally filter out those not on the whitelist
     *
     * @param xml the xml file to parse from the response body
     * @param matcher the substring the xml needs to contain for validation purposes.
     * @param startTime the start time to remove outdated xml messages
     * @param whiteList white list of uids that are always allowed.
     * @return
     */
    public static List<CotEvent> parseCotEvents(String xml, String matcher,
            final long startTime, final List<String> whiteList) {
        if (FileSystemUtils.isEmpty(xml)) {
            Log.w(TAG, "Failed to parse response body");
            return null;
        }

        //validate expected contents
        if (!xml.contains(matcher)) {
            Log.e(TAG, "Failed to parse response body: " + xml,
                    new IOException());
            return null;
        }

        final boolean bCheckWhitelist = !FileSystemUtils.isEmpty(whiteList);
        final List<CotEvent> cotEvents = new ArrayList<>();
        parseCotEvents(xml, new ParseCallback() {
            @Override
            public boolean onEventParsed(CotEvent evt) {
                if (bCheckWhitelist && !whiteList.contains(evt.getUID()))
                    Log.d(TAG, "Filtering parsed UID: " + evt.getUID());
                else if (startTime > 0
                        && evt.getTime().getMilliseconds() < startTime)
                    Log.d(TAG, "Time filtering parsed UID: " + evt.getUID());
                else
                    cotEvents.add(evt);
                return true;
            }
        });

        return cotEvents;
    }

    /**
     * Parse CoT events one by one with a callback which decides how to handle
     * each CoT event and whether to continue parsing
     *
     * @param xml CoT events list XML
     * @param cb Parse callback
     */
    public static void parseCotEvents(String xml, ParseCallback cb) {
        if (FileSystemUtils.isEmpty(xml) || cb == null)
            return;

        int i = 0, start, end;
        while ((start = xml.indexOf("<event ", i)) > -1
                && (end = xml.indexOf("</event>", i)) > -1) {
            end += "</event>".length();
            String cotXML = xml.substring(start, end);
            CotEvent evt = CotEvent.parse(cotXML);
            if (evt != null && evt.isValid() && !cb.onEventParsed(evt))
                break;
            i = end;
        }
    }

    public interface ParseCallback {

        /**
         * Called when a single event has been parsed in a list of events
         * @param event CoT event
         * @return True to continue parsing, false to stop
         */
        boolean onEventParsed(CotEvent event);
    }
}
