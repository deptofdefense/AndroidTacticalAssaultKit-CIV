package gov.tak.test;

import android.Manifest;
import android.content.Context;

import com.atakmap.coremap.loader.NativeLoader;
import com.atakmap.map.EngineLibrary;

import org.junit.Rule;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.GrantPermissionRule;

public abstract class KernelJniTest
{
    final static String[] PermissionsList = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE,
            Manifest.permission.SET_WALLPAPER,
            Manifest.permission.INTERNET,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.DISABLE_KEYGUARD,
            Manifest.permission.GET_TASKS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.NFC,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,

            // 23 - protection in place
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,

            // 26 - protection in place
            Manifest.permission.REQUEST_DELETE_PACKAGES,

            //"com.atakmap.app.ALLOW_TEXT_SPEECH",
    };

    @Rule
    public GrantPermissionRule permissionRule = GrantPermissionRule
            .grant(PermissionsList);

    protected KernelJniTest()
    {
        Context appContext = ApplicationProvider.getApplicationContext();

        NativeLoader.init(appContext);
        EngineLibrary.initialize();
    }
}
