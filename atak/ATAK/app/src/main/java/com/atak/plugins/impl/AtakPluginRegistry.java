
package com.atak.plugins.impl;

import android.annotation.SuppressLint;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapActivity;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.metrics.MetricsApi;
import com.atakmap.android.tools.ActionBarReceiver;
import com.atakmap.android.tools.menu.ActionBroadcastData;
import com.atakmap.android.tools.menu.ActionClickData;
import com.atakmap.android.tools.menu.ActionMenuData;
import com.atakmap.android.update.AppMgmtUtils;
import com.atakmap.android.util.ATAKConstants;
import com.atakmap.app.BuildConfig;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dalvik.system.DexClassLoader;
import plugins.core.model.Plugin;
import transapps.maps.plugin.lifecycle.Lifecycle;
import transapps.maps.plugin.tool.Tool;
import transapps.maps.plugin.tool.ToolDescriptor;

public final class AtakPluginRegistry {

    private static final String TAG = "AtakPluginRegistry";

    private static final int PROGRESS_DESCRIPTORS = 20; //20%
    public static final String SHOULD_LOAD = "shouldLoad-";

    private static AtakPluginRegistry singleton = null;

    private final MapView mapView;
    private final AtakMapView atakMapView;

    private final static String[] ACCEPTABLE_KEY_LIST = new String[] {
            "308201b13082011aa00302010202044dd50358300d06092a864886f70d0101050500301c311a3018060355040a131157696e546563204172726f776d616b65723020170d3131303531393131343733365a180f32313631303431323131343733365a301c311a3018060355040a131157696e546563204172726f776d616b657230819f300d06092a864886f70d010101050003818d0030818902818100986c787624199264ee2f77ea8a21fe2562d18607d694b16676f9ddc1af9953f075c5036cb07cc8204cafb964ac794756855c606ac2646b387a2b59f4134483f4a3825ad85175e858eddb345da375dfd11bdc0a2ee40f6bdf7801d53951965a19cfcadf1b185628395cabd1a42f120b2c3956f3c64170afd74f8ba6ed78d5d1570203010001300d06092a864886f70d0101050500038181006d2f87358f3d06ccf8979a54a28bce085209cfb2c816a3affe6598919d548826c99ac04dad7c4fbd685dfba9f54db40f6b329ac0e380db159a66938e38e1205fbf2e42e722435d82cf429b26652d788ea1a3d857c8f509af575d234ddb4db6b6de86116bd72edf40fac67498f33a2e77bd5028fc73f250c2fcfd36b6067c38fa",
            "3082037130820259a00302010202047112d53a300d06092a864886f70d01010b05003069310b3009060355040613025553310b3009060355040813025641311330110603550407130a46742042656c766f69723110300e060355040a130755532041726d7931153013060355040b130c4e6574742057617272696f72310f300d06035504031306436869726f6e301e170d3137303132333135313835395a170d3432303131373135313835395a3069310b3009060355040613025553310b3009060355040813025641311330110603550407130a46742042656c766f69723110300e060355040a130755532041726d7931153013060355040b130c4e6574742057617272696f72310f300d06035504031306436869726f6e30820122300d06092a864886f70d01010105000382010f003082010a0282010100848d4e32ef5abe3faffaba91ef5bdfb00f6087efdd89fe7e36d8ac74284b482f0a403636edd2d6ab6493b9ecf5788a96bafdc91ff1168e4db8c05f57d9f1868c5e31b4ce088efc1b920131df7a99e223a3f20c651c50cdd4565040a2dbc11a745f76ccde21fd780b6755fbff7bed30829f4d32549f2c2a75dd57c0c386bd50a956101776a1614908cecf44ce07c2ef5247708d098d534787d3c495db85fbd7552e2bf6ef981cbfaf225b20f0e3964e8f6e798fccda73df91e025507ee118296581f32e2d18d2650f050a6fbcb13ba53ff1921a769c7b7de86a701862cf5524422012d6f1cb311f89f1937e42bf9f35ca1226d97a40a3a8a1d663f51d75daf38b0203010001a321301f301d0603551d0e0416041461a39efde992f22585609192fc6bd2838ed34171300d06092a864886f70d01010b050003820101000f3610c512a9494687d701806ae01685a8dc92b58f4e07e55b42b4fb5ca36aa8537bea496f27c4e0fabd04b633c1e7ad5dccafe86d38a4fac6698f22680f45edf160f55937ae335aa81d907fb8ba3130cf50f0c5a547c4c0984f2e8b76aa9bc11b247348eff41665abe8831d126cc52261b8352e9c9098b474ee2f8b17fec50333998b7f90bba9c4836a8105212f41a964f60425ec36be2c6cca31619745d4d37b57d1ad7e09a4a4d124e7c60b9530681738f6221a4da00ad5962b9ee0f4ee7f97135f5f515118b79dfad7dc183c16f186986f2b253ceca18d7a332399a63704e028e209cc23f89d9bb4edbdaff4cfd36767602ae746364b18a89a1d7f05b36e",
            "3082030d308201f5a00302010202044fce2f46300d06092a864886f70d01010b05003037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f6964204465627567301e170d3133303132323135323932395a170d3134303132323135323932395a3037310b30090603550406130255533110300e060355040a1307416e64726f6964311630140603550403130d416e64726f696420446562756730820122300d06092a864886f70d01010105000382010f003082010a02820101009b715652725d49cd5ce8e52c3c1dfa77b60861e23a787c8d1dd4b980596ef7fa9e843500ec7dbf883416e58b5e43fd58eb0faeb5412717629b033b28e3760650c88e635f54538af20c52289087b895f175bf0ccb8135c83e0db8c08dd58f4262881317ae88b659d7e73fdc5d3c3cc96b1e0d2a957ceec45632870a23e794e0b403047a9265e74e115f9af6a18d747cc45c4591fd0bf6f6e5fcd5d305b4f6b1cd392d71ecb6428c7ccf4f3cef96a0dce499bb24ff888319fabc5aa2163ae723637f20e79f14037c20854b305b39f0f2106831c9e4145857f987d6c8fedf7e5bf9b4f0fd3662bf1761f301ded0550ad7f1172394035b9e5ba8d7a8384f00555bf50203010001a321301f301d0603551d0e04160414b4531a77e455c714390e73ff7f828179f97c2c41300d06092a864886f70d01010b0500038201010082b47e77b10ed6262c936f66d16affb6607668d62b87ce1399ddcfcebce67f92c2630352bf0b7685610cac1ad97c0b3cfd0fc1c24842cfb0f839ea49b952b7f0b12121aed87b19a7944d2b24cb6a373a651ac9919164826961dc35a37061874940afcafad1f8466caf08f4b405634afa3723f59ca22ff8bcb66a30794d74b08e62ed213eabbdea6260b5cb2fc8844d21f196636fba1cea077a51a969193d99fbca156298a18909cfc16511708317b684e0d3744217d05276aaf71cac68063bd0c58d7097d9f5ebbd3d9cd0faed8744222422bb3572acab31e67bbcaf1f290d7f56d2a54d2d39ca04338f7e426556a6e3271d3f68e88ca1140e49b46d8dd497fb",
            "3082038930820271a00302010202040885ec68300d06092a864886f70d01010b05003075310b30090603550406130255533110300e06035504081307556e6b6e6f776e311430120603550407130b726170746f72782e6f72673110300e060355040a1307526170746f7258311a3018060355040b1311526170746f7220436f6e736f727469756d3110300e06035504031307556e6b6e6f776e301e170d3137303531373230303533325a170d3434313030323230303533325a3075310b30090603550406130255533110300e06035504081307556e6b6e6f776e311430120603550407130b726170746f72782e6f72673110300e060355040a1307526170746f7258311a3018060355040b1311526170746f7220436f6e736f727469756d3110300e06035504031307556e6b6e6f776e30820122300d06092a864886f70d01010105000382010f003082010a0282010100a23b3a0d799312fc4e9d6c842e2778f378b873b71dd041aa438903e08c77a387e446c72f626f7766cd90dfe5aeb2d9d1ab997bc70de0d2c338925991de0511547e78b70b77b99a29af71baa8cef3dd9cedd88a13ac0c0cfeef30d33890eff178d992168aeb13272833e8aea6c3c698e09e6789bc9d8d2373b3a13e7ab75f710cacff3c0e7fe1d06a191d73f990554d84ef8da2784388ccacdaacb366bd4acae5c646bdf68b822804a7dfff0e7a4887579719b7badce060710847b0a652895d2ea11107f92d5fef8efb9677a19bb1f0cd8e572b97a92fcf0b9952ce7f41784783521ec373920c55e4292030df3c0fcf3dd1ace8267ec71f868d62f0c5f58f908b0203010001a321301f301d0603551d0e04160414c414ae3840cc708132c2b8b4ac981f7c3bf35a87300d06092a864886f70d01010b05000382010100a0be7e78c323bf5d68db99280bce6392f3315bc0bced005793055e791c052a5d12aeedee2c743da05e6409e24cbbb84ad4d50c2b0bae9bb930187875b928cb59b46e429f28d9306a476fb1679849e5bd8f274d7717e84fa05c3845d582616960f7863ee7b901793ad0869b236de36fb0ed265e2c16e6ac9aeb211b620393db310b9bae158e3551953548dd4b7d4e2425f283ae8ff67221d96bfc92610de4e49989cc398a3e077ecaad3b1b4002d49ccc0badfc626502aa98dbc1d73ef2227b63562891575cd2c5c91c6915b5394cb3b342b74838f069af219501fd827e296e3eb7e28ec273ad0340e05e0a9782fb2a4e080cc7ffc51503bb95f3940c3a9b8ee0",
            "308202c43082024aa0030201020214643352031f1da2384eadb9eaf1daed408eac13da300a06082a8648ce3d040302308197310b30090603550406130255533111300f06035504080c0856697267696e69613115301306035504070c0c466f72742042656c766f6972310c300a060355040a0c0354414b31173015060355040b0c0e50726f647563742043656e7465723137303506035504030c2e54414b2050726f647563742043656e746572204154414b205472757374656420506c7567696e2052656c656173653020170d3230303532313039333530365a180f32303530303531343039333530365a308197310b30090603550406130255533111300f06035504080c0856697267696e69613115301306035504070c0c466f72742042656c766f6972310c300a060355040a0c0354414b31173015060355040b0c0e50726f647563742043656e7465723137303506035504030c2e54414b2050726f647563742043656e746572204154414b205472757374656420506c7567696e2052656c656173653076301006072a8648ce3d020106052b81040022036200044feb54baeaf9a24f3dc0181daf8a2871840f2c3209b1f2135da72b5ee356a06ae36ca5b5542c7b21da5b7e7a8d17af93b0e6d49e8a6076f988e4b011a106e68c0f740eece9f1bd71254d0b1498ee923598dd5c8ad6eef3856f024b24fccdb528a3533051301d0603551d0e04160414c22b57a8e00e0a32ebc128d7833762bf65dbc49e301f0603551d23041830168014c22b57a8e00e0a32ebc128d7833762bf65dbc49e300f0603551d130101ff040530030101ff300a06082a8648ce3d0403020368003065023055cea4a942e0d6d4710a3fa506eb1163f6d39cec289cf3b5ac9368a709564bd0426850c455178ba357b40dfdf46c2d5f023100f5b493c98edc08744d42625db0040b33e05334a39a41d33759fc8f4270089932532085904cbeedddcd4bf3719213df7e",
            "308202c73082024ea00302010202143efff3c9fc6b6865c29bb23af07fa7645727e650300a06082a8648ce3d040302308199310b30090603550406130255533111300f06035504080c0856697267696e69613115301306035504070c0c466f72742042656c766f6972310c300a060355040a0c0354414b31173015060355040b0c0e50726f647563742043656e7465723139303706035504030c3054414b2050726f647563742043656e746572204154414b20556e7472757374656420506c7567696e2052656c656173653020170d3230313030383135343631375a180f32303530313030313135343631375a308199310b30090603550406130255533111300f06035504080c0856697267696e69613115301306035504070c0c466f72742042656c766f6972310c300a060355040a0c0354414b31173015060355040b0c0e50726f647563742043656e7465723139303706035504030c3054414b2050726f647563742043656e746572204154414b20556e7472757374656420506c7567696e2052656c656173653076301006072a8648ce3d020106052b8104002203620004bbf9dba5553faaee4558788805494c1a3d8bc0a5eca4c59bce62fcb68b979993877f5c65190454e1700c98184163d022b8d91648ca6898b41b2cf56b26ce19e3794a4bdeb1c7c08f021d8fb7b258b904d94c52ab1ffb223975bd6365127083cfa3533051301d0603551d0e04160414893a2594bd35f780183a882731d180de056b8326301f0603551d23041830168014893a2594bd35f780183a882731d180de056b8326300f0603551d130101ff040530030101ff300a06082a8648ce3d040302036700306402306a0b7e55fb2eb46584bf79dbac99720ed368cd0f9e4c333893aaa1763104472ce60c899136cb3fb3a847ee7d6dc9029602300afbc42742092b1f499cb7febd33543c0a24afe21aee6820d1b917375bd0204406751c647d76071cd61e16c1042d796a",
    };

