COMPILATION

helloword is a complete skeleton project that can be used as a starting point 
for developing ATAK private plugins.  


Private Plugins offer the most capability for utilizing the ATAK subsystem, but 
this interface will likely change from version to version.


build.xml and Makefile both reflect the same project name (in this case helloworld).

The assets file describes both a Lifecycle and a ToolDescriptor.   For convention,
these are in the same location used in the AndroidManifest.xml file.    For 
readability I have broken out the plugin to be in a directory off of the main 
package structure.

When constructing the plugin, it is important to recognize that there are two 
different android.content.Context in play.   

  The plugin context is used to resolve resources from the plugin APK
  The mapView context is used for graphic access (AlertDialogs, Toasts, etc).

Note:
   The plugin context will cause a runtime error to occur if used to construct an
   AlertDialog.









