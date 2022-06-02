# Version History

## 4.5.0.1

* Bug Fixes
  * Playstore fix crash with allowed null preference key which is now allowed in Android 11 and used when SharedPreference::editor::clear is called.
  * Playstore fix  call the correct super method when sending a TAK client parameters mission package.
  * Common commo correctly recovers in specific low level cases involving open ssl library usage.

## 4.5.0.0

* Feature Additions
  * User Interface updates
    * Action Bar
    * Tools Menu
    * On Screen Map Controls (zoom, self-lock, compass)
    * Radial Menu Look & Feel
  * First person perspective
  * Wide (360) Degree Photo Support
  * Device Information dialog
  * Chat read receipt timestamp
  * Improved Send Image workflow
* Bug Fixes
  * Various issues with certificate self-enrollment
  * Update the expired letsEncrypt root CA
  * Update magnetic model to WMM2020
  * Fix several discrepancies with KML parsing/rendering
  * Fix issue with screen-always-on mode during navigation
* Security
  * Update `libpng`
  * Android 11 requires prompt for file system access

## 4.4.0.15

* Bug Fixes
  * display the angle correctly for Grid  North when showing doghouses.

## 4.4.0.14

* Bug Fixes
  * display the angle correctly when showing doghouses.

## 4.4.0.13

* Bug Fixes
  * Null Pointer when setting the title of a MultiPolyline
  * Toggling doghouse visibility displays doghouses for hidden routes

## 4.4.0.12

* Bug Fix
  * Difficult time changing range and bearing units specifically feet/meters.

## 4.4.0.11

* Bug Fixes
  * address issue with Spot Markers not getting properly renamed when done in succession.
  * prevent a NullPointerException when rendering the label for a Range and Bearing arrow

## 4.4.0.10

* Bug Fix
  * Prevent Null Pointer crash condition when getting the current IP in a 9 line.

## 4.4.0.9

* Bug Fixes
  * address crashes within the Vehicle Model Usage on Android 12

* Feature Additions
  * change WMM2015 with WMM 2020

## 4.4.0.8

* Bug Fix
  * fix tile size computation for equirectangular OSMDroid sqlite map data

## 4.4.0.7

* Bug Fixes
  * do not allow user to pan the map via rotation when the radial is open
  * Newly loaded terrain doesn't update until ATAK is restarted

## 4.4.0.6

* Bug Fixes
  * do not continually call setTitle if the title is already set on MultiPolyline causing a crash
  * ensure that fillStyle cannot be null (Playstore Crash Log)
  * if a preference has a dependency on a hidden preference, then also hide the preference otherwise ATAK will crash
  * fix map scale bar so that it accurately reflects distance

## 4.4.0.5

* Bug Fix
  * For plugins using ImportFileBrowserDialog - fix case where extensions have not been set, assume wildcard '*'

## 4.4.0.4

* Bug Fix
  * correction to allow for Network Monitor to handle detection of 2 PRC 163/167's.

## 4.4.0.3

* Bug Fixes
  * address  hit back button in Import Manager to navigate up to "/storage". Note now no child directories or files listed
  * Update video libraries to 20210812 Addresses issue outlined in when in rare situations during buffered * streaming, video playback could freeze
  * in the case of distance or inclination being NaN, toast and do not continue
  * fix logic for wildcard searches where wildcard character is not first or last
  * protect against the possibility that the MapRenderer3 can be null causing a crash

## 4.4.0.2

* Bug Fixes
  * only allow for text entry without newlines during callsign entry
  * fix tile query issues with imagery capture for GRG Builder and other plugins
  * break out and allow the port to be specified during enrollment, fixing a hard crash
  * toggle the channels checkbox when the user clicks anywhere in the list item
  * vehicle model load failsafe where if the vehicle is 0 length file, it will reload it from the apk
  * fix occasional hard crash when hit testing rubbersheet models

## 4.4.0.1