    private boolean allTrusted = true;

    /**
     * List of all apps installed on the local device
     */
    private final Set<ApplicationInfo> installedAppSet = new HashSet<>();

    /**
     * List of apps installed on the local device, which are a plugin (contain valid plugin.xml)
     */
    private final Set<PluginDescriptor> pluginDescriptorSet = new HashSet<>();

    /**
     * List of apps (package name) installed and loaded in ATAK currently. At least one plugin
     * Extension was loaded for the specified pacakge name
     */
    private final Set<String> loadedPluginsSet = new HashSet<>();

    /**
     * List of incompatible plugins (based on plugin-api from AndroidManifest.xml)
     */
    private final Set<String> incompatiblePluginsSet = new HashSet<>();

    /**
     * Map plugin Extension classname to the instantiated plugin Extension object
     */
    private static final Map<String, Object> pluginInstantiations = new HashMap<>();

    /**
     * Map plugin Extension classname to the instantiated plugin LifeCycleMapComponent object
     */
    private static final Map<String, LifecycleMapComponent> pluginLifecycleInstantiations = new HashMap<>();

    /**
     * Store receivers registered on behalf of all loaded plugins
     */
    private final List<Pair<String, BroadcastReceiver>> pluginReceivers = new ArrayList<>();

    /**
     * Store action bar menus added on behalf of all loaded plugins
     */
    private final List<Pair<String, ActionMenuData>> pluginMenus = new ArrayList<>();

    public static final String pluginLoadedBasename = "plugin.version.loaded.";
    private final SharedPreferences _prefs;

