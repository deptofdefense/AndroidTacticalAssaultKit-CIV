Overview
====

ATAK is a Moving Map capability for the Android OS.   Respository development should make use of the model as outlined by https://nvie.com/posts/a-successful-git-branching-model/ which closely matches the WinTAK development model.

The moving map capability is based on a core civilian capability that is built first and then flavored with either the military or fms capabilities.

Developer Notes
====

*Please make sure to read these for solutions to commonly encountered issues*


14 October 2020 - ATAK will *require* core developers using Android Studio to set the Launch Option for ATAK Activity to be manually set.  This is because Android Studio has a difficult time determining if you are running a flavor or unflavored version of ATAK.   Under the Run/Debug configuration for ATAK, please set the Launch Activity to com.atakmap.app.ATAKActivity

If you do not do this you will get the following message from Android Studio with a failure to launch ATAK

    $ adb shell am start -n "com.atakmap.app.civ/com.atakmap.app.ATAKActivityCiv" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
    Error while executing: am start -n "com.atakmap.app.civ/com.atakmap.app.ATAKActivityCiv" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
    Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] cmp=com.atakmap.app.civ/com.atakmap.app.ATAKActivityCiv }
    Error type 3
    Error: Activity class {com.atakmap.app.civ/com.atakmap.app.ATAKActivityCiv} does not exist.


05 October 2020 - NDK installation is now required.

04 May 2020 - ATAK is compiled to use targetSdkVersion 29 but still provide support for the minSdkVersion 21.    Be careful when developing new code within core to make sure that appropriate safeguards are in place that retain system compatibility for minSdkVersion 21.


20 March 2020 - With gradle 6.0.1, if you are building from the command line and fail to set ANDROID_HOME, the build process will fail with a message similiar to this:
      
      * What went wrong:
          Task 'assembleDebug' not found in root project 'ATAK'.

Please make sure to set the ANDROID_HOME environment variable or create a local.properties file to fix this issue.
The local.properties file should have the following line:
sdk.dir=/path/to/android/sdk


Requirements for Development
====

The following tools are required (at a minimum) to compile and deploy ATAK:

- Java Development Kit 1.8 (OpenJDK https://adoptopenjdk.net/)
- git client 2.19 
- git-lfs

If you are using the command line to build and deploy ATAK:

- gradlew

If using an IDE:

- Android Studio 

The following is *required* and only useful if you are creating native libraries. 
The version that must be used is NDK 12b.    

Windows:
     https://dl.google.com/android/repository/android-ndk-r12b-windows-x86.zip
     https://dl.google.com/android/repository/android-ndk-r12b-windows-x86_64.zip
Linux:
     https://dl.google.com/android/repository/android-ndk-r12b-linux-x86_64.zip
Mac:
     https://dl.google.com/android/repository/android-ndk-r12b-darwin-x86_64.zip



All development should make use of the android-formatting.prefs rules, 
use LF (Unix) breaks, and should contain sufficient documentation to 
be used by development team. 

Prior to code review or periodically, your code will be stripped of CR's and 
hard tabs.   It is also a good idea to make use of the code formatting files 
in the root of the directory.   Automatic code formatting is run on occasion.
              
Consideration of thirdparty libraries need to occur early in a release cycle.  
Without proper attribution and appropriate review, third party libraries will 
be removed.


What do I have access to
===

ATAK can be distributed in many forms.    This document attempts to describe the 
common setup for all of these forms.

1) As an API in a zip file / tar.    This level of access does not require any GIT 
   access.   The files included in this package are self contained and do make use
   of the gradle build system.   The atak.apk provided in this package is required 
   for development of the plugins.

2) SDK Repository Access.   For developers who would like to have access to updates
   to the SDK when they occur.  This access also allows for access to other community
   developed plugins for ATAK.   

3) Main Repository Access.   For core access only and not recommended for plugin 
   development.  




Steps for building:
===

Please see the generalized steps for building as detailed in the ATAK_Plugin_Development_Guide.pdf guide.



Using an Emulator
====
When using an emulator, ATAK might crash due to incompatibilities with the 
computers graphics card. A few potential work arounds are:

1) construct a file in the /sdcard/atak directory called "opengl.broken"
   - this will set USE_GENERIC_EGL_CONFIG true 

2) set the system property "USE_GENERIC_EGL_CONFIG" to  "true"

3) If all else fails modify code
./ATAKMapEngine/src/com/atakmap/map/opengl/GLMapSurface.java:129

    if (System.getProperty("USE_GENERIC_EGL_CONFIG", "false").equals("true"))
to be:
    if (true)

If EGL is activated successfully, you should see a log message stating:
"application has been informed that OPEN GL is a bit busted"