* Bug Fixes
  * Fix chat receipts showing up as chat messages in older version of ATAK

## 4.4.0.0

* Bug Fixes
  * Renamed CoT 2525B to 2525C in Point Dropper to aligh with actual version of included symbology dataset
  * Removed vehicle outlines from Point Dropper which are also supplied by 2D/3D models (custom user outlines are still available)
  * Fixed issue in Overlay Manager where blank pane would be displayed
  * Various issues reported through Google Play Store distribution
* Feature Additions
  * Map Controls
    * Enhanced Depth Perception
    * Lollipop Visibility
  * DTED0 default elevation source. App will automatically download DTED0 from tak.gov when no elevation data is available. The Elevation Overlays preferences allow the user to configure the server and disable the feature.
  * Mapbox Vector Tiles (MVT) support
  * GeoChat read/unread indicators for chat messages
  * Basic style editing for File Overlays (KML, Shapefile, etc.)
  * 3D Tactical Rectangles
  * Cesium 3D Tiles import from .3tz containers
* Security
  * An extra step is now required on first ATAK start to allow GPS while ATAK is in the background for Android 11
  * Incorporated Google required text to inform user that data is not collected

## 4.3.1.9

* Bug Fix
  * Account for possible `NullPointerException` in `FeatureDetailsView`
  
## 4.3.1.8

* Bug Fix
  * for hit testing for areas in high altitude
  
## 4.3.1.7

* Bug Fixes
  * fixed crash caused synchronized the invalidate labels call since it touches the points field

* Feature Additions
  * enabled KML/KMZ export in plugins by not filtering list items that implement Exportable and support the target class (usability)
  * added support NGA GRiD streaming tile URLS

## 4.3.1.6

* Bug Fix
  * for plugins toggling feature labels

## 4.3.1.5

* Bug Fix
  * for misidentification of PDF files on a remote system

## 4.3.1.4

* Bug Fix
  * for video playback issue detected in Thomas_Fire_Video with regards when projecting to the ground.

## 4.3.1.3

* Bug Fix
  * for crash caused by trying to query map view controls which using them (Playstore Crash Log)

## 4.3.1.2

* Bug Fix
  * for crash when video encoding is being used by plugins such as UAS Tool

## 4.3.1.1

* Bug Fix
  * for crash caused by an Android Toast being used improperly. (Playstore Crash Log)

## 4.3.1.0

* Bug Fixes
  * will avoid leak of local reference in Java/JNI VSI layer's seek implementation
  * corrected issues with strings found during translation to Japanese
  * migrated the name of the directory for the icons from the incorrect mil-std-2525b to mil-std-2525c
  * will resize the text widget if needed when the bloodhound is started
  * migrated over fix for GlArrow2 so that the association arrow is not render sub-terrain when no altitude/elevation is present
  * corrected issue with FAH being clipped incorrectly in areas greater than 90 degree longitude
  * corrected issue with countries using translation for All Chat Room
* Feature Additions
  * can turn off enhanced depth perception mode
  * can modify the styling of features (preliminary capability)
  * migrated to Label Manager

## 4.3.0.4

* Fix various typos
* Implement _latitude_ wrapping for routes
* Fix bounds check for FAH arrow renderer
* Update logic for chat room handling to support translations for well-known chat room names
* Guard against potential NPE for geometry collections

## 4.3.0.3

* Reorder logic for FAH arrow rendering to ensure text validated before drawing

## 4.3.0.2

* Add auto-download DTED0
* Support integer-only representations for fill color for CoT shapes


## 4.3.0.1

* Throw out invalid polyline points
* Fix R&B arrow direction on globe tilt
* Handle potential invalid memory accesses for meshes that may not have textures
* Set a default tessellation threshold (160km) for polyine rendering

## 4.3.0.0

