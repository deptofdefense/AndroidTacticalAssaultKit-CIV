
package com.atakmap.app;

import android.annotation.SuppressLint;
import android.app.Service;
import android.app.Activity;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.location.GpsStatus.Listener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import android.app.AlarmManager;
import android.content.Context;

import com.atakmap.android.util.NotificationUtil;

import android.os.IBinder;
import android.speech.tts.UtteranceProgressListener;
import android.media.AudioManager;

import android.app.Notification;
import android.app.PendingIntent;
import android.os.Build;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.coremap.log.Log;

import java.util.Locale;
import android.speech.tts.TextToSpeech;

/**
 * Allows for certain functions to run as part of a Service so that they are not impacted by
 * certain Doze restrictions.
 */
public class BackgroundServices extends Service implements LocationListener,
        Listener, TextToSpeech.OnInitListener {

    private static final String TAG = "BackgroundServices";

    static PendingIntent contentIntent;
    private static Notification notification;

    public static final String SPEAK_INTENT = "com.atakmap.android.speak";
    public static final String SPEAK_TEXT = "text";
    public static final String SPEAK_STRATEGY = "strategy";

    final private static int NOTIFICATION_ID = 72992;
    private static BackgroundServices _instance;

    private LocationManager mLocationManager;
    private int gpsStatus = -1;

    private TextToSpeech _textToSpeech;
    private AudioManager audioManager = null;

    private final UtteranceProgressListener upListener = new UtteranceProgressListener() {
        @Override
        public void onDone(String utteranceId) {
            Log.d(TAG, "utterance onDone");

            if (audioManager != null) {
                audioManager.abandonAudioFocus(afChangeListener);
            }
        }

        @Override
        public void onError(String utteranceId) {
            Log.d(TAG, "utterance onError");
        }

        @Override
        public void onStart(String utteranceId) {
            Log.d(TAG, "utterance onStart");
        }
    };

    private final AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String text = intent.getStringExtra(SPEAK_TEXT);
            int strategy = intent.getIntExtra(SPEAK_STRATEGY,
                    TextToSpeech.QUEUE_ADD);

            //Log.d(TAG, "action: " + action + " " + text + " " + strategy);
            if (_textToSpeech != null && text != null) {

                if (audioManager != null) {
                    int result = audioManager.requestAudioFocus(
                            afChangeListener,
                            AudioManager.STREAM_MUSIC,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                    if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        Log.d(TAG, "audio focus request not granted");
                    }
                }

                _textToSpeech.speak(text, strategy, null, "MessageId");
            }
        }

    };

    @Override
    @SuppressLint("MissingPermission")
    public void onCreate() {
        super.onCreate();

        _instance = this;

        // create the notification util which creates the default channel on the newer android versions
        NotificationUtil.getInstance().cancelAll();

        Intent atakFrontIntent = new Intent();
        atakFrontIntent.setComponent(ATAKConstants.getComponentName());

        atakFrontIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // requires the use of currentTimeMillis
        PendingIntent contentIntent = PendingIntent.getActivity(this,
                (int) System.currentTimeMillis(),
                atakFrontIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        Notification.Builder nBuilder;
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            nBuilder = new Notification.Builder(this);
        } else {
            nBuilder = new Notification.Builder(this, "com.atakmap.app.def");
        }

        com.atakmap.android.util.ATAKConstants.init(this);
        nBuilder.setContentTitle(ATAKConstants.getAppName())
                .setContentText(ATAKConstants.getAppName() + " Running....")
                .setSmallIcon(
                        com.atakmap.android.util.ATAKConstants.getIconId())
                .setContentIntent(contentIntent)
                .setAutoCancel(true);
        nBuilder.setStyle(new Notification.BigTextStyle()
                .bigText(ATAKConstants.getAppName() + " Running...."));
        nBuilder.setOngoing(true);
        nBuilder.setAutoCancel(false);
        notification = nBuilder.build();

        startForeground(NOTIFICATION_ID, notification);

        mLocationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);
        if (mLocationManager != null) {
            if (Permissions.checkPermission(this,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION))
                mLocationManager.addGpsStatusListener(this);
        }

        audioManager = (AudioManager) this
                .getSystemService(Context.AUDIO_SERVICE);

        // we only need to register for the status listener, do not waste time listening for 
        // gps values.   leave this code in just in case this behavior changes in the future.

        //if (mLocationManager != null) {
        //    mLocationManager.requestLocationUpdates(
        //        LocationManager.GPS_PROVIDER,
        //        500,
        //        0,
        //        this);
        //}    

        _textToSpeech = new TextToSpeech(this, this);

        DocumentedIntentFilter filter = new DocumentedIntentFilter(SPEAK_INTENT,
                "Allows for speaking to occur when the app is backgrounded, utilizes"
                        +
                        " the 'text' string extra and 'strategy' int extra which could be one of"
                        +
                        "TextToSpeech.QUEUE_FLUSH or TextToSpeech.QUEUE_ADD");

        registerReceiver(receiver, filter, "com.atakmap.app.ALLOW_TEXT_SPEECH",
                null);

    }

    @Override
    public void onInit(int i) {
        Log.d(TAG, "initializing text to speech code=" + i);
        if (i != TextToSpeech.ERROR) {
            try {
                _textToSpeech.setLanguage(Locale.ENGLISH);
            } catch (Exception e) {
                Log.d(TAG, "error setting text to speech locale", e);
            }
            _textToSpeech.setOnUtteranceProgressListener(upListener);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "application swiped away");
        try {
            Log.d(TAG, "clearing notifications");
            NotificationUtil.getInstance().cancelAll();
        } catch (Exception ignored) {
            Log.d(TAG, "error clearing exceptions");
        }

        Log.d(TAG, "cancel alarm");
        AlarmManager alarmManager = (AlarmManager) getSystemService(
                Context.ALARM_SERVICE);
        if (contentIntent != null && alarmManager != null)
            alarmManager.cancel(contentIntent);

        Log.d(TAG, "stop service");
        stopService();
        Log.d(TAG, "stopped...");
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "location = [" + location + "]");
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, "provider disabled = [" + provider + "]");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, "provider enabled = [" + provider + "]");
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle b) {
        Log.d(TAG, "provider status changed sib = [" + s + "]");
    }

    @Override
    public void onGpsStatusChanged(int i) {
        if (i != gpsStatus) {
            Log.d(TAG, "provider status changed i = [" + i + "]");
            gpsStatus = i;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "service onStartCommand()");

        return START_NOT_STICKY;
    }

    @Override
    @SuppressLint("MissingPermission")
    public void onDestroy() {
        Log.d(TAG, "stopping");
        try {
            unregisterReceiver(receiver);

            if (_textToSpeech != null) {
                _textToSpeech.stop();
                _textToSpeech.shutdown();
            }

        } catch (Exception e) {
            Log.d(TAG, "error", e);
        }
        try {
            if (mLocationManager != null) {
                mLocationManager.removeUpdates(this);
                mLocationManager.removeGpsStatusListener(this);
            }
        } catch (Exception e) {
            Log.d(TAG, "error", e);
        }

        try {
            stopForeground(true);
        } catch (Exception e) {
            Log.d(TAG, "error", e);
        }
        Log.d(TAG, "complete stop");
    }

    public static void start(final Activity a) {

        Log.d(TAG, "call to start the gps keep alive service");
        Intent i = new Intent(a, BackgroundServices.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            a.startForegroundService(i);
        } else {
            a.startService(i);
        }
        Log.d(TAG, "call to start the gps keep alive service completed");
    }

    public static void stopService() {
        if (_instance != null) {
            Log.d(TAG, "call to stop the gps keep alive service");
            _instance.stopForeground(true);
            _instance.stopSelf();
            Log.d(TAG, "call to stop the gps keep alive service completed");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
