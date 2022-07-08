
package com.atakmap.app.system;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.LayoutInflater;

import com.atakmap.annotations.DeprecatedApi;
import com.atakmap.app.ATAKApplication;
import com.atakmap.app.BuildConfig;
import com.atakmap.coremap.io.IOProviderFactory;
import com.atakmap.coremap.log.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dalvik.system.DexClassLoader;

/**
 * Attenpt to load system level changes that can be made to ATAK by installing one or more APKs.
 * These changes are more rigid and are not considered plugins since they cannot be loaded and
 * unloaded during runtime.   These are loaded during system start and remain loaded until the
 * ATAK application is finished.
 */
public class SystemComponentLoader {

    public static final String TAG = "SystemComponentLoader";

    private static AbstractSystemComponent flavorComponent;
    private static AbstractSystemComponent encryptionComponent;
    private static AbstractSystemComponent raptorApiComponent;
    private static String encryptionName = null;
    private static String flavorCrashInfo;
    private static String encryptionCrashInfo;
    private static String raptorApiCrashInfo;

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

    private static ClassLoader getClassLoader(Context context,
            ApplicationInfo applicationInfo) {
        return new DexClassLoader(
                applicationInfo.sourceDir, // source directory of the APK
                context.getDir("" + applicationInfo.sourceDir.hashCode(),
                        Context.MODE_PRIVATE)
                        .getAbsolutePath(),
                applicationInfo.nativeLibraryDir, // native libraries
                context.getClassLoader()); // parent classloader
    }