If using AVD, it may be necessary to switch the GPU mode. This can be done by
accessing the Settings for your emulated device, then going to the Settings tab,
then Advanced tab. From here you can select the OpenGL ES Renderer, which is the
setting that may need to be changed. The emulated device will need to be cold
booted for the change to be effective. If all else fails, AVD may be launched
from the command line and passed the -gpu flag with various options--"host" and
"guest" are likely the most helpful.


ATAK
====

ATAK is a moving map capability that is comprised of several key concepts:

     Tools - Provide a capabilty for interacting with the map on the right hand side of the 
     screen.   They may or may not have a associated Drop Down component.    Tools are considered
     a legacy concept.

     DropDowns - All of the right hand functionality within ATAK is achieved by the concept of a 
     drop down.  A drop down receiver is what is implemented to inject functionality into the 
     ATAK system. 

     AbstractMapComponent -  An implementation of an AbstractMapComponent is usually used to 
     create the and manage one or more drop down receivers.   Any implementation of the 
     AbstractMapComponent is loaded by a XML file in trunk/ATAK/assets/components.xml

     MapView - The map view is the moving map capability within ATAK.    There are many examples 
     on how Tools and DropDowns make use of the MapView.

     ATAKActivity - This is responsible for the startup, creation, cleanup, and shutdown of ATAK
     proper.

     AtakBroadcast - Used instead of global usage of sendBroadcast, registerReceiver, unregisterReceiver.
     If you need to send a system wide intent, use sendSystemBroadcast if required.    


A MapComponent is the basic unit of functionality for major functionality within 
ATAK.   A MapComponent can have one or more DropDownReceivers, Tools, Widgets. 
Each MapComponent should live in com.atakmap.android at this time and be registered
in the ATAK/assets/components.xml.   Additionally, if there is a DropDownReceiver, 
it needs to be registered in the DefaultActionBars.xml file.   Dynamic registration
of preferences associated with the components will allow for easier maintenance 
as these certain components are created as "plugins".



Code Structure
====

ATAK is comprised of 3 Android projects.

      ATAKMapEngine    
      MapCoreInterfaces
      ATAK


ATAK has additional directories 

      doc - documenation relevant to developers.

Plugins
===

Plugins for ATAK are additional Android Applications that are built but cannot be run stand alone.   These plugins are built so that at runtime they rely on the internal classes within the ATAK application.  This is done through a special classloader that knows how to join up the missing class files from the plugin application at run time.    During compile time, the plugin only needs to reference the classes using the provided keyword.

HelloWorld Plugin
-----------------

A good example of a plugin that does several different tasks is the Helloworld plugin.   This plugin is structured to be very minimalistic and contains a single user experience.   The plugin describes the capabilitities within the assets/plugin.xml file under the plugins/helloworld directory.    The java src code contains comment to guide the developer through the mechanics of how a plugin works.
 
Plugin Debugging
===

Plugins are not processes.   When deployed to the device, they are just containers that are pulled into the ATAK process space.  
1) Set corresponding break points within the plugin code.
2) You will need to direct Android Studio to attach to an existing process.
     Under the Run menu
        Attach debugger to existing process.
        Show all process
        Select com.atakmap.app.
3) Once that has been done, you should see when your breakpoint is hit, 
   the debugger will stop appropriately.

Formatting
====

Source code within the ATAK repository is formatted using a set of Eclipse rules that exist in the
root of the repository.   In order to use these rules with Android Studio, we require the installation
of the EclipseCodeFormatter plugin.   Once installed, the required configuration files are all set up.
Within Android Studio

   With newer versions of Android Studio you may need to install it from a file
                   https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter

   Settings->
          Plugins->
              Type in Eclipse in the Search Box
              Tap Browse Repository Button
              Select the Eclipse Code Formatter Plugin 
       Once Android Studio restarts, then you should be able to perform normal code formatting per the 
       ATAK formatting rules.


Plugins
====


The plugin architecture is no different than developing code internally to ATAK.   
In fact, all ATAK components use the same constructs and capabilities.    There is 
not a single document that could cover the entire capabilities of the plugin
architecture, but there are several examples that show how to do basic capabilities.


Plugin Structure
===

The assets/plugin.xml contains the information required to load a plugin.
Plugins may implement Lifecycle and/or Tools.   In the helloworld example, the 
plugin.xml file contains both.

By convention the lifecycle and tool are mostly boilerplate code and can be 
mostly duplicated from one project to another.   In the ATAK architecture, a 
Lifecycle closely relates to a MapComponent.  A Tool is used to populate the 
action bar and describe the intent that is fired by invoking the icon on the 
actionbar.

