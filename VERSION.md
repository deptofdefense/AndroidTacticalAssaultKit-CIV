# Version History

## 4.1.1.17

* Handle potential ClassCastException when a shared preference is updated while the user is in the IsrvNetworkPreferenceFragment

## 4.1.1.16

* Handle potential WindowManager$BadTokenException in TLSUtils
* Handle potential NullPointerException in geofence
* Reverse intepretation of Ellipse azimuth property when recomputing the geometry
* Handle potential NullPointerException in AddNetInfoActivity

## 4.1.1.15

* Perform a double reflection to bypass google security for blacklisted reflection
* Handle potential NullPointerException in geofence
* Handle case where system display settings activity may not exist
* Do not delete all persisted actionbar layouts during a save, only delete the actionbar layout that has been deleted.
* Handle potential BadTokenException in TileButtonDialog

## 4.1.1.14

* Relax undocumented check to mitigate potential NullPointerException

## 4.1.1.12

* Potential SecurityException on file import
* Potential NullPointerException in ContactLocationView

## 4.1.1.11

* Potential NullPointerException in bloodhound
* Fix formatting issue with selected_exports_export_or_send

## 4.1.1.10

* Potential ClassCastException in LayerManagerBroadcastReceiver
* Potential NullPointerException in CotMapServerListener
* Add read limits during DAE probing

## 4.1.1.9

* Handle potential `IllegalStateException` in `onResume`

## 4.1.1.8

* Handle potential `NullPointerException` on geofence dismiss

## 4.1.1.7

* fix KML export for content with more than one item with `null` name
* fix potential `NullPointerException` in contacts

## 4.1.1.6

* Handle potential Security Exception on file import

## 4.1.1.5

* Handle potential `NullPointerException` in Route Navigation

## 4.1.1.4

* Android 11 (API Level 30) compatibility; add `PluginSpinner.isUIContext`

## 4.1.1.3

* Handle potential `NullPointerException` in `AtakMapView` in race at exit

## 4.1.1.2

* Handle potential crash when trying to import empty file

## 4.1.1.1

* Handle potential crash with grid lines overlay
* Layout fixes for Danger Close dialog

## 4.1.1.0

* Video library update
* Video list dialog can be toggled to show URLs
* Add Edit Mode for Vehicle Models
* Add deprecation throughout codebase with target version for removal
* Add retry attempts for failed tile downloads during bulk map download
* Source code updates to make Java more portable
* Integrate filesystem abstraction layer
* Various bug fixes
* New gradle plugin to support ATAK plugin development

## 4.1.0.1

* Adjust Android SDK to use named SDK constants instead of raw numbers
* Add a Developer Option to skip the drawing of route vertex points to work around crash observed on some custom hardware
* Fix potential `NullPointerException` on Android 6 in `RemarksLayout`

## 4.1.0.0

* Bloodhound integration with route planners
* Data Packages sent via HTTPS by default
* 3D Vehicle Icons
* 3D Route Visualization
* Migrate to Android 29 (minimum version Android 21)
* Migrate to AndroidX
* New dynamic radial menu API
* Add SRT support to video libraries
* Fixed issue where files on an external SD card are not loaded when ATAK is run for the first time.
* Received SPI now can be color coded from the radial menu.
* Various performance improvements.
* Tested and verified working against Android 10.
* Support for SRT video within ATAK
* Support for recording of RTSP streams.
* Updated all bundled applications to be compliant with Android 29.
* Updated allowable units of measure for user created shapes.
* Introduced translation for French speaking users. The French translation is enabled by setting the device’s language to French and launching ATAK. Figure 53 shows the ATAK additional tools menu in French.

## 4.0.0.7

* Set the GPL license as the EULA on application start
* SDK does not perform plugin signing key whitelist check
* Add SDK build type to build scripts

## 4.0.0.6

* Initial commit of ATAK-CIV (GPLv3)
