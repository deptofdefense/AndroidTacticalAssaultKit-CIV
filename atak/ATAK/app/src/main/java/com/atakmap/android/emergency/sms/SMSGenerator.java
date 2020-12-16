
package com.atakmap.android.emergency.sms;

import android.content.SharedPreferences;
import android.util.Base64;

import com.atakmap.android.emergency.tool.EmergencyType;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.locale.LocaleUtil;
import com.atakmap.coremap.log.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.SimpleTimeZone;

/**
 *
 * Generates an SMS message intended for the SMS2CotRelay app.
 */
public class SMSGenerator {

    public static final String TAG = "SMSGenerator";
    // Fields that appear within the encrypted, encoded SMS message.
    static private final byte version = 1;

    /** Generates a simple SMS message, intended to be converted into CoT format
     *
     * @return A String containing the formatted message.
     */
    static public String formatSMS(SharedPreferences pref, EmergencyType type,
            String callsign, boolean how, double lat, double lon) {
        DataOutputStream dos;
        ByteArrayOutputStream baos;
        try {
            // Create a new DataOutputStream object to write java primitives
            // to the underlying output stream.
            baos = new ByteArrayOutputStream();
            dos = new DataOutputStream(baos);
            dos.writeByte(version);
            dos.writeByte(0); //might go away in future version
            dos.writeInt(type.getCode());
            writeTime(
                    strFromMillisSinceEpoch(System.currentTimeMillis()),
                    dos);
            dos.writeBoolean(how);
            dos.writeDouble(lat);
            dos.writeDouble(lon);
            writeVariableLengthString(callsign, dos);
            // Flush everything into the output stream.
            dos.flush();
            byte[] key = extractKey(pref);
            byte[] res = baos.toByteArray();
            res = EncryptionUtils.encrypt(key, res);
            res = Base64.encode(res, Base64.DEFAULT);
            return new String(res, FileSystemUtils.UTF8_CHARSET);
        } catch (Exception e) {
            Log.e(TAG, "error occurred", e);
            return null;
        }
    }

    /**
     * Extracts a Base64-encoded AES key from preferences.
     * @return - The Base64-encoded symmetric key, or null if not found
     */
    static private byte[] extractKey(SharedPreferences pref) {
        String keyString = pref.getString("sms_crypto_key", null);
        if (keyString != null) {
            return Base64.decode(keyString.getBytes(), Base64.DEFAULT);
        }
        return null;
    }

    /**
     * Writes a variable-length string to a DataOutputStream. This is accomplished by prepending the string's
     * size.
     *
     * @param toWrite - the string to write
     * @param out     - the data output stream to write to
     * @throws IOException if there is a problem writing to the provided OutputStream
     */
    private static void writeVariableLengthString(String toWrite,
            DataOutputStream out) throws IOException {
        out.writeShort(toWrite.getBytes().length);
        out.write(toWrite.getBytes());
    }

    /**
     * Writes the time to a DataOutputStream.
     *
     * @param smallTime - the time in String form
     * @param out       - the data output stream to write to
     * @throws IOException if the time cannot be written to the DataOutputStream
     * @throws ParseException if the date time cannot be parsed into a date object
     */
    private static void writeTime(String smallTime, DataOutputStream out)
            throws IOException, ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.S'Z'", LocaleUtil.US);
        dateFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
        Date date = dateFormat.parse(smallTime);
        out.writeLong(date.getTime());
    }

    /**
     * Converts milliseconds to a more friendly time format.
     *
     * @param millisSinceEpoch - milliseconds since 1/1/1970
     * @return the string representation of the time provided in millis since epoch
     */
    private static String strFromMillisSinceEpoch(long millisSinceEpoch) {
        Date datetime = new Date(millisSinceEpoch);
        SimpleDateFormat dateFormat = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss.S'Z'", LocaleUtil.US);
        dateFormat.setTimeZone(new SimpleTimeZone(0, "UTC"));
        return dateFormat.format(datetime);
    }

}