* Lasso tool to create imagery download regions
* Lasso tool to select content for Data Packages
* Automatic label deconfliction/declutter
* Map can be tilted to 90 degrees
* Quick Pic metadata overlay from tool menu
* Display height and/or area of markers and shapes in the coordinate widget when an item is selected
* Android 11 support
  * Allow ATAK to access GPS while in the background
  * Address battery usage
  * Allow access to external SD cards
* Improved KML/KMZ imports
  * Improve handling of KML network links, including nested network links
  * KMZs containing both image overlays and placemarks/shapes only need to be imported once to ingest all contents
  
## 4.2.1.10

* Better KML consistency with Google Earth for line style

## 4.2.1.9

* Fix off by one error in `Dt2File`

## 4.2.1.8

* Implement custom removable storage handling for Android 11
* Add permission to publish GPS location in background on Android 11

## 4.2.1.7

* Adjust DPI for GeoPDF to make maps more readable
* Fix bug with KML label scale interpretation

## 4.2.1.6

* Update `MissionPackageReceiver` to not trigger on files during directory shuffle on upgrade
* Update bounds for drawing rectangle on points change


## 4.2.1.5

* Fix logic error when checking for database credentials

## 4.2.1.4

* Add guard against spurious exception in mesh rendering causing crash
* Update lasso area prompt text

## 4.2.1.3

* Fix KML rendering style issues with <GroundOverlay> elements

## 4.2.1.2

* Fix bug in `GLBatchPoint` with bad matrix pop. This issue would most commonly manifest with the 3D model indicator on screen.

## 4.2.1.1

* Fix reported issue with BP/HA placement
* Align BP/HA dimension input with USMC doctrine
* Update Mission Package Manager to store certs with connection vs defaults

## 4.2.1.0

* Add lasso tool to support region based imagery download
* Add support for KML icon scaling
* Sort icons by distance from camera to mitigate artifacts when map is tilted
* Reset expiration on certificate when credentials are re-entered
* Pull expiration from certificate if it's specified
* Reimplement KML parsing for network link import
* Fix GDAL band request parameters for monochrome imagery with alpha
* Add additional vehicle models
* Show nested child KML network links in Overlay Manager
* Allow user to modify child KML network link download intervals
* Handle KML altitude mode specified as `gx:altitudeMode` element
* Add support for open polyline extrusions
* Add extrusion support for negative heights (extrude down from base)
* Various bug fixes

## 4.2.0.5

* Fix bug in `CommsMapComponent.removeOutputsChangedListener(...)`
* Fix attach imave/video to marker from Downloads or Photos app

## 4.2.0.4

* Improve handling for KML PolyStyle with <fill>0</fill>

## 4.2.0.3

* Address various cases where streams may not be closed if exceptions are raised
* Fix bug in 3D Tiles for bad bounding volume compute on Box or Sphere bounding volumes

## 4.2.0.2

* Address potential crash when receiving CoT shape with invalid points
* Address potential crash when importing DTED without a file extension

## 4.2.0.1

* Fix crash for 32-bit hosts when trying to upload Data Package to TAK Server
* Fix JNI local reference leak
* Potential `NullPointerException` in `AppMgmtUtils`
* Refactor logic in `MyPreferenceFragment` to avoid potential `NullPointerException`

## 4.2.0.0

* ATAK CIV plugins are compatible with all variants of ATAK.
* 3D Billboard Feature.  Marker image attachments can appear as 3D Billboards when navigating routes.
* Point Dropper - 3D Vehicle Icons improvement. Higher contrast outlines are rendered around the icons in the point dropper selector.
* Edit Route Checkpoint Elevation. Elevation of individual route checkpoints may now be edited.
* Toggle Sensor Field of View (FOV). The sensor FOV can now be toggled from the sensor radial menu.
* Additional Metrics Collection. Information on screen touches is now included in metrics logging, when enabled.
* Added DTED Data Manager.
* Added GeoPDF support.
* Upgrade to GDAL 2.4.4.
* Various bug fixes

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
