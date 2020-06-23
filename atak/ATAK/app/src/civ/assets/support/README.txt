ATAK (herein labelled "the app") Files Operator README

This file describes the folder structure for the app and, more
specifically, where files (such as imagery) should reside.

/atak is the top level folder, all folders referenced below are relative to this folder

/DTED

All DTED resides in this directory. The directory structure should look like the following:
/DTED/w117/n34.dt1
/DTED/w117/n34.dt2

Where w117 represents the "westing" of the DTED and "n34.dt*" represents the "northing"
for the DTED. This folder structure is common when exporting DTED out of FalconView or
JMPS Map Data Manager so you shouldn't have to take any additional steps to format the 
directories correctly.

Also note that the app supports DTED Level 0,1,2, and 3. The various levels of DTED can be
co-located with one another such that you have one or more levels for a given geographic
location.

It is important to note that your folder names and file names must follow the westing
and northing naming convention mentioned above.

/grg

GeoTIFF or KMZ GRG images go in this directory. The app will treat GRG's as an overlay such
that reference imagery is visible "underneath" the GRG. Since they are treated as an
overlay, their visibility can be toggled on and off.

This folder is for GRG imagery only. Do not place any other geospatial product in this
directory.


/imagery

Data exported from FalconView or JMPS Map Data Manager goes in this directory.

The following formats are supported:
    GeoJPEG2000
    GeoTIFF
    CIB/CADRG
    ECRG
    KMZ
    MrSID
    NITF
    RPF

For the best performance, it is recommended that you organize your imagery directory such that each dataset
gets its own subdirectory under imagery. Small datasets (less than 5000 chips) will be checked every time
ATAK starts to make sure they are up to date.  Large datasets (more than 5000) chips will not be checked. If
you have a large dataset and it has changed, you can force a refresh by selecting the dataset for import
using the Import Manager.

/imagery

Mobile imagery should be placed in this directory.  Mobile imagery includes:

    SQLite tilesets created with the MOBAC desktop application or downloaded by ATAK or WinTAK.
    When creating a cache with MOBAC use the OSMDroid Sqlite format as the output type.

    Configuration data for connecting to a WMS provider. Several configuration examples have been
    included with the default the app installation. These examples can be modified/removed to
    accommodate any standard WMS provider.

    Geopackage tilesets.  These files end with the extension, gpkg.
    
    Legacy tile caches (ending in .zip) can also be placed in here. These tilesets were created with 
    the app tile generator.


/prefs (internal card only)

NOTE: Preferences must be loaded on internal storage, hence the <internal card> designation in the path.

Within the app, under Settings, General Settings, Save Preferences you can save all of the preferences
for a preconfigured the app.   This file can be loaded by other the app devices so preference items do not 
need to be manually configured for each the app load.    This file can also be renamed to just "defaults" and 
deployed to an the app build in the prefs directory.   In this case, it will be automatically read the next 
time the app is started allowing preferences to be pushed to many devices automatically.   Once the "defaults"
file is loaded, it is removed from the system.

In both cases, this file is read and used to populate internal preferences, which persist until the application data is cleared or a clean install is performed.


APASS

Imagery used with the APASS Android application will be auto-discovered by the app. If you have previously
loaded imagery products into the APASS application then no action is required. The app will
identify APASS imagery data sets and render those products inside of the app.

/support/logs

Diagnostic logs are placed in this directory. If the app crashes a report is generated and saved
in this location. It is advisable to send these log files back to the app team for further
inspection.


Keyhole Markup Language files may be placed in this directory to be imported as a vector
overlay. A subset of KML is supported by the app including Styles, Placemark Point, LineString,
Polygon, MultiGeometry. Specifically unsupported: Placemark LinearRing, MultiTrack, and
IconStyle/Icon/Href (in other words your icons may specify a color but not an image).

/export

Shapes drawn on the app map may be exported to various formats including KML; this is the
directory where these files are created.

/tools/missionpackage

Mission packages which are created, imported, or received over the air are stored in this
directory. Mission Packages may be manually placed in this directory to be imported.


/overlays

Overlay files may be placed in this directory to be imported as vector overlays.
The following formats are supported: DRW, GPX, KML, KMZ, LPT and Shapefile. The color
for Shapefile shapes may be controlled at import time via the settings.
In 3.10+, support has been added for processing and using obj models and other types 
of 3D models from producers such as Pix4D.

/tools/videos

Place recorded videos in this directory for playback.


/tools/jumpmaster

Wind data files (CSV, TXT, CNS) may be placed in this directory and may then be imported
during jump planning. In addition, network wind servers may be configured in this directory.


/Databases/crumbs.sqlite (encrypted as of 3.9+)
This database contains all crumb points (self or other) recorded by the app over the last 30 days. 
The fields of this database can be examined or used via Self Track History tool or by 3rd party tools. The columns for the crumbs table are defined as:

uid,sid, title,timestamp,lat,lon,alt,ce,le,bearing,speed,ptsource,altsource,point_geom 

where

uid - the unique identifier for the map marker.
sid - the track segment id this crumb is associated with
title - callsign or title for the map marker,
timestamp - a recorded timestamp in milliseconds since January 1, 1970, 00:00:00 GMT 
            (normalized to GPS time if enabled in the app).
lat - latitude in degrees
lon - longitude in degrees
alt - altitude in HAE
ce - CE90 for the point 
le - LE90 for the point 
bearing - bearing in degrees
speed - speed in meters / second
ptsource - source for the location point (USER, GPS, etc)
altsource - source for the altitude point (USER, GPS, DTED, etc)
point_geom - a non-human readable spatial geometry for searching purposes.




