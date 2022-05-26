
package com.atakmap.android.video.legacy;

import android.app.AlertDialog;

import android.content.DialogInterface;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.atakmap.android.metrics.activity.MetricActivity;
import com.atakmap.android.util.NotificationUtil;
import com.atakmap.android.video.ConnectionEntry;
import com.atakmap.app.R;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;
import com.partech.pgscmedia.MediaException;
import com.partech.pgscmedia.MediaFormat;
import com.partech.pgscmedia.MediaProcessor;
import com.partech.pgscmedia.VideoMediaFormat;
import com.partech.pgscmedia.consumers.KLVConsumer;
import com.partech.pgscmedia.consumers.StatusUpdateConsumer;
import com.partech.pgscmedia.frameaccess.DecodedMetadataItem;
import com.partech.pgscmedia.frameaccess.KLVData;
import com.partech.pgscmedia.frameaccess.MediaMetadataDecoder;

import java.io.File;
import java.io.IOException;

import java.text.NumberFormat;
import java.util.HashMap;
import com.atakmap.coremap.locale.LocaleUtil;
import java.util.Map;

/*
 * This class makes use of Gv2F binaries which are licensed for use 
 * within the ATAK program. Please see MapVideoLibrary/lib/license.txt for
 * more information.
 * <P>
 * Demonstration video display Activity.  Uses a simplistic software-based
 * rendering to an Android SurfaceView. The video is played at 1x from
 * start to end.  At completion, a Toast is shown.  IO or connection
 * errors are reported via dialog.<P>
 *
 * KLV-based metadata is also decoded and a subset of items is displayed
 * at the bottom of the video display (optionally).<P>
 *
 * This activity is the only one in this demo app that interfaces directly
 * with the Gv2F library.<P>
 *
 * Accepts Intents that contain 4 "Extra" parameters containing the
 * connection type, hostname, port, and path. The URI is not internally used.
 * <P>
 *
 * Activity life cycle changes (rotation, exiting to Home, etc) just cause
 * the video playback to terminate gracefully.  It resumes from the start
 * when next the Activity is created.
 * <P>
 */
