
package com.atakmap.android.lrf.reader;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;

import com.atakmap.android.bluetooth.BluetoothConnection;
import com.atakmap.android.bluetooth.BluetoothCotManager;
import com.atakmap.android.bluetooth.BluetoothBinaryClientConnection;
import com.atakmap.android.bluetooth.BluetoothReader;

import com.atakmap.android.lrf.LRFCotManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import java.util.Arrays;

/**
 * Manages the connection and reading from specific supported laser range finders.
 * Attempts to handle both PC and ICD-153.  See the serial monitor for the ICD-153
 * LRF spec.
 * 
 * For PLRF-15C
 *   Click the azimuth key six times in rapid succession. 
 *   Hit the range key to cycle between PC/PLGR
 *   Click the azimuth key six times in rapid succession to save the value.
 */
public class LRFReader extends BluetoothReader {

    static final private String TAG = "LRFReader";

    protected Callback callback = null;

    RangeFinderData range = null;
    RangeFinderData azimuth = null;
    RangeFinderData elevation = null;

    private static final int WORD_SIZE = 2;
    private final float MILS_TO_DEGREES = 0.05625f;

    /**
     * Defines the size of the GPS 153 data format in bytes (56 words)
     */
    private static final int EVENT_SIZE = 56 * WORD_SIZE;

    private final byte[] buffer = new byte[8 * 8 * 1024];
    private int curr = 0;

    public LRFReader(BluetoothDevice device) {
        super(device);
    }

    @Override
    public void onRead(final byte[] data) {

        // add the value to the message
        for (byte datum : data) {
            buffer[curr] = datum;
            curr++;
        }

        Log.d(TAG, "buffer_size: " + curr);

        debugData(buffer, curr);

        final String line = new String(buffer, 0, curr,
                FileSystemUtils.UTF8_CHARSET);

        // simple for now will likely only check the checksum in
        // the future....

        if (curr >= EVENT_SIZE) {
            int soh_idx = -1;

            // search for SOH
            for (int i = 0; i < curr && soh_idx < 0; ++i) {
                if (buffer[i] == (byte) 0xFF &&
                        buffer[i + 1] == (byte) 0x81 &&
                        buffer[i + 2] == (byte) 0xA5 &&
                        buffer[i + 3] == (byte) 0x13) {
                    soh_idx = i;
                }
            }
            Log.d(TAG, "found SOH at: " + soh_idx);
            if (soh_idx >= 0 && soh_idx + EVENT_SIZE <= curr) {
                byte[] msg = Arrays.copyOfRange(buffer,
                        soh_idx, EVENT_SIZE);
                debugData(msg, msg.length);
                try {
                    float[] result = parseData(msg);
                    Log.d(TAG, "result distance=" + result[0]
                            + " azimuth=" + result[1]
                            + " elevation=" + result[2]);
                    if (callback != null) {
                        callback.onRangeFinderInfo(result[1],
                                result[2],
                                result[0]);
                    }
                } catch (Exception e) {
                    Log.d(TAG,
                            "huge error occurred, reset the buffer and try again");
                }
                curr = 0;
            }
        }

        // look for weapon mounted balistic 
        if (curr >= EVENT_SIZE) {
            int soh_idx = -1;

            // search for SOH
            for (int i = 0; i < curr && soh_idx < 0; ++i) {
                if (buffer[i] == (byte) 0xFF &&
                        buffer[i + 1] == (byte) 0x85 &&
                        buffer[i + 2] == (byte) 0xAE &&
                        buffer[i + 3] == (byte) 0x1B) {
                    soh_idx = i;
                }
            }
            Log.d(TAG, "found wmb-SOH at: " + soh_idx);
            if (soh_idx >= 0 && soh_idx + EVENT_SIZE <= curr) {
                byte[] msg = Arrays.copyOfRange(buffer,
                        soh_idx, EVENT_SIZE);
                debugData(msg, msg.length);
                try {
                    float[] result = parseData1(msg);
                    Log.d(TAG, "result distance=" + result[0]
                            + " azimuth=" + result[1]
                            + " elevation=" + result[2]);
                    if (callback != null) {
                        callback.onRangeFinderInfo(result[1],
                                result[2],
                                result[0]);
                    }
                } catch (Exception e) {
                    Log.d(TAG,
                            "huge error occurred, reset the buffer and try again");
                }
                curr = 0;
            }

        }

        Log.d(TAG, line);
        String[] legacy = line.split("\r");
        for (String s : legacy)
            legacyParse(s);

    }