    /**
     * Returns an unmodifiable version of the plugin instantiations.
     * @return a colection of plugins that have been instantiated
     */
    public Collection<Object> getPluginInstantiations() {
        synchronized (this) {
            return Collections
                    .unmodifiableCollection(pluginInstantiations.values());
        }
    }

    /**
     * Verify the signature of the package matches the signature used to sign the TAK application.
     * <pre>
     * Suppressed Lint warning because of the information in 
     *    https://thehackernews.com/2014/07/android-fake-id-vulnerability-allows_29.html
     *    https://www.blackhat.com/docs/us-14/materials/us-14-Forristal-Android-FakeID-Vulnerability-Walkthrough.pdf
     *    and the fact that it is not used for anything more than printing 
     *    the current signatures.   If this is ever enabled as a true 
     *    verification, then the above links should be examined.
     * </pre>
     *
     * @param context provided context for getting the package manager
     * @param pkgname the name of the pacakge to check
     * @return true if the signatures match.
     */
    @SuppressLint("PackageManagerGetSignatures")
    public static boolean verifySignature(final Context context,
            final String pkgname) {
        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo atak = pm.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNATURES);
            //for (Signature sig : atak.signatures) {
            //      Log.d(TAG, "atak sigs: " + sig);
            //}

            final PackageInfo pi = pm.getPackageInfo(pkgname,
                    PackageManager.GET_SIGNATURES);
            //for (Signature sig : pi.signatures) {
            //      Log.d(TAG, "pi sigs: " + sig);
            //}