When developing in the plugin architecture, it is important to remember that 
there are two context's in use.   The primary context is the ATAK context and
should be used when visual components such as AlertDialog is being constructed.   
The secondary context is the plugin's context and should be used when looking up
resources and graphics specific to the plugin.  Keep this in mind during 
development.   Using the wrong context can lead to runtime crashes or wrong 
visual behavior.

A few notes:

1) Notifications cannot reference a resource from the plugin.  The implementation 
of the Notification class is not capable of realizing where to look up a resource.
In the helloworld plugin, there is an example of how to have your plugin display an
appropriate small icon.

2) Spinners will crash on devices depending how the theme is set.   For this purpose,
ATAK provides a com.atakmap.android.gui.PluginSpinner class which is identical 
to a real android Spinner.

3) Signing a Plugin -  If you choose to develop a plugin, the key alogrithm must be RSA and the Signing Algorithm must be SHA1withRSA.

4) Some plugins will fail to compile using gradle with an obscure ndkStrip error when it contains no native libraries.   Please unset ANDROID_NDK_HOME and/or move it out of the ANDROID_SDK_HOME/NDK directory.   This is an issue with the gradle build system at this time. The specific error message is:
    :transformNativeLibsWithStripDebugSymbolForDebug FAILED
    
    FAILURE: Build failed with an exception.
    
    * What went wrong:
    Execution failed for task ':transformNativeLibsWithStripDebugSymbolForDebug'.
    > java.lang.NullPointerException (no error message)




Useful Information
====

1) Filing a bug with a screen capture.    
     There are many ways to capture the screen for bug submission.  By default
     a large png file is produced.   If you have access to adb and would like 
     to capture directly to a jpg, you can execute:

      unix - 
        adb shell screencap -p /sdcard/screen.png && adb pull /sdcard/screen.png
      windows - 
        adb shell screencap -p /sdcard/screen.png 
        adb pull /sdcard/screen.png

2) To record a video from the command line of the screen.
      adb shell screenrecord /sdcard/demo.mp4
      adb pull /sdcard/demo.mp4

     Stop the screen recording by pressing Ctrl-C, 
     otherwise the recording stops automatically at 
     three minutes or the time limit set by --time-limit.

3) To print out the certificate for the a specific APK
     unzip -p Name-of-apk.apk META-INF/CERT.RSA | keytool -printcert

       - or -

     unzip -p Name-of-apk.apk META-INF/CERT.DSA | keytool -printcert
  
       - or -
 
     keytool -printcert -jarfile Name-of-apk.apk

   To sign a apk with multiple certificates or to strip the certificate and add a new one 

      - 26.0.2 build-tools apksigner allows for multiple key stores to be specified without using keystore
        rotation (but both keystores are required to be known)

          $ANDROID_SDK/./build-tools/26.0.2/apksigner sign --ks keystore1 --next-signer --ks keystore2
      
      - apksigner allows for existing keystore to be stripped and a new one added

          $ANDROID_SDK/./build-tools/26.0.2/apksigner sign --ks keystore1 
 
      - the latest apksigner requires multiple keystores to be treated as a app signature rotation which is 
        stored different within the file
      

4) Since ATAK has now migrated to crypted databases I have been getting questions on how to examine them for debugging purposes.   Please see https://github.com/sqlitebrowser/sqlitebrowser/releases      This allows for you to decrypt the databases if you know your passphrase.

5) Go to File -> Settings or press CTRL + ALT + S . The following window will open and check Show quick doc on mouse move under IDE Settings -> Editor. Or just press CTRL and hover your move over your method, class ... If you just need a shortcut, then it is Ctrl + Q on Linux (and Windows).

6) To execute a gitlab-runner locally 
         
         gitlab-runner exec docker sdk 

  NOTE: If you plugin makes use of git-lfs and you are trying to run it locally

         gitlab-runner exec docker --pre-clone-script "git config --global lfs.url https://gitlab-ci-token:<your token>@repo-url/info/lfs" assembleMilRelease


7) Testing your app with App Standby

To test the App Standby mode with your app:

    Configure a hardware device or virtual device with an Android 6.0 (API level 23) or higher system image.
    Connect the device to your development machine and install your app.
    Run your app and leave it active.
    Force the app into App Standby mode by running the following commands:

    $ adb shell dumpsys battery unplug
    $ adb shell am set-inactive com.atakmap.app true

    Simulate waking your app using the following commands:

    $ adb shell am set-inactive com.atakmap.app false
    $ adb shell am get-inactive com.atakmap.app

    Observe the behavior of your app after waking it. Make sure the app recovers gracefully from standby mode. In particular, you should check if your app's Notifications and background jobs continue to function as expected. 