    private void legacyParse(String line) {
        try {

            RangeFinderData info = RangeFinderData.parse(line);

            if (info.getType() == RangeFinderData.Type.UNKNOWN)
                return;

            if (info.getType() == RangeFinderData.Type.RANGE) {
                range = info;
            } else if (info.getType() == RangeFinderData.Type.AZIMUTH) {
                azimuth = info;
            } else if (info.getType() == RangeFinderData.Type.ELEVATION) {
                elevation = info;
            } else if (info.getType() == RangeFinderData.Type.COMPASS_ERROR) {
                if (azimuth == null)
                    azimuth = info;
                else
                    elevation = info;
            } else if (info.getType() == RangeFinderData.Type.DISTANCE_ERROR) {
                range = info;
            } else if (info.getType() == RangeFinderData.Type.MAINBOARD_ERROR) {
                if (callback != null) {
                    callback.onComputerError();
                }
                range = null;
                azimuth = null;
                elevation = null;
            } else {
                if (callback != null) {
                    callback.onComputerError();
                }
                range = null;
                azimuth = null;
                elevation = null;
            }
            curr = 0;

            if (range != null && azimuth != null && elevation != null) {
                String message;
                if (range.getType() == RangeFinderData.Type.DISTANCE_ERROR) {
                    if (callback != null) {
                        callback.onRangeError();
                    }
                } else if (azimuth
                        .getType() == RangeFinderData.Type.COMPASS_ERROR) {
                    if (callback != null) {
                        callback.onCompassError();
                    }
                } else {
                    if (callback != null) {
                        callback.onRangeFinderInfo(azimuth.getDataConverted(),
                                elevation.getDataConverted(),
                                range.getDataConverted());
                    }
                }

                range = null;
                azimuth = null;
                elevation = null;
            }
        } catch (Exception e) {
            Log.d(TAG, "error reading line: " + line, e);
            range = null;
            azimuth = null;
            elevation = null;
        }

    }

    @Override
    protected BluetoothConnection onInstantiateConnection(
            BluetoothDevice device) {
        return new BluetoothBinaryClientConnection(device,
                BluetoothConnection.MY_UUID_INSECURE);
    }

    @SuppressLint("MissingPermission")
    @Override
    public BluetoothCotManager getCotManager(MapView mapView) {
        BluetoothDevice device = connection.getDevice();
        return new LRFCotManager(this, mapView,
                device.getName().replace(" ", "").trim()
                        + "." + device.getAddress(),
                device.getName());
    }

    public void setCallback(Callback callback) {
        this.callback = callback;
    }

    public interface Callback {
        void onCompassError();

        void onRangeError();

        void onComputerError();

        /**
         * Provides information uncorrected in terms of azimuth, elevation, and distance. distance
         * is uncorrected for elevation.   If the azimuth passed in is Double.NaN, then the 
         * implementation is responsible for warning the user and taking the appropriate steps
         * such as pulling the azimuth of the phone, etc.
         */
        void onRangeFinderInfo(double azimuth, double elevation,
                double distance);
    }