            for (final Signature sig : pi.signatures) {
                if (BuildConfig.BUILD_TYPE.equals("sdk")) {
                    Log.d(TAG, "SDK skipping signature check[" + pkgname + "]");
                    return true;
                } else if (sig.equals(atak.signatures[0])) {
                    Log.d(TAG, "signature verified[" + pkgname + "]");
                    return true;
                } else {
                    for (final String key : ACCEPTABLE_KEY_LIST) {
                        if (sig.toCharsString().equals(key)) {
                            Log.d(TAG, "signature verified[" + pkgname + "]");
                            return true;
                        }
                    }
                }

            }
        } catch (Exception e) {
            Log.d(TAG, "error occurred verifying signature", e);
        }

        Log.d(TAG, "signature mismatch[" + pkgname + "]");

        // in the case of failure to verify the signature, mention it in the logs and in the metrics
        ArrayList<String> values = new ArrayList<>();
        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(pkgname,
                    PackageManager.GET_SIGNATURES);
            for (Signature sig : pi.signatures) {
                values.add(sig.toCharsString());
                Log.d(TAG,
                        "signature[" + pkgname + "]: " + sig.toCharsString());
            }
        } catch (Exception ignored) {
        }

        if (MetricsApi.shouldRecordMetric()) {
            Bundle b = new Bundle();
            b.putString("package", pkgname);
            b.putStringArrayList("signatures", values);
            b.putString("verifySignature", "false");
            MetricsApi.record("plugin", b);
        }

        return false;
    }

    /**
     * Verifies that a specific package can be trusted.
     * @param context the context to use
     * @param pkgname the package name to look up
     * @return
     */
    public static boolean verifyTrust(final Context context,
            final String pkgname) {
        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo pi = pm.getPackageInfo(pkgname,
                    PackageManager.GET_SIGNATURES);
            for (final Signature sig : pi.signatures) {
                final String val = sig.toCharsString();
                if (val.endsWith("213df7e") || val.endsWith("f05b36e") ||
                        val.endsWith("a9b8ee0"))
                    return true;
            }

        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * @return fluid interface
     */
    AtakPluginRegistry dispose() {

        List<Pair<String, BroadcastReceiver>> receivers;
        synchronized (this) {
            installedAppSet.clear();
            pluginDescriptorSet.clear();
            loadedPluginsSet.clear();
            incompatiblePluginsSet.clear();
            receivers = new ArrayList<>(pluginReceivers);
            pluginReceivers.clear();
        }

        // Unregister plugin receivers
        unregisterReceivers(receivers);

        // remove from action bar
        final List<ActionMenuData> toolMenus = new ArrayList<>();
        for (Pair<String, ActionMenuData> menu : pluginMenus) {
            try {
                Log.d(TAG, "unregistering previously registered menu: "
                        + menu.first);
                toolMenus.add(menu.second);
            } catch (Exception e) {
                Log.d(TAG, "error occurred unregistering menu: ", e);
            }
        }

        if (toolMenus.size() > 0) {
            Log.d(TAG,
                    "Broadcasting intent to remove: " + toolMenus.size()
                            + " tools from the ActionBar");
            Intent intent = new Intent(ActionBarReceiver.REMOVE_TOOLS);
            intent.putExtra("menus",
                    toolMenus.toArray(new ActionMenuData[0]));
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }

        // Note during shutdown, MapActivity invokes end of lifecycle methods (e.g. onDestroy) on
        // all loaded MapComponents, including plugins
        return this;
    }

    /**
     * Get a handle on the singleton, NULL if initialize() was not called first
     * @return the fluid AtakPluginRegistry
     */
    public synchronized static AtakPluginRegistry get() {
        return singleton;
    }

    /**
     * Initialize this singleton with all parameter requirements
     * @param mapView pass in a mapview for initialization purposes.
     * @return return the single instance of the AtakPluginRegistry.
     */
    public synchronized static AtakPluginRegistry initialize(MapView mapView) {
        if (singleton == null)
            singleton = new AtakPluginRegistry(mapView);
        return singleton;
    }

    /**
     * Protected constructor - use initialize() first, then get() subsequently
     * @param mapView the mapView used during initialization.
     */
    private AtakPluginRegistry(final MapView mapView) {
        this.mapView = mapView;
        this.atakMapView = new AtakMapView(mapView);
        this._prefs = PreferenceManager
                .getDefaultSharedPreferences(mapView.getContext());
        final Map<String, ?> keys = _prefs.getAll();
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            final String key = entry.getKey();
            if (key.startsWith(pluginLoadedBasename)) {
                _prefs.edit().remove(key).apply();
            }
        }
    }

    /**
     * Scan apps, parse plugin descriptors, loads all extentions for all plugins which are flagged \
     * as "shouldLoad" and are not already loaded
     * @return if all of the plugins are to be considered trusted.
     */
    public boolean scanAndLoadPlugins(PluginLoadingProgressCallback callback) {
        allTrusted = true;
        Log.d(TAG, "Scanning and Loading Plugins");
        this.scan().loadDescriptors(callback).loadPlugins(callback);
        return allTrusted;
    }

    /////////////////////////////////////////////////////////////////////////////
    // for System Scan -> ApplicationInfo

    /**
     * Populates the list of installed applications on the system
     * @return fluid interface
     */
    public AtakPluginRegistry scan() {
        Log.d(TAG, "scan");

        if (!installedAppSet.isEmpty()) {
            Log.d(TAG, "Already scanned, replacing...");
        }

        // ask Android's packagemanager for all the installed apps
        PackageManager packageMgr = mapView.getContext().getPackageManager();
        List<ApplicationInfo> infos = packageMgr
                .getInstalledApplications(PackageManager.GET_META_DATA);

        synchronized (this) {
            installedAppSet.clear();
            installedAppSet.addAll(infos);
        }

        // fluid interface
        return this;
    }

    /**
     * scan() populates the list of apps, this will tell you how many the scan returned...
     * @return the number of apps installed.
     */
    public int getNumberOfAppsInstalled() {
        synchronized (this) {
            return installedAppSet.size();
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    // for Reporting Progress
    public interface PluginLoadingProgressCallback {

        /**
         * Scan/load percentage [0, 100]
         * @param percent the percentage of scanning or loading out of 100.
         */
        void onProgressUpdated(int percent);

        /**
         * Number of plugins loaded
         * @param numLoaded the number of apps loaded
         */
        void onComplete(int numLoaded);
    }

    /////////////////////////////////////////////////////////////////////////////
    // for ApplicationInfo -> PluginDescriptor

    /**
     * Loads all the PluginDescriptors from the set of installed applications populated by scan()
     * @param progressCallback the callback to invoke during the scanning process.
     * @return fluid interface
     */
    private AtakPluginRegistry loadDescriptors(
            PluginLoadingProgressCallback progressCallback) {
        Log.d(TAG, "loadDescriptors");
        if (!pluginDescriptorSet.isEmpty()) {
            Log.d(TAG, "Plugin descriptors already loaded, replacing...");
        }

        synchronized (this) {
            pluginDescriptorSet.clear();
            //use float for progress calc
            float numCompleted = 0;

            //loop all installed apps, see which ones are plugins
            for (ApplicationInfo app : installedAppSet) {
                // try to load the plugin's descriptor
                PluginDescriptor plugin = loadPluginDescriptor(app);
                // if we were able to load the descriptor
                if (plugin != null) {
                    // add it to the list of plugin descriptors
                    pluginDescriptorSet.add(plugin);
                    Log.d(TAG, "Adding plugin app: "
                            + app.toString() + ", total plugins: "
                            + pluginDescriptorSet.size());
                } else {
                    Log.d(TAG, "Skipping non plugin app: "
                            + app.toString());
                }
                numCompleted++;

                if (progressCallback != null) {
                    float progress = (numCompleted / installedAppSet.size())
                            * PROGRESS_DESCRIPTORS;
                    progressCallback.onProgressUpdated((int) progress);
                }
            }
        }

        return this;
    }

    /**
     * Gets all the PluginDescriptors, must be populated by loadDescriptors()
     * @return a copy of the set of PluginDescriptors
     */
    public Set<PluginDescriptor> getPluginDescriptors() {
        synchronized (this) {
            return new HashSet<>(pluginDescriptorSet);
        }

    }

    /**
     * Gets list of plugins scanned which are incompatible with current version of ATAK
     * @return a copy of the set of incompatible plugins
     */
    public Set<String> getIncompatiblePlugins() {
        synchronized (this) {
            return new HashSet<>(incompatiblePluginsSet);
        }
    }

    public boolean isPluginLoaded(String pkg) {
        //TODO this is not getting populated when installing a plugin via new UI
        synchronized (this) {
            return loadedPluginsSet.contains(pkg);
        }
    }

    /**
     * Returns a copy of a set that contains the names of all of the loaded plugins.
     * @return the copy
     */
    public Set<String> getPluginsLoaded() {
        synchronized (this) {
            return new HashSet<>(loadedPluginsSet);
        }
    }

    private boolean isExtensionLoaded(String impl) {
        synchronized (this) {
            return pluginInstantiations.containsKey(impl);
        }
    }

    /**
     * Check if the specified app is a plugin
     * @param pkg the package name
     * @return true if the specified package is a plugin
     */
    public boolean isPlugin(String pkg) {
        return getPlugin(pkg) != null;
    }

    /**
     * Check if the specified app is a plugin
     * @param pkg the provided package name
     * @return the plugin descriptor if the package is a plugin, otherwise null.
     */
    public PluginDescriptor getPlugin(final String pkg) {
        //first check pluginDescriptor cache
        synchronized (this) {
            for (PluginDescriptor pluginDescriptor : pluginDescriptorSet) {
                if (FileSystemUtils.isEquals(pkg,
                        pluginDescriptor.getPackageName())) {
                    Log.d(TAG, "Plugin was previously scanned: " + pkg);
                    return pluginDescriptor;
                }
            }
        }

        //now scan OS
        Log.d(TAG, "Plugin has not been scanned, scanning now: " + pkg);
        ApplicationInfo info = AppMgmtUtils.getAppInfo(mapView.getContext(),
                pkg);
        if (info == null) {
            Log.d(TAG, "Plugin not installed: " + pkg);
            return null;
        }

        PluginDescriptor plugin = loadPluginDescriptor(info);
        synchronized (this) {
            if (plugin != null) {
                Log.d(TAG, "Scanned and found plugin: " + pkg);
                installedAppSet.add(info);
                pluginDescriptorSet.add(plugin);
            }
        }

        return plugin;
    }

    /////////////////////////////////////////////////////////////////////////////
    // for PluginDescriptor -> Plugin Instantiation

    private <T> List<T> loadExtension(PluginDescriptor descriptor,
            Class<T> type) {
        String typeName = type.getCanonicalName();
        List<T> ret = new LinkedList<>();

        Log.d(TAG,
                "Loading " + typeName + " extensions for "
                        + descriptor.toString());

        for (Extension extension : descriptor.extensions) {
            if (extension.parent == null)
                extension.parent = descriptor;

            if (typeName != null && typeName.equals(extension.type)) {
                if (isExtensionLoaded(extension.impl)) {
                    Log.d(TAG, "Already loaded, skipping plugin extension: "
                            + extension.impl);
                    continue;
                }

                T ext = this.loadExtension(extension);
                if (ext == null) {
                    Log.w(TAG,
                            "failed to load extension: "
                                    + extension.toString());
                    continue;
                }
                ret.add(ext);
            }
        }

        Log.d(TAG, "Found " + ret.size() + " plugin extensions matching "
                + type.getSimpleName()
                + " in " + pluginDescriptorSet.size() + " plugins");
        return ret;
    }

    /**
     * Loads the plugin extension from descriptor into the application space and returns an instantiation of it.
     * @param extension the extension to attempt ot load, can either be a Tool or a Lifecycle.
     * @param <T>
     * @return
     */
    private <T> T loadExtension(Extension extension) {
        Object ret = null;
        if (extension.parent == null || extension.parent.appInfo == null) {
            Log.w(TAG, "plugin extension invalid: " + extension.toString());
            return null;
        }

        // first see if we already have loaded this class...
        //TODO better sync for all these collections...
        ret = pluginInstantiations.get(extension.impl);

        // if we don't already have one cached...
        if (ret == null) {
            try {
                final String pkgname = extension.parent.appInfo.packageName;
                final PackageManager pm = mapView.getContext()
                        .getPackageManager();
                ApplicationInfo ai = null;
                try {
                    ai = pm.getApplicationInfo(pkgname, 0);
                } catch (final PackageManager.NameNotFoundException ignored) {
                }
                final String pluginName = (String) (ai != null ? pm
                        .getApplicationLabel(ai)
                        : "unknown("
                                + pkgname + ")");

                //first check manifest based plugin API
                if (!isTakCompatible(mapView.getContext(), extension.parent)) {
                    Log.d(TAG,
                            "plugin will not load, version incorrect");
                    synchronized (this) {
                        incompatiblePluginsSet.add(extension.parent
                                .getPackageName());
                    }

                    if (MetricsApi.shouldRecordMetric()) {
                        Bundle b = new Bundle();
                        b.putString("package",
                                extension.parent.getPackageName());
                        b.putString("compatible", "false");
                        MetricsApi.record("plugin", b);
                    }
                    return null;
                }

                if (!verifySignature(mapView.getContext(), pkgname)) {
                    return null;
                }

                allTrusted = allTrusted
                        && verifyTrust(mapView.getContext(), pkgname);

                //finally add plugin to classpath
                ClassLoader classLoader = getClassLoader(
                        extension.parent.appInfo);

                // attempt to get class loader for the plugin
                if (classLoader == null) {
                    Log.w(TAG, "Could not get classloader for: "
                            + extension.parent.toString());
                    synchronized (this) {
                        incompatiblePluginsSet.add(extension.parent
                                .getPackageName());
                    }
                    return null;
                }

                Class<?> implClass = classLoader.loadClass(extension.impl);

                // See if there's a constructor that takes a Context
                Constructor<?> contextConstructor = null;
                try {
                    contextConstructor = implClass
                            .getConstructor(Context.class);
                } catch (Exception ignored) {
                }

                // Instantiate one using the context constructor
                if (contextConstructor != null) {
                    try {
                        //XXX: Need this for access to the PluginContext.  How can we get around this?
                        Plugin transAppsPlugin = new Plugin();
                        transAppsPlugin.setName(extension.impl);

                        _prefs.edit()
                                .putString(
                                        pluginLoadedBasename + pluginName,
                                        AppMgmtUtils
                                                .getAppVersionName(
                                                        mapView.getContext(),
                                                        pkgname)
                                                + "-" +
                                                AppMgmtUtils
                                                        .getAppVersionCode(
                                                                mapView.getContext(),
                                                                pkgname))
                                .apply();

                        //XXX: where do I get this?
                        String pluginVersion = "1.0";
                        transAppsPlugin.setDescriptor(
                                new plugins.core.model.PluginDescriptor(
                                        pkgname, pluginVersion));
                        Context pluginContext = this.mapView.getContext()
                                .createPackageContext(
                                        pkgname,
                                        Context.CONTEXT_IGNORE_SECURITY
                                                | Context.CONTEXT_INCLUDE_CODE);
                        // Call the constructor!
                        ret = contextConstructor
                                .newInstance(
                                        new PluginContext(pluginContext,
                                                classLoader));
                    } catch (Throwable constructorException) {
                        Log.w(TAG,
                                "Got an exception instantiating "
                                        + extension.impl
                                        + " with a Context, even though such a constructor exists.",
                                constructorException);
                    }
                }

                // if we still don't have an instantiated object
                if (ret == null) {
                    // try to instantiate it via a no-arg constructor
                    try {
                        ret = implClass.newInstance();
                    } catch (Exception noArgConstructorException) {
                        Log.w(TAG,
                                "Got an exception instantiating "
                                        + extension.impl
                                        + " with a no-arg constructor",
                                noArgConstructorException);
                    }
                }

            } catch (ClassNotFoundException classNotFound) {
                Log.w(TAG, "No class " + extension.impl + " found in plugin "
                        + extension.parent.toString(), classNotFound);
            } catch (Exception e) {
                Log.w(TAG,
                        "Miscellaneous error loading extension "
                                + extension.impl + " from plugin "
                                + extension.parent.toString(),
                        e);
            }

            // last, add this instantiation to the list...
            if (ret != null) {
                Log.d(TAG, "Loaded " + extension.impl + ", for plugin: "
                        + extension.parent.toString());
                pluginInstantiations.put(extension.impl, ret);
                loadedPluginsSet.add(extension.parent.getPackageName());
                incompatiblePluginsSet
                        .remove(extension.parent.getPackageName());
            }

        } else {
            Log.d(TAG, "Using cached (already loaded) plugin extension: "
                    + extension.impl);
        }

        T toRet = null;
        if (ret == null) {
            Log.w(TAG, "Error creating " + extension.impl + ", "
                    + extension.type);
            synchronized (this) {
                incompatiblePluginsSet.add(extension.parent.getPackageName());
            }
        } else {
            // cast it to the proper type
            try {
                toRet = (T) ret;
            } catch (Exception castingException) {
                Log.w(TAG, "Error casting " + extension.impl + " (" + ret
                        + ") to "
                        + extension.type, castingException);
                toRet = null;
                synchronized (this) {
                    incompatiblePluginsSet
                            .add(extension.parent.getPackageName());
                }
            }
        }

        return toRet;
    }

    /**
     * See if the plugin is compatible with this version of ATAK, and set/cache the plugins' api level
     * @param context the context used for to inspect the  package on the system.
     * @param descriptor the descriptor that describes the package
     * @return true if the version of the plugin is TAK compatible.
     */
    static private boolean isTakCompatible(final Context context,
            final PluginDescriptor descriptor) {
        try {
            //cache plugin API on descriptor
            if (FileSystemUtils.isEmpty(descriptor.pluginApi)) {
                descriptor.pluginApi = getPluginApiVersion(context,
                        descriptor.getPackageName(), false);
            }

            return isTakCompatible(descriptor.getPackageName(),
                    descriptor.pluginApi);

        } catch (Exception e) {
            Log.d(TAG, "error occurred verifying signature", e);
        }
        return false;
    }

    /**
     * See if the specific plugin is compatible with this version of ATAK
     * @param context the context to use.
     * @param packageName the package name to check
     * @return true if the package is considered tak compatible.
     */
    static public boolean isTakCompatible(final Context context,
            String packageName) {
        return isTakCompatible(packageName,
                getPluginApiVersion(context, packageName, false));
    }

    /**
     * See if the specific plugin API version is compatible with this version of ATAK
     * Currently must match exactly the ATAK plugin API version (from AndroidManifest.xml)
     * @param packageName the package name
     * @param pluginApiVersion the api version
     * @return true if the package is considered tak compatible.
     */
    static public boolean isTakCompatible(final String packageName,
            final String pluginApiVersion) {

        try {
            final String flavorAtakApiVersion = ATAKConstants
                    .getPluginApi(false);
            final String coreAtakApiVersion = getCoreApi(flavorAtakApiVersion)
                    + ".CIV";
            if (flavorAtakApiVersion.equals(pluginApiVersion) ||
                    coreAtakApiVersion.equals(pluginApiVersion)) {
                Log.d(TAG, "api matches[ " + packageName
                        + "]; pluginApiVersion:" + pluginApiVersion);
                return true;
            } else {
                Log.d(TAG, "api version mismatch[" + packageName
                        + "]; atakApiVersion: "
                        + flavorAtakApiVersion + ", pluginApiVersion: "
                        + pluginApiVersion);
                return false;
            }

        } catch (Exception e) {
            Log.d(TAG, "error occurred verifying api compatibility", e);
        }
        return false;
    }

    /**
     * Since the flavor will provide a packagename@apinumber.flavor, use this to strip the flavor
     * off so we can determine if the plugin base is compatible.   So this means every plugin can
     * either be flavor compatible or base compatible
     * @param api a properly formatted api
     * @return the api with the flavor removed.
     */
    private static String getCoreApi(final String api) {
        int i = api.lastIndexOf('.');
        if (i > 0 && i + 1 < api.length()) {
            if (!Character.isDigit(api.charAt(i + 1))) {
                return api.substring(0, i);
            }
        }
        return api;
    }

    /**
     * Check if the specified package is ATAK
     * @param info the package to check
     * @return true if the package is the main application.
     */
    public static boolean isAtak(PackageInfo info) {
        if (info == null)
            return false;

        return isAtak(info.packageName);
    }

    /**
     * Check if the specified package is ATAK
     * @param packageName the package name to check.
     * @return true if the package describes the core application
     */
    public static boolean isAtak(String packageName) {
        return !FileSystemUtils.isEmpty(packageName)
                && FileSystemUtils.isEquals(packageName,
                        ATAKConstants.getPackageName());
    }

    static public String getPluginApiVersion(final Context context,
            final String packageName, boolean stripPrefix) {
        String apiVersion = "";
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName,
                    PackageManager.GET_META_DATA);

            if (info != null && info.metaData != null) {
                Object value = info.metaData.get("plugin-api");
                if (value instanceof String)
                    apiVersion = (String) value;
                if (stripPrefix)
                    apiVersion = stripPluginApiVersion(apiVersion);
            }

            if (FileSystemUtils.isEmpty(apiVersion)) {
                Log.d(TAG, "Unable to determine api version");
            }
        } catch (Exception e) {
            Log.d(TAG, "error occurred verifying signature", e);
        }
        return apiVersion;
    }

    static public String stripPluginApiVersion(final String api) {
        String apiVersion = api;
        if (!FileSystemUtils.isEmpty(apiVersion)) {
            int index = apiVersion.lastIndexOf('@');
            if (index > 0 && index < apiVersion.length()) {
                apiVersion = apiVersion.substring(index + 1);
            }
        }

        return apiVersion;
    }

    private static class PluginContext extends android.content.ContextWrapper {
        private final ClassLoader classLoader;

        public PluginContext(Context ctx, ClassLoader classLoader) {
            super(ctx);
            this.classLoader = classLoader;
        }

        @Override
        public ClassLoader getClassLoader() {
            return this.classLoader;
        }

        @Override
        public Object getSystemService(String service) {
            Object retval = super.getSystemService(service);
            if (retval instanceof LayoutInflater)
                retval = ((LayoutInflater) retval).cloneInContext(this);
            return retval;
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    // for ApplicationInfo -> ClassLoader

    /// Set of APK source directories that are loadable with buildingClassLoader
    private final Map<String, ClassLoader> loadedSourceDirs = new HashMap<>();

    /**
     * Gets a ClassLoader that is capable of loading classes in the application 
     * referenced by applicationInfo.
     *
     * @param applicationInfo the application information used to get the classloader
     * @return the classloader related to the application.
     */
    private ClassLoader getClassLoader(ApplicationInfo applicationInfo) {
        // prevent re-adding any source directories to the classloader
        if (!loadedSourceDirs.containsKey(applicationInfo.sourceDir)) {
            //String outDir = context.getAppInfo().dataDir;
            ClassLoader buildingClassLoader = new DexClassLoader(
                    applicationInfo.sourceDir, // source directory of the APK
                    mapView.getContext()
                            .getDir("" + applicationInfo.sourceDir.hashCode(),
                                    Context.MODE_PRIVATE)
                            .getAbsolutePath(),
                    applicationInfo.nativeLibraryDir, // native libraries
                    this.mapView.getContext().getClassLoader()); // parent classloader
            loadedSourceDirs
                    .put(applicationInfo.sourceDir, buildingClassLoader);
        }
        return loadedSourceDirs.get(applicationInfo.sourceDir);
    }

    /////////////////////////////////////////////////////////////////////////////
    // for ApplicationInfo -> PluginDescriptor
    //  analyzing assets/plugin.xml

    /**
     * Plugin's description, high-level <plugin> element of plugin.xml, contains
     * a list of Extensions, use SimpleXML to parse with this.
     */
    @Root(name = "plugin")
    public static class PluginDescriptor {
        @ElementList(entry = "extension", inline = true)
        public List<Extension> extensions;

        public transient ApplicationInfo appInfo = null;
        public transient boolean shouldLoad = false;
        public transient String pluginApi = null;

        public String toString() {
            StringBuilder ret = new StringBuilder(getPackageName() + ": ");
            for (Extension extension : extensions) {
                ret.append(extension).append(", ");
            }
            return ret.toString();
        }

        public String getPackageName() {
            if (appInfo != null) {
                return appInfo.packageName;
            } else {
                return null;
            }
        }

        public String getPluginApi() {
            return pluginApi;
        }
    }

    /**
     * Each plugin can have multiple extensions where each extension describes a type
     * (i.e., interface that the plugin implements), and the implementation of that type.
     * Use SimpleXML to parse with this.
     */
    public static class Extension {
        @Attribute
        public String type;
        @Attribute
        public String impl;
        @Attribute(required = false)
        public String singleton;

        /**
         * Reference to plugin containing this extension
         */
        public transient PluginDescriptor parent;

        public String toString() {
            return "[Extension type=" + type + ", impl=" + impl + "]";
        }
    }

    /**
     * Uses the appInfo of an installed application to read its assets/plugin.xml
     * file and load the PluginDescriptor. Note this is currently called for all installed apps
     *
     * @param appInfo
     * @return
     */
    private PluginDescriptor loadPluginDescriptor(ApplicationInfo appInfo) {
        PluginDescriptor plugin = null;
        final String packageName = appInfo.packageName;

        // this app cannot be a plugin
        if (packageName.equals(mapView.getContext().getPackageName())) {
            return null;
        }

        try {
            Resources res = mapView.getContext().getPackageManager()
                    .getResourcesForApplication(packageName);
            AssetManager assets = res.getAssets();
            InputStream fileStream = assets.open("plugin.xml");
            plugin = new Persister().read(PluginDescriptor.class, fileStream);

            plugin.appInfo = appInfo;
            Log.d(TAG, "Successfully loaded plugin descriptor for " + plugin);

            // Check to see if the user has explicitly prevented this plugin from being loaded
            plugin.shouldLoad = PreferenceManager.getDefaultSharedPreferences(
                    mapView.getContext())
                    .getBoolean(SHOULD_LOAD + plugin.getPackageName(),
                            false);

            Log.d(TAG, plugin.toString() + " will "
                    + (plugin.shouldLoad ? "load" : "NOT load"));

            //if we processed plugin.xml, lets also pull plugin-api from AndroidManifest.xml
            plugin.pluginApi = getPluginApiVersion(mapView.getContext(),
                    packageName, false);
        } catch (PackageManager.NameNotFoundException e) {
            Log.v(TAG, "Could not resolve package " + packageName, e);
        } catch (IOException e) {
            Log.v(TAG, "Could not open plugin.xml asset in " + packageName);
        } catch (Exception e) {
            Log.v(TAG, "Error parsing plugin.xml in " + packageName, e);
        }
        return plugin;
    }

    /////////////////////////////////////////////////////////////////////////////
    // for PluginDescriptor -> Plugins Loaded

    /**
     * Loads all the plugins that have been marked PluginDescriptor.shouldLoad
     * @param progressCallback indicates how many extensions have been loaded
     * @return fluid interface
     */
    public AtakPluginRegistry loadPlugins(
            PluginLoadingProgressCallback progressCallback) {
        Log.d(TAG, "loadPlugins");

        // compute the total number of extensions that we need to load (for progress)
        int totalToLoad = 0;

        synchronized (this) {
            for (PluginDescriptor pluginDescriptor : pluginDescriptorSet) {
                if (pluginDescriptor.shouldLoad)
                    totalToLoad += pluginDescriptor.extensions.size();
            }
        }

        int totalLoaded = 0;

        // Load all Lifecycle plugins
        try {
            totalLoaded += loadLifecyclePlugins(pluginDescriptorSet,
                    progressCallback, totalToLoad);
        } catch (IllegalAccessError iae) {
            Log.d(TAG, "error loading lifecycle: ", iae);
        }

        // Load all ToolDescriptor plugins
        try {
            totalLoaded += loadToolDescriptorPlugins(pluginDescriptorSet,
                    progressCallback,
                    totalLoaded, totalToLoad);
        } catch (Error iae) {
            Log.d(TAG, "error loading lifecycle: ", iae);
        }

        if (progressCallback != null) {
            progressCallback.onComplete(totalLoaded);
        }
        Log.d(TAG, "loadPlugins complete");
        return this;
    }

    /**
     * Loads the specified plugin
     * @param pkg the package to load
     * @return fluid interface
     */
    public boolean loadPlugin(String pkg) {

        if (loadedPluginsSet.contains(pkg)) {
            Log.w(TAG, "Plugin already loaded: " + pkg);
            return true;
        }

        Log.d(TAG, "Loading plugin: " + pkg);
        PluginDescriptor toLoad = getPlugin(pkg);
        if (toLoad == null) {
            Log.w(TAG, "Not available, cannot load: " + pkg);
            return false;
        }

        //set shouldLoad, so it will load after restart
        toLoad.shouldLoad = true;
        _prefs.edit().putBoolean(SHOULD_LOAD + toLoad.getPackageName(), true)
                .apply();

        // Load all Lifecycle plugins
        List<PluginDescriptor> plugins = new ArrayList<>();
        int total = 0;
        try {
            plugins.add(toLoad);
            total += loadLifecyclePlugins(plugins, null, 0);
        } catch (IllegalAccessError iae) {
            Log.d(TAG, "error loading lifecycle: ", iae);
        }

        // Load all ToolDescriptor plugins
        try {
            total += loadToolDescriptorPlugins(plugins, null, 0, 0);
        } catch (Error iae) {
            Log.d(TAG, "error loading descriptors: ", iae);
        }

        Log.d(TAG,
                "Loaded plugin: " + pkg + ", with extension count: " + total);
        return total > 0;
    }

    /**
     * Unloads the specified plugin
     *
     * @param pkg the package to unload
     * @return true if the package was unloaded.
     */
    public boolean unloadPlugin(final String pkg) {
        Log.d(TAG, "Unloading plugin: " + pkg);

        List<Pair<String, BroadcastReceiver>> receivers = new ArrayList<>();
        boolean ret = false;
        synchronized (this) {
            PluginDescriptor toUnload = null;
            for (PluginDescriptor pluginDescriptor : pluginDescriptorSet) {
                if (FileSystemUtils.isEquals(pkg,
                        pluginDescriptor.getPackageName())) {
                    toUnload = pluginDescriptor;
                    break;
                }
            }

            if (toUnload != null) {
                Log.d(TAG, "Unloading plugin descriptor: " + pkg);
                //set shouldLoad, so it will load after restart
                toUnload.shouldLoad = false;
                _prefs.edit()
                        .putBoolean(SHOULD_LOAD + toUnload.getPackageName(),
                                false)
                        .apply();
                pluginDescriptorSet.remove(toUnload);
                ret = true;
            }

            ApplicationInfo appInfoToUnload = null;
            for (ApplicationInfo cur : installedAppSet) {
                if (FileSystemUtils.isEquals(pkg, cur.packageName)) {
                    appInfoToUnload = cur;
                    break;
                }
            }

            if (appInfoToUnload != null) {
                Log.d(TAG, "Unloading app info: " + pkg);
                installedAppSet.remove(appInfoToUnload);

                if (loadedSourceDirs
                        .containsKey(appInfoToUnload.sourceDir)) {
                    Log.d(TAG,
                            "Unloading the classloader for item: " + pkg);
                    loadedSourceDirs.remove(appInfoToUnload.sourceDir);
                }
            }

            if (toUnload != null) {
                for (Extension extension : toUnload.extensions) {
                    if (FileSystemUtils.isEquals(extension.type,
                            Lifecycle.class.getName())) {
                        Log.d(TAG, "Unloading Lifecycle: " + extension.impl);

                        try {
                            //remove the plugin lifecyle imple
                            Object o = pluginInstantiations.get(extension.impl);
                            pluginInstantiations.remove(extension.impl);
                            if (!(o instanceof Lifecycle)) {
                                Log.w(TAG,
                                        "Failed to unload Lifecycle extension: "
                                                + extension.impl);
                            }
                            //Lifecycle loadedImpl = (Lifecycle) pluginInstantiations.get(extension.impl);

                            //remove the lifecycle mapcomponent wrapper
                            // TODO synchronized?
                            LifecycleMapComponent mapComponent = pluginLifecycleInstantiations
                                    .get(extension.impl);
                            pluginLifecycleInstantiations
                                    .remove(extension.impl);
                            if (mapComponent == null) {
                                Log.w(TAG, "Failed to unload map component: "
                                        + extension.impl);
                            } else {
                                ((MapActivity) mapView.getContext())
                                        .unregisterMapComponent(mapComponent);
                                //Note the object may still exist in memory, but we have invoked onDestory
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to unload: " + extension.impl,
                                    e);
                        }
                    } else if (FileSystemUtils.isEquals(extension.type,
                            ToolDescriptor.class.getName())) {
                        Log.d(TAG, "Unloading ToolDescriptor: "
                                + extension.impl);

                        Object o = pluginInstantiations.get(extension.impl);
                        pluginInstantiations.remove(extension.impl);
                        if (!(o instanceof ToolDescriptor)) {
                            Log.w(TAG,
                                    "Failed to unload ToolDescriptor extension: "
                                            + extension.impl);

                        }
                        //Note, below we handle tool unloading by removing BroadcastReceivers and ActionBar menus
                        //TODO what if tool.getTool().onActivate is still active for this tool?
                        // i.e. user recently launched tool via action bar Do we need to de-activate?
                    }
                }
                PluginLayoutInflater.dispose();
            }

            if (loadedPluginsSet.contains(pkg)) {
                Log.d(TAG, "Unloading loadedPluginsSet item: " + pkg);
                loadedPluginsSet.remove(pkg);
            }

            // Remove plugin receivers
            for (Pair<String, BroadcastReceiver> receiver : pluginReceivers) {
                if (FileSystemUtils.isEquals(receiver.first, pkg))
                    receivers.add(receiver);
            }
            for (Pair<String, BroadcastReceiver> receiver : receivers)
                pluginReceivers.remove(receiver);
        }

        // Unregister plugin receivers
        unregisterReceivers(receivers);

        // remove from action bar
        List<ActionMenuData> toolMenus = new ArrayList<>();
        for (Pair<String, ActionMenuData> menu : pluginMenus) {
            if (FileSystemUtils.isEquals(menu.first, pkg)
                    && menu.second != null) {
                try {
                    Log.d(TAG, "unregistering previously registered menu: "
                            + pkg);
                    toolMenus.add(menu.second);
                } catch (Exception e) {
                    Log.d(TAG, "error occurred unregistering menu: ", e);
                }
            }
        }
        if (toolMenus.size() > 0) {
            Log.d(TAG,
                    "Broadcasting intent to remove: " + toolMenus.size()
                            + " tools from the ActionBar");
            Intent intent = new Intent(ActionBarReceiver.REMOVE_TOOLS);
            intent.putExtra("menus",
                    toolMenus.toArray(new ActionMenuData[0]));
            AtakBroadcast.getInstance().sendBroadcast(intent);
        }

        Log.d(TAG, "Unloaded plugin: " + pkg);
        return ret;
    }

    /**
     * Unregister a list of plugin receivers
     * @param receivers List of plugin receivers (in pairs w/ package first)
     */
    private static void unregisterReceivers(
            List<Pair<String, BroadcastReceiver>> receivers) {
        for (Pair<String, BroadcastReceiver> receiver : receivers) {
            try {
                Log.d(TAG, "unregistering previously registered receiver: "
                        + receiver.first);
                AtakBroadcast.getInstance().unregisterReceiver(receiver.second);
            } catch (Exception e) {
                Log.d(TAG, "error occurred unregistering listener: ", e);
            }
        }
    }

    /**
     * Load all Plugins based on a collection of PluginDescriptors.
     * @param progressCallback the callback that is used when loading the plugins
     * @param totalToLoad the total count to load used to compute the percentage
     * @return the total count actually loaded
     */
    private int loadLifecyclePlugins(Collection<PluginDescriptor> plugins,
            PluginLoadingProgressCallback progressCallback, int totalToLoad) {

        //use float for progress calc
        float completed = 0;

        //TODO less synchronization/blocking?
        synchronized (this) {
            //loop all specified plugins
            for (PluginDescriptor plugin : plugins) {
                if (!plugin.shouldLoad) {
                    Log.d(TAG,
                            "!should load, skipping Lifecycle plugins extensions: "
                                    + plugin.toString());
                    continue;
                }

                List<Lifecycle> lifecycles = this.loadExtension(plugin,
                        Lifecycle.class);

                for (Lifecycle l : lifecycles) {
                    Log.d(TAG, "Loading new Lifecycle plugin: " + l);
                    try {
                        LifecycleMapComponent mapComponent = new LifecycleMapComponent(
                                l, plugin.getPackageName());
                        ((MapActivity) mapView.getContext())
                                .registerMapComponent(mapComponent);
                        pluginLifecycleInstantiations.put(l.getClass()
                                .getName(), mapComponent);
                        completed++;
                        if (totalToLoad > 0) {
                            if (progressCallback != null) {
                                float progress = PROGRESS_DESCRIPTORS
                                        + (completed / totalToLoad)
                                                * (100 - PROGRESS_DESCRIPTORS);
                                progressCallback
                                        .onProgressUpdated((int) progress);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Problem executing Lifecycle plugin " + l,
                                e);
                    } catch (LinkageError e) {
                        Log.w(TAG, "Problem loading Lifecycle plugin " + l, e);
                    }
                }
            }
        }
        return (int) completed;
    }

    /**
     * Load all ToolDescriptors
     *
     * @param plugins a collection of plugins from the scan proccess
     * @param progressCallback the progress callback to be used when the plugins are being loaded
     * @param alreadyLoaded the number of tools already loaded
     * @param totalToLoad the number of descripters to attempt to load.
     * @return the total number of descriptors loaded which is not the same as the total number of
     * plugins.
     */
    private int loadToolDescriptorPlugins(Collection<PluginDescriptor> plugins,
            PluginLoadingProgressCallback progressCallback, int alreadyLoaded,
            int totalToLoad) {

        List<ActionMenuData> toolMenus = new ArrayList<>();
        //use float for progress calc
        float completed = 0;

        //TODO less synchronization/blocking?
        synchronized (this) {
            //loop all specified plugins
            for (PluginDescriptor plugin : plugins) {
                if (!plugin.shouldLoad) {
                    Log.d(TAG,
                            "!should load, skipping ToolDescriptor plugin extensions: "
                                    + plugin.toString());
                    continue;
                }

                //get ToolDescriptors for this plugin
                List<ToolDescriptor> toolDescriptors = this.loadExtension(
                        plugin, ToolDescriptor.class);

                //loop all ToolDesciptors for this plugin
                for (final ToolDescriptor toolDescriptor : toolDescriptors) {
                    try {
                        Log.d(TAG, "Loading new ToolDescriptor plugin: "
                                + toolDescriptor);
                        final String shortDesc = toolDescriptor
                                .getShortDescription();
                        if (shortDesc != null) {
                            final String intentAction = "com.atak.plugin.selected."
                                    +
                                    plugin.getPackageName() + "." +
                                    toolDescriptor.getClass();
                            //Log.d(TAG, "shb: " + intentAction);

                            BroadcastReceiver receiver = new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context pluginContext,
                                        Intent intent) {
                                    Log.d(TAG,
                                            "Received intent to open ToolDescriptor "
                                                    + toolDescriptor
                                                            .getShortDescription());
                                    final Tool tool = toolDescriptor.getTool();

                                    Bundle extrasToSendToPlugin = (intent
                                            .getExtras() != null)
                                                    ? intent.getExtras()
                                                    : new Bundle();

                                    final Tool.ToolCallback callback = new Tool.ToolCallback() {
                                        @Override
                                        public void onInvalidate(Tool tool) {
                                            Log.d(TAG,
                                                    "onInvalidate callback for "
                                                            + tool);
                                        }

                                        @Override
                                        public void onToolDeactivated(
                                                Tool tool) {
                                            Log.d(TAG,
                                                    "onToolDeactivated callback for "
                                                            + tool);
                                        }
                                    };

                                    final ViewGroup toDisplay = new android.widget.LinearLayout(
                                            pluginContext);

                                    // Tell the plugin that it's activated!
                                    try {
                                        tool.onActivate((MapActivity) mapView
                                                .getContext(),
                                                atakMapView,
                                                toDisplay,
                                                extrasToSendToPlugin,
                                                callback);
                                    } catch (Exception e) {
                                        Log.w(TAG,
                                                "Problem executing ToolDescriptor plugin "
                                                        + toolDescriptor,
                                                e);
                                    }
                                }
                            };
                            AtakBroadcast.getInstance().registerReceiver(
                                    receiver,
                                    new DocumentedIntentFilter(
                                            intentAction));
                            //cache off for later, so we can unregister as needed
                            pluginReceivers
                                    .add(new Pair<>(
                                            plugin
                                                    .getPackageName(),
                                            receiver));

                            String iconId = PluginMapComponent
                                    .addPluginIcon(toolDescriptor);

                            ArrayList<ActionClickData> temp = new ArrayList<>();
                            temp.add(new ActionClickData(
                                    new ActionBroadcastData(
                                            intentAction, null),
                                    ActionClickData.CLICK));

                            ActionMenuData actionMenuData = new ActionMenuData(
                                    "plugin://" + plugin.getPackageName() + "/"
                                            + toolDescriptor.getClass()
                                                    .getName(),
                                    toolDescriptor.getShortDescription(),
                                    iconId,
                                    iconId,
                                    iconId,
                                    "overflow",
                                    true,
                                    temp,
                                    /*null,*/
                                    false,
                                    false,
                                    false);
                            toolMenus.add(actionMenuData);

                            pluginMenus.add(new Pair<>(
                                    plugin.getPackageName(),
                                    actionMenuData));

                            completed++;

                            if (progressCallback != null && totalToLoad > 0) {
                                float progress = PROGRESS_DESCRIPTORS
                                        + ((alreadyLoaded + completed)
                                                / totalToLoad)
                                                * (1 - PROGRESS_DESCRIPTORS);
                                progressCallback
                                        .onProgressUpdated((int) progress);
                            }
                        }

                    } catch (Exception ex) {
                        Log.w(TAG, "Exception while loading tool plugin "
                                + toolDescriptor, ex);
                    }
                }
            }
        }

        Log.d(TAG,
                "Broadcasting intent to add: " + toolMenus.size()
                        + " new tools to the ActionBar");
        Intent intent = new Intent(ActionBarReceiver.ADD_NEW_TOOLS);
        intent.putExtra("menus",
                toolMenus.toArray(new ActionMenuData[0]));
        AtakBroadcast.getInstance().sendBroadcast(intent);

        return (int) completed;
    }
}