    /**
     * Construct a Context that takes into account the classloader and the context for the plugin.
     * @param context the application context
     * @param info the application information
     * @param pluginClassLoader the plugin class loader
     * @return a plugin context that is used to look up resources from the plugin.
     */
    private static Context getPluginContext(Context context,
            ApplicationInfo info, ClassLoader pluginClassLoader) {
        try {
            Context c = context.createPackageContext(
                    info.packageName,
                    Context.CONTEXT_IGNORE_SECURITY
                            | Context.CONTEXT_INCLUDE_CODE);
            return new PluginContext(c, pluginClassLoader);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Perform a check on the plugin to make sure it both matches the required API
     * @param info the application information
     * @return true if the application api is set correctly and the signature matches the core TAK
     * signature.
     */
    private static boolean check(final ApplicationInfo info,
            final Context context) {
        try {
            final PackageManager pm = context.getPackageManager();

            // flavors are designed to augment a core api - so verify that the core api for the flavor
            // matches the core api provided by the core.
            final String apk = getCoreApi(getAPI(info)) + ".CIV";
            final String self = getAPI(
                    pm.getApplicationInfo(context.getPackageName(),
                            PackageManager.GET_META_DATA));
            if (!self.equalsIgnoreCase(apk)) {
                Log.d(TAG, "api does not match, needed: " + self
                        + " but the system plugin had: " + apk);
                return false;
            }
            if (verifySignature(context, info.packageName))
                return true;
        } catch (Exception e) {
            Log.d(TAG, "error occurred checking the system plugin", e);
        }
        return false;
    }

    private static String getAPI(ApplicationInfo info) {
        if (info != null && info.metaData != null) {
            Object value = info.metaData.get("plugin-api");
            if (value instanceof String) {
                return (String) value;
            }
        }
        return "";
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
    private static boolean verifySignature(final Context context,
            final String pkgname) {
        try {
            final PackageManager pm = context.getPackageManager();
            final PackageInfo atak = pm.getPackageInfo(context.getPackageName(),
                    PackageManager.GET_SIGNATURES);
            final PackageInfo pi = pm.getPackageInfo(pkgname,
                    PackageManager.GET_SIGNATURES);
            for (Signature sig : pi.signatures) {
                if (BuildConfig.BUILD_TYPE.equalsIgnoreCase("sdk")) {
                    Log.d(TAG, "SDK skipping signature check[" + pkgname + "]");
                    return true;
                } else if (sig.equals(atak.signatures[0])) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "error occurred verifying signature", e);
        }

        Log.w(TAG, "wrong signature (refusing to load) [" + pkgname + "]");

        return false;
    }

    /**
     * Retrieve the first application that contains the specified prefix as part of the package
     * name.
     * @param prefix the prefix of the package name to be used.
     * @return the application info
     */
    private static ApplicationInfo getApplicationInfo(final PackageManager pm,
            final String prefix) throws PackageManager.NameNotFoundException {
        ApplicationInfo info = null;
        List<ApplicationInfo> list = pm
                .getInstalledApplications(PackageManager.GET_META_DATA);
        for (ApplicationInfo ai : list) {
            if (ai.packageName.startsWith(prefix)) {
                info = ai;
                break;
            }
        }
        if (info == null)
            throw new PackageManager.NameNotFoundException();

        return info;
    }

    /**
     * Attempt to load a flavor system component
     * @param context the system context used to load the flavor component
     */
    public synchronized static void initializeFlavor(final Context context) {
        if (flavorComponent != null)
            return;

        final PackageManager pm = context.getPackageManager();
        final ApplicationInfo info;
        try {
            long start = SystemClock.elapsedRealtime();
            info = getApplicationInfo(pm, "com.atakmap.app.flavor.");
            Log.d(TAG, "loading flavor "
                    + (SystemClock.elapsedRealtime() - start) + "ms");
            flavorCrashInfo = infoToJson(info.packageName, context);
        } catch (Exception e) {
            Log.d(TAG, "no flavor system component found");
            return;
        }

        try {
            if (!check(info, context))
                return;

            final ClassLoader pluginClassLoader = getClassLoader(context, info);
            final Context pluginContext = getPluginContext(context, info,
                    pluginClassLoader);

            final Class<?> c = pluginClassLoader
                    .loadClass("com.atakmap.android.FlavorComponent");

            if (!ensureAbstractImpl(AbstractSystemComponent.class, c))
                return;

            flavorComponent = (AbstractSystemComponent) c.newInstance();

            if (!(flavorComponent instanceof FlavorProvider)) {
                Log.d(TAG,
                        "flavor does not implement FlavorProvider, this is an error");
            }
            flavorComponent.setPluginContext(pluginContext);
            flavorComponent.setAppContext(context);

        } catch (Throwable e) {
            Log.d(TAG, "error loading the flavor system component", e);
            flavorComponent = null;
        }
    }

    /**
     * Given an implementation of a class and the corresponding abstract class,
     * ensure that the implementation does provide all of the abstract methods.
     * This attempts to prevent issues with running obfuscation mismatch.
     *
     * For example a obfuscated version of the FlavorPlugin and a non-obfuscated
     * version of the core.   The only people that should encounter this are
     * core developers since we also enforce that signing of the system level
     * plugins and the core must be the same.
     *
     * @param abstractClass the abstract class
     * @param implClass the implementation class
     * @return returns true if all abstract methods in the abstract class
     * are provided in the implementation class.
     */
    private static boolean ensureAbstractImpl(Class abstractClass,
            Class implClass) {
        Method[] methods = abstractClass.getMethods();
        for (Method method : methods) {
            if (Modifier.isAbstract(method.getModifiers())) {
                String s = method.getName();
                try {
                    Method m = implClass.getMethod(method.getName(),
                            method.getParameterTypes());
                } catch (Throwable e) {
                    Log.d(TAG, "could not find method: " + method.getName());
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Attempt to load an encryption system component.
     * @param activity the activity used to load the encryption component.
     */
    public synchronized static void initializeEncryption(
            final Activity activity) {
        if (encryptionComponent != null) {
            return;
        }

        final PackageManager pm = activity.getPackageManager();
        final ApplicationInfo info;
        try {
            long start = SystemClock.elapsedRealtime();
            info = getApplicationInfo(pm, "com.atakmap.app.flavor.encryption.");
            PackageInfo pInfo = pm.getPackageInfo(info.packageName,
                    PackageManager.GET_META_DATA);
            Log.d(TAG, "loading encryption "
                    + (SystemClock.elapsedRealtime() - start) + "ms");
            encryptionName = info.loadLabel(pm) + " " + pInfo.versionName;
            encryptionCrashInfo = infoToJson(info.packageName, activity);
        } catch (Exception e) {
            Log.d(TAG, "no encryption system component found");
            IOProviderFactory.registerProvider(new ATAKFileIOProvider(), true);
            return;
        } finally {
            // After the SystemComponents have been loaded, ensure that the applications native
            // jni crash log capability is initialized correct.
            // Instead of augmenting the
            ((ATAKApplication) activity.getApplication())
                    .notifyEncryptionChanged();
        }

        try {
            if (!check(info, activity))
                return;

            final ClassLoader pluginClassLoader = getClassLoader(activity,
                    info);
            final Context pluginContext = getPluginContext(activity, info,
                    pluginClassLoader);

            final Class<?> c = pluginClassLoader
                    .loadClass("com.atakmap.android.EncryptionComponent");

            if (!ensureAbstractImpl(AbstractSystemComponent.class, c))
                return;

            encryptionComponent = (AbstractSystemComponent) c.newInstance();
            encryptionComponent.setPluginContext(pluginContext);
            encryptionComponent.setAppContext(activity);
        } catch (Throwable e) {
            Log.d(TAG, "error loading the encryption system component", e);
            encryptionComponent = null;
            IOProviderFactory.registerProvider(new ATAKFileIOProvider(), true);
        } finally {
            // After the SystemComponents have been loaded, ensure that the applications native
            // jni crash log capability is initialized correct.
            // Instead of augmenting the
            ((ATAKApplication) activity.getApplication())
                    .notifyEncryptionChanged();
        }
    }

    /**
     *
     * Attempt to load the RaptorApi system component.
     *
     * @param context The context used to load the system component
     */
    public synchronized static void initializeRaptorApi(final Context context) {
        if (raptorApiComponent != null)
            return;

        final PackageManager pm = context.getPackageManager();
        final ApplicationInfo info;
        try {
            long start = SystemClock.elapsedRealtime();
            info = getApplicationInfo(pm, "gov.raptortak.plugin");
            Log.d(TAG, "loading RaptorApi "
                    + (SystemClock.elapsedRealtime() - start) + "ms");
            raptorApiCrashInfo = infoToJson(info.packageName, context);
        } catch (Exception e) {
            Log.d(TAG, "no TAKX system component found");
            return;
        }

        try {
            if (!check(info, context))
                return;

            final ClassLoader pluginClassLoader = getClassLoader(context, info);
            final Context pluginContext = getPluginContext(context, info,
                    pluginClassLoader);

            final Class<?> c = pluginClassLoader
                    .loadClass("gov.raptortak.RaptorTakSystemComponent");

            if (!ensureAbstractImpl(AbstractSystemComponent.class, c))
                return;

            raptorApiComponent = (AbstractSystemComponent) c.newInstance();
            raptorApiComponent.setPluginContext(pluginContext);
            raptorApiComponent.setAppContext(context);

        } catch (Throwable e) {
            Log.d(TAG, "error loading the RaptorApi system component", e);
            raptorApiComponent = null;
        }
    }

    /**
     * Allow for system level plugins to be notified of changes in the state of the application
     * PAUSE, RESUME and when it is being shut down (DESTROYED).
     */
    public static void notify(AbstractSystemComponent.SystemState state) {
        if (encryptionComponent != null)
            encryptionComponent.notify(state);
        if (encryptionComponent != null)
            encryptionComponent.notify(state);
        if (raptorApiComponent != null)
            raptorApiComponent.notify(state);
    }

    /**
     * Throws a Security Exception if the method is called with a stacktrace that does not include
     */
    public static void securityCheck() {

        StackTraceElement[] stackTraceElements = Thread.currentThread()
                .getStackTrace();

        for (StackTraceElement stackTraceElement : stackTraceElements) {
            final String className = stackTraceElement.getClassName();
            if (className.equals("com.atakmap.android.EncryptionComponent") ||
                    className.equals("com.atakmap.android.FlavorComponent"))
                return;
        }
        throw new SecurityException(
                "method can only be called by a SystemComponent");
    }

    /**
     * Obtain a flavor provider if it is installed.
     * @return null if no flavor is installed.
     */
    public static FlavorProvider getFlavorProvider() {
        return (FlavorProvider) flavorComponent;
    }

    /**
     * Returns the list of abstract components in order of importance.
     * @return
     */
    public static AbstractSystemComponent[] getComponents() {
        // order is important //
        final List<AbstractSystemComponent> components = new ArrayList<>();
        if (flavorComponent != null)
            components.add(flavorComponent);
        if (encryptionComponent != null)
            components.add(encryptionComponent);
        if (raptorApiComponent != null)
            components.add(raptorApiComponent);
        return components.toArray(new AbstractSystemComponent[0]);
    }

    /**
     * Unregister the registered encryption provider and replace it with the generic unencrypted
     * ATAKFileIOProvider.
     * @param activity the ATAKApplication to call notifyEncryption changed on.
     */
    public static void disableEncryptionProvider(final Activity activity) {
        IOProviderFactory.registerProvider(new ATAKFileIOProvider(), true);
        ((ATAKApplication) activity.getApplication())
                .notifyEncryptionChanged();

    }

    /**
     * Obtain the flavor API.
     * @return null if a flavor is not installed.
     */
    public static String getFlavorAPI(final Context context) {
        // only consider the API valid if the flavor component has passed the security checks and
        // loaded.
        if (flavorComponent == null)
            return null;

        try {
            final PackageManager pm = context.getPackageManager();
            ApplicationInfo info = getApplicationInfo(pm,
                    "com.atakmap.app.flavor.");

            if (info.metaData != null) {
                Object value = info.metaData.get("plugin-api");
                if (value instanceof String) {
                    return (String) value;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Returns true if a custom encryption module is installed.
     */
    public static String getEncryptionComponentName() {
        return encryptionName;
    }

    private static String infoToJson(String pkg, Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(pkg,
                    PackageManager.GET_META_DATA);
            PackageInfo pInfo = pm.getPackageInfo(pkg,
                    PackageManager.GET_META_DATA);
            return "{ \"name\": \"" + info.loadLabel(pm) + "\" , " +
                    "\"versionName\": \"" + pInfo.versionName + "\" , " +
                    "\"versionCode\": \"" + pInfo.versionCode + "\" }";
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Get crash log information for better trouble shooting as an entry in a larger json file.
     * @return the crash log informatino in json format.
     */
    public static String getCrashLogInfo() {
        List<String> crashInfo = new ArrayList<>(Arrays.asList(flavorCrashInfo,
                encryptionCrashInfo, raptorApiCrashInfo));
        crashInfo.removeAll(Arrays.asList(null, ""));

        return "\"system.plugins\": [" + TextUtils.join(", ", crashInfo) + "]";
    }
}