    private void debugData(byte[] bytes, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(len, bytes.length); ++i) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        Log.d(TAG, "Buffer [size=" + bytes.length + "] " + sb);
    }

    protected float[] parseData(byte[] data) {
        int headerOffset = 5 * WORD_SIZE;
        byte[] holder = new byte[4];
        float slant, azimuth, elevation;

        int offset = headerOffset + (5 * WORD_SIZE);
        holder[0] = data[offset + 3];
        holder[1] = data[offset + 2];
        holder[2] = data[offset + 1];
        holder[3] = data[offset + 0];
        offset += 4 * WORD_SIZE;
        slant = convertToFloat(holder);
        slant = convertDistance(slant, data[offset]);

        offset += WORD_SIZE;
        holder[0] = data[offset + 3];
        holder[1] = data[offset + 2];
        holder[2] = data[offset + 1];
        holder[3] = data[offset + 0];
        azimuth = convertToFloat(holder);
        offset += 4 * WORD_SIZE;
        azimuth = convertAngle(azimuth, data[offset]);

        offset += (WORD_SIZE * 2);
        holder[0] = data[offset + 3];
        holder[1] = data[offset + 2];
        holder[2] = data[offset + 1];
        holder[3] = data[offset + 0];
        elevation = convertToFloat(holder);
        offset += 4 * WORD_SIZE;
        elevation = convertAngle(elevation, data[offset]);

        return new float[] {
                slant, azimuth, elevation
        };
    }

    protected float[] parseData1(byte[] data) {
        int headerOffset = 5 * WORD_SIZE;
        byte[] holder = new byte[4];
        float slant, azimuth, elevation;

        int offset = headerOffset + (5 * WORD_SIZE);
        holder[0] = data[offset + 3]; // 20 range
        holder[1] = data[offset + 2];
        holder[2] = data[offset + 1];
        holder[3] = data[offset + 0];
        slant = convertToFloat(holder);

        offset += (12 * WORD_SIZE);
        holder[0] = data[offset + 3]; // 44 heading
        holder[1] = data[offset + 2];
        holder[2] = data[offset + 1];
        holder[3] = data[offset + 0];
        azimuth = convertToFloat(holder);

        offset += (WORD_SIZE * 2); // 48 inclination
        holder[0] = data[offset + 3];
        holder[1] = data[offset + 2];
        holder[2] = data[offset + 1];
        holder[3] = data[offset + 0];
        elevation = convertToFloat(holder);

        return new float[] {
                slant, azimuth, elevation
        };
    }

    private float convertDistance(float distance, byte unitCode) {
        float meters;
        switch (unitCode) {
            case 0: {
                // Meters
                meters = distance;
                break;
            }
            case 1: {
                // Feet
                meters = (float) (distance * 0.304803706);
                break;
            }
            case 2: {
                // Yards
                meters = (float) (distance * 0.914411119);
                break;
            }
            case 3: {
                // Km
                meters = distance / 1000f;
                break;
            }
            case 4: {
                // Miles
                meters = distance;
                break;
            }
            case 5: {
                // nm = Nautical Miles
                meters = (float) (distance * 1852.000011853);
                break;
            }
            default: {
                // Unknown units
                meters = Float.NaN;
                break;
            }
        }

        return meters;
    }

    private float convertAngle(float angle, byte unitCode) {
        float degrees;
        switch (unitCode) {
            case 0: {
                // Mils
                degrees = angle * MILS_TO_DEGREES;
                break;
            }
            case 1: {
                // Degrees
                degrees = angle;
                break;
            }
            case 4: {
                // Mils radians
                double radians = angle / 1000.0;
                degrees = (float) Math.toDegrees(radians);
                break;
            }
            default: {
                // Unknown units
                degrees = Float.NaN;
                break;
            }
        }

        return degrees;
    }

    private float convertToFloat(byte[] holder) {
        int value1 = 0xff & holder[0];
        int value2 = 0xff & holder[1];
        int value3 = 0xff & holder[2];
        int value4 = 0xff & holder[3];

        int singleInt = (value1 << 24) | (value2 << 16) | (value3 << 8)
                | (value4);

        int sign = singleInt >>> 31;
        int exponent = (singleInt & 0x00ff) - 128;
        int mantissa = (singleInt >> 8) & 0x7fffff;
        // This conversion is the IEEE standard with normalized in the range of 1 to 2
        double result = Math.pow(-1, sign) * Math.pow(2, (exponent))
                * (1 + (mantissa / Math.pow(2, 23)));

        // Actual conversion is in the range of 0.5 and 1 so dividing by two fixes that
        return (float) (result / 2);
    }

}