public class Gv2FMobilePlayer extends MetricActivity
        implements StatusUpdateConsumer,
        KLVConsumer {

    public static final String TAG = "Gv2FMobilePlayer";

    /**
     * String extra the specifies if buffering
     */
    public static final String EXTRA_UNBUFFERED = "Unbuffered";

    /** Temporary file directory to use. */
    private static final String TEMP_DIR = "VidActivityTmp";

    private static final int NOTIFICATION_ID = 13213;

    private MediaProcessor processor;

    // The following is only initialized when a metadata track is found
    private MediaMetadataDecoder metadataDecoder;
    private NumberFormat latFormat;
    private NumberFormat lonFormat;
    private NumberFormat altitudeFormat;
    private NumberFormat degFormat;
    private HashMap<DecodedMetadataItem.MetadataItemIDs, TextView> metadataMap;
    private HashMap<DecodedMetadataItem.MetadataItemIDs, NumberFormat> formatMap;

    // Time bar and display stuff
    private Handler timeBarUpdater;

    private SeekBar mSeekBar;
    private TextView tv;
    private NumberFormat nf;

    // "stepper" stuff
    private Handler stepper;
    private float stepperScale = 1.0f;
    private boolean stepperRunning = false;

    private ConnectionEntry ce;

    /** Temp directory in use - not used for file opens */
    private File tmpDir;

    /************************************************************/
    /*
     * Activity /***********************************************************
     */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        //this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, 
        //                          WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.gv2fmp);

        // Extract parameters from Intent
        final Intent intent = getIntent();
        Log.v(TAG, "View: " + intent.getData());

        // What type are we dealing with?
        ce = (ConnectionEntry) intent.getSerializableExtra("CONNECTION_ENTRY");

        processor = (MediaProcessor) getLastNonConfigurationInstance();

        mSeekBar = findViewById(R.id.SeekBar01);
        tv = findViewById(R.id.TimeView);
        nf = NumberFormat.getInstance();
        nf.setMinimumIntegerDigits(2);
        nf.setMaximumIntegerDigits(2);

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {

                if (processor == null) {

                    try {
                        // Create MediaProcessor using the various available
                        // constructors, depending on the type of source media.

                        String httpString = "http://"; // encountering a HTTPS will change this.
                        String rtmpString = "rtmp://"; // encountering a RTMPS will change this.

                        switch (ce.getProtocol()) {
                            case FILE:
                                try {
                                    File f = new File(
                                            FileSystemUtils.validityScan(
                                                    ce.getPath()));
                                    if (IOProviderFactory.exists(f)) {
                                        processor = new MediaProcessor(f);
                                    } else {
                                        throw new MediaException("");
                                    }
                                } catch (IOException ioe) {
                                    Log.d(TAG, "invalid file", ioe);
                                    throw new MediaException("");
                                }
                                break;

                            case UDP:
                                String host = ce.getAddress();
                                if (host != null) {
                                    if (host.length() == 0
                                            || host.equals("0.0.0.0")
                                            || host.equals("127.0.0.1"))
                                        host = null;
                                }
                                int port = ce.getPort();
                                setupTmpDir();

                                int buffer = ce.getBufferTime();
                                int timeout = ce.getNetworkTimeout();

                                String localAddr = null;
                                if (ce.getPreferredInterfaceAddress() != null) {
                                    localAddr = ce
                                            .getPreferredInterfaceAddress();
                                    Log.d(TAG,
                                            "use local address for network traffic: "
                                                    + localAddr);

                                }

                                processor = new MediaProcessor(host, port,
                                        timeout, buffer,
                                        0,
                                        tmpDir, localAddr);
                                break;
                            case RAW:
                            case RTP:
                            case TCP:
                            case RTMP:
                            case RTMPS:
                            case HTTPS:
                            case HTTP:
                                setupTmpDir();
                                String addr = ConnectionEntry.getURL(ce, false);
                                processor = new MediaProcessor(addr);
                                break;
                            case RTSP: {

                                setupTmpDir();
                                String rtspaddr = ConnectionEntry.getURL(ce,
                                        false);
                                processor = new MediaProcessor(rtspaddr,
                                        ce.getNetworkTimeout(),
                                        ce.getBufferTime(), 0, tmpDir);
                                break;
                            }
                        }

                    } catch (MediaException e) {
                        Log.e(TAG, "error: ", e);
                        Log.v(TAG, "Media Exception!");
                        // Gv2FMobilePlayer.this.finish();
                        Gv2FMobilePlayer.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(Gv2FMobilePlayer.this)
                                        .setTitle(R.string.error)
                                        .setCancelable(false)
                                        .setMessage(
                                                R.string.video_text57)
                                        .setNeutralButton(
                                                R.string.close,
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            DialogInterface d,
                                                            int i) {
                                                        Gv2FMobilePlayer.this
                                                                .finish();
                                                    }
                                                })
                                        .show();
                            }
                        });
                        processor = null;
                    } catch (IOException e) {
                        Log.e(TAG, "error: ", e);
                        Log.v(TAG, "IO Exception!");
                        // Gv2FMobilePlayer.this.finish();
                        Gv2FMobilePlayer.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new AlertDialog.Builder(Gv2FMobilePlayer.this)
                                        .setTitle(R.string.error)
                                        .setMessage(
                                                R.string.video_text58)
                                        .setNeutralButton(
                                                R.string.close,
                                                new DialogInterface.OnClickListener() {
                                                    @Override
                                                    public void onClick(
                                                            DialogInterface d,
                                                            int i) {
                                                        Gv2FMobilePlayer.this
                                                                .finish();
                                                    }
                                                })
                                        .show();
                            }
                        });
                        processor = null;
                    }
                }
            }
        }, "VideoOpenThread");
        t.start();
        try {
            // throw all of the network activity onto a thread, but wait until the thread is done to
            // continue.
            t.join();
        } catch (Exception ignored) {
        }

        Log.v(TAG, "continuing activity");
        if (processor == null) {
            // something bad happened, do not continue.
            GLSurfaceView gsv = findViewById(R.id.GLSurface);
            gsv.setVisibility(View.INVISIBLE);

            return;
        }
        Log.v(TAG, "processor != null continuing activity");

        timeBarUpdater = new Handler();
        stepper = new Handler();

        try {
            // Look at the format of all tracks and grab the ones we are
            // interested in. Here, for sake of simplicity,
            // we take just the first video track
            // and first klv metadata track if it exists.
            MediaFormat[] fmts = processor.getTrackInfo();
            boolean haveVid = false;
            boolean haveKLV = false;
            for (MediaFormat fmt : fmts) {
                if (!haveVid && fmt.type == MediaFormat.Type.FORMAT_VIDEO) {
                    setupVideo((VideoMediaFormat) fmt);
                    haveVid = true;
                }
                if (!haveKLV && fmt.type == MediaFormat.Type.FORMAT_KLV) {
                    setupKLV(fmt);
                    haveKLV = true;
                }
                if (haveKLV && haveVid)
                    break;
            }
            if (!haveVid) {
                Log.v(TAG, "No video track?");
                new AlertDialog.Builder(this)
                        .setTitle(R.string.video_text24)
                        .setMessage(
                                R.string.video_text59)
                        .setNeutralButton(R.string.close,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d,
                                            int i) {
                                        Gv2FMobilePlayer.this.finish();
                                    }
                                })
                        .show();
                return;
            }
        } catch (MediaException | RuntimeException e) {
            Log.v(TAG, "Media Exception!");
            // Gv2FMobilePlayer.this.finish();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.error)
                    .setMessage(R.string.video_text57)
                    .setNeutralButton(R.string.close,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int i) {
                                    Gv2FMobilePlayer.this.finish();
                                }
                            })
                    .show();
            return;
        }

        // Tell Gv2F we want status updates delivered to us.
        processor.setStatusUpdateConsumer(this);

        // setup the seek bar
        SeekBar mSeekBar = findViewById(R.id.SeekBar01);
        mSeekBar.setMax((int) (processor.getDuration()));
        mSeekBar.setKeyProgressIncrement(1000000);
        mSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    private boolean priorTrackingState = true;

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        if (fromUser) {
                            long posAct = processor.setTime(seekBar
                                    .getProgress());
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        priorTrackingState = processor.isProcessing();
                        processor.stop();
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (priorTrackingState) {
                            processor.start();
                        }
                    }

                });

        SlidingDrawer sd = findViewById(R.id.SlidingDrawer);
        sd.setOnDrawerOpenListener(new SlidingDrawer.OnDrawerOpenListener() {
            @Override
            public void onDrawerOpened() {
                ToggleButton tb = findViewById(
                        R.id.slideHandleButton);
                tb.setChecked(true);
            }
        });

        sd.setOnDrawerCloseListener(new SlidingDrawer.OnDrawerCloseListener() {
            @Override
            public void onDrawerClosed() {
                ToggleButton tb = findViewById(
                        R.id.slideHandleButton);
                tb.setChecked(false);
            }
        });

        timeBarUpdater.postDelayed(timeBarUpdaterMethod, 500);

    }

    @Override
    public void onResume() {
        super.onResume();

        if (processor != null) {
            // Can be null if we get a MediaException during onCreate() and
            // are getting here whilst showing the dialog
            processor.start();
            setNotification(true);
            final ImageButton pausePlay = findViewById(
                    R.id.PlayPauseBID);
            pausePlay.post(new Runnable() {
                @Override
                public void run() {
                    pausePlay.setImageResource(R.drawable.pauseforeground);
                }
            });
            Log.v(TAG, "Playback started");
            GLSurfaceView gsv = findViewById(R.id.GLSurface);
            try {
                if (gsv != null)
                    gsv.onResume();
            } catch (Exception e) {
                // not initialized
            }
            timeBarUpdater.postDelayed(timeBarUpdaterMethod, 500);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (processor != null) {
            processor.stop();
            setNotification(false);
            Log.v(TAG, "Playback stopped");
            GLSurfaceView gsv = findViewById(R.id.GLSurface);
            try {
                if (gsv != null)
                    gsv.onPause();
            } catch (Exception e) {
                // not initialized
            }

            timeBarUpdater.removeCallbacks(timeBarUpdaterMethod);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        removeNotification();

        stepperScale = 1.0f;
        if (isFinishing()) {
            // stop any steppers
            if (processor != null) {
                // Explicit destruction is not necessary, but we do so
                // to quickly stop all activity. Otherwise it will not
                // occur until 'processor' is garbage collected.
                processor.destroy();

                if (metadataDecoder != null) {
                    // This, like the processor destroy above,
                    // is just to expedite reclaimation of native resources
                    // in the face of a lazy GC.
                    metadataDecoder.clear();
                    metadataDecoder = null;
                }

                // Clean up all our temporary files
                cleanTmpDirs();
            }
        }

    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        Log.v(TAG,
                "Caching away non configuration instance " + processor + "!");
        return processor;
    }

    /************************************************************/
    /* Callbacks from our own timer bar UI xml */
    /************************************************************/

    public void onClickFastRev(View v) {
    }

    public void onClickSlowRev(View v) {
    }

    public void onClickPausePlay(View v) {
        MediaProcessor mProcessor = processor;
        if (mProcessor == null)
            return;

        ImageButton pausePlay = findViewById(R.id.PlayPauseBID);
        if (mProcessor.isProcessing()) {
            mProcessor.stop();
            mProcessor.setRate(1.0f);
            pausePlay.setImageResource(R.drawable.playforeground);
            setNotification(false);
        } else {
            mProcessor.setRate(1.0f);
            mProcessor.start();
            pausePlay.setImageResource(R.drawable.pauseforeground);
            setNotification(true);
        }

    }

    public void onClickSlowFwd(View v) {
        stepperScale = 1.0f;
        float processorRate = processor.getRate();
        if (processorRate <= (1.0f / 8.0f) || processorRate > 1.0f) {
            processor.setRate(1.0f);
        } else {
            processor.setRate(processorRate / 2.0f);
        }

    }

    public void onClickFastFwd(View v) {
        stepperScale = 1.0f;
        float processorRate = processor.getRate();
        if (processorRate >= 8.0f || processorRate < 1.0f) {
            processor.setRate(1.0f);
        } else {
            processor.setRate(processorRate * 2.0f);
        }

    }

    /**
     * Sets up the metadata track decribed by the argument.
     */
    private void setupKLV(MediaFormat fmt) {
        processor.setKLVConsumer(fmt.trackNum, this);
        metadataDecoder = new MediaMetadataDecoder();
        latFormat = LocaleUtil
                .getDecimalFormat("00.000\u00b0N    ;00.000\u00b0S    ");
        lonFormat = LocaleUtil
                .getDecimalFormat("000.000\u00b0E    ;000.000\u00b0W    ");
        altitudeFormat = LocaleUtil.getDecimalFormat("#0.000m    ");
        degFormat = LocaleUtil.getDecimalFormat("#0.000\u00b0    ");

        metadataMap = new HashMap<>();
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_TRUE_ALTITUDE,
                        findViewById(
                                R.id.AircraftAltitudeValueView));

        //        metadataMap.put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_PLATFORM_DESIGNATION,
        //                (TextView) findViewById(R.id.AircraftDesignationValueView));
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_PLATFORM_TAIL_NUMBER,
                        findViewById(R.id.AircraftTailValueView));

        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_ALT_PLATFORM_HEADING,
                        findViewById(R.id.AircraftHeadingValueView));
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_PLATFORM_PITCH_ANGLE,
                        findViewById(R.id.AircraftPitchValueView));
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_LATITUDE,
                        findViewById(
                                R.id.AircraftLatitudeValueView));
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_LONGITUDE,
                        findViewById(
                                R.id.AircraftLongitudeValueView));
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_FRAME_CENTER_LATITUDE,
                        findViewById(R.id.TargetLatitudeValueView));
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_FRAME_CENTER_LONGITUDE,
                        findViewById(R.id.TargetLongitudeValueView));
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_GROUND_RANGE,
                        findViewById(R.id.TargetRangeValueView));
        metadataMap
                .put(
                        DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_IMAGE_COORDINATE_SYSTEM,
                        findViewById(R.id.TargetCoordSysValueView));
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_IMAGE_SOURCE_SENSOR,
                        findViewById(R.id.SensorDeviceValueView));
        metadataMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_HORIZONTAL_FIELD_OF_VIEW,
                        findViewById(R.id.SensorHFOVValueView));
        metadataMap
                .put(
                        DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_VERTICAL_FIELD_OF_VIEW,
                        findViewById(R.id.SensorVFOVValueView));
        metadataMap
                .put(
                        DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_RELATIVE_ROLL_ANGLE,
                        findViewById(R.id.SensorRollValueView));

        formatMap = new HashMap<>();
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_TRUE_ALTITUDE,
                        altitudeFormat);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_ALT_PLATFORM_HEADING,
                        degFormat);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_PLATFORM_PITCH_ANGLE,
                        degFormat);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_LATITUDE,
                        latFormat);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_LONGITUDE,
                        lonFormat);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_FRAME_CENTER_LATITUDE,
                        latFormat);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_FRAME_CENTER_LONGITUDE,
                        lonFormat);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_GROUND_RANGE,
                        altitudeFormat);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_IMAGE_COORDINATE_SYSTEM,
                        null);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_IMAGE_SOURCE_SENSOR,
                        null);
        formatMap
                .put(DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_HORIZONTAL_FIELD_OF_VIEW,
                        degFormat);
        formatMap
                .put(
                        DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_VERTICAL_FIELD_OF_VIEW,
                        degFormat);
        formatMap
                .put(
                        DecodedMetadataItem.MetadataItemIDs.METADATA_ITEMID_SENSOR_RELATIVE_ROLL_ANGLE,
                        degFormat);
    }

    /**
     * Sets up the track decribed by the argument.
     */
    private void setupVideo(VideoMediaFormat fmt) throws MediaException {
        // Get the Surface
        View v = findViewById(R.id.vidsurface);
        SurfaceView sv = (SurfaceView) v;
        GLSurfaceView gsv = findViewById(R.id.GLSurface);
        processor.setVideoConsumer(fmt.trackNum,
                RenderFactory.initialize(fmt, gsv, sv, this));
    }

    /**
     * Creates a temp directory that is unique
     */
    private void setupTmpDir() throws IOException {
        File base = getFilesDir();
        base = new File(base, TEMP_DIR);
        if (!IOProviderFactory.mkdirs(base)) {
            Log.d(TAG, "could not make the directory: " + base);
        }
        tmpDir = IOProviderFactory.createTempFile("stream", null, base);
        FileSystemUtils.delete(tmpDir);
        if (IOProviderFactory.mkdirs(tmpDir)) {
            Log.d(TAG, "could not make the directory: " + tmpDir);
        }
    }

    /** Recursively delete the temp directory if it exists. */
    private void cleanTmpDirs() {
        File base = getFilesDir();
        base = new File(base, TEMP_DIR);
        if (!IOProviderFactory.exists(base))
            return;
        FileSystemUtils.deleteDirectory(base, true);
    }

    /************************************************************/
    /* StatusUpdateConsumer */
    /************************************************************/

    @Override
    public void mediaStreamExtentsUpdate(long startMillis, long endMillis) {
    }

    /** EOF callback implementation that logs and shows a Toast. */
    @Override
    public void mediaEOF() {
        Log.v(TAG, "EOF!");
        // if (glRenderer != null)
        // glRenderer.printAndResetStats();

        findViewById(R.id.vidsurface).post(new Runnable() {
            @Override
            public void run() {
                String s = ce.getProtocol() == ConnectionEntry.Protocol.FILE
                        ? getString(R.string.file)
                        : getString(R.string.stream);
                Toast.makeText(Gv2FMobilePlayer.this,
                        getString(R.string.video_text60) + s,
                        Toast.LENGTH_LONG).show();
            }
        });

        final ImageButton pausePlay = findViewById(
                R.id.PlayPauseBID);
        pausePlay.post(new Runnable() {
            @Override
            public void run() {
                pausePlay.setImageResource(R.drawable.playforeground);
            }
        });
    }

    /**
     * Fatal error callback implementation that shows a dialog and terminates the Activity
     */
    @Override
    public void mediaFatalError(String info) {
        Log.v(TAG, "Fatal error: " + info);
        findViewById(R.id.vidsurface).post(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(Gv2FMobilePlayer.this)
                        .setTitle(R.string.error)
                        .setMessage(
                                R.string.video_text61)
                        .setNeutralButton(R.string.close,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface d,
                                            int i) {
                                        Gv2FMobilePlayer.this.finish();
                                    }
                                })
                        .show();
            }
        });
    }

    /************************************************************/
    /*
     * KLVConsumer /***********************************************************
     */

    @Override
    public void mediaKLVData(final KLVData data) {
        final Map<DecodedMetadataItem.MetadataItemIDs, DecodedMetadataItem> items = metadataDecoder
                .decode(data);

        final SlidingDrawer drawer = findViewById(
                R.id.SlidingDrawer);
        drawer.post(new Runnable() {
            @Override
            public void run() {
                for (Map.Entry<DecodedMetadataItem.MetadataItemIDs, DecodedMetadataItem> e : items
                        .entrySet()) {
                    try {

                        // Log.v(TAG,"Cycling over {" + e.getKey() + "}");
                        final TextView view = metadataMap.get(e.getKey());
                        if (view != null) {
                            final Map.Entry<DecodedMetadataItem.MetadataItemIDs, DecodedMetadataItem> entry = e;
                            NumberFormat fmt = formatMap.get(entry.getKey());
                            if (fmt != null) {
                                String val = fmt.format(((Double) entry
                                        .getValue().getValue())
                                                .doubleValue());
                                view.setText(val);
                            } else {
                                view.setText(entry.getValue().getValue()
                                        .toString());
                            }
                        }
                    } catch (Exception ex) {
                        //Log.d(TAG, "exception occurred parsing key: " + ex);
                    }
                }
            }
        });
    }

    /************************************************************/
    /* UI update handler methods */
    /************************************************************/

    private final Runnable timeBarUpdaterMethod = new Runnable() {
        @Override
        public void run() {
            long curr = processor.getTime();
            mSeekBar.setProgress((int) (curr));

            int millis = (int) curr;
            int secs = (millis / 1000) % 60;
            int min = ((millis / 1000) % 3600) / 60;
            int hour = ((millis / 1000) / 3600);

            tv.setText(nf.format(hour) + ":" + nf.format(min) + ":"
                    + nf.format(secs));

            timeBarUpdater.postDelayed(timeBarUpdaterMethod, 250);
        }
    };

    private final Runnable stepperMethod = new Runnable() {
        @Override
        public void run() {
            if (stepperScale < 0.0f && processor.getTime() > 1000) {
                stepperRunning = true;
                long time = processor.getTime();
                long sought = processor.setTime(time - 1000);
                stepper.postDelayed(stepperMethod,
                        (long) Math.abs((time - sought) / stepperScale));
            } else {
                Log.v(TAG, "Stopping stepper!!!");
                stepperRunning = false;
                stepper.removeCallbacks(this);
            }
        }
    };

    private void setNotification(boolean isPlaying) {
        if (isPlaying) {
            NotificationUtil.getInstance().postNotification(NOTIFICATION_ID,
                    R.drawable.green_full,
                    NotificationUtil.GREEN,
                    getString(R.string.video_text56),
                    getString(R.string.playing), null, false);
        } else {
            NotificationUtil.getInstance().postNotification(NOTIFICATION_ID,
                    R.drawable.red_full,
                    NotificationUtil.RED,
                    getString(R.string.video_text56),
                    getString(R.string.stopped), null, false);
        }

    }

    private void removeNotification() {
        NotificationUtil.getInstance().clearNotification(NOTIFICATION_ID);
    }

}
