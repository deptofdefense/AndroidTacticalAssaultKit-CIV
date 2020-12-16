## Prerequisites

  - dos2unix
  - autoconf
  - automake
  - libtool
  - GNU patch
  - GNU make (non GNU likely will not work)
  - tclsh 8.x (needed for sqlite build on all platforms; if building **on**
    Windows, see Windows section for more specific instructions)
  - cmake (needed for assimp; if building **on** Windows, see Windows section for specific versions)
  - swig (needed for gdal; if building **for** Windows, see Windows section for specific versions)
  - **Building on Windows:**
      - cmake **x86** version
        (https://cmake.org/files/v3.11/cmake-3.11.1-win32-x86.msi has
        been used in the past)
      - cygwin
          - In addition to above tools, also need cygwin's binutils
          - Might need gcc from cygwin? Chris L. reported issues with
            iconv build without it, but Jeff can build fine without it?
  - **Building Win32 target:**
      - MS Visual Studio 2015 Professional
      - SWIG 1.3.31
        (http://sourceforge.net/projects/swig/files/swigwin/swigwin-1.3.31/)
      - ActiveState TCL **Version 8.5.x** - do not get 8.6.x versions
        they are known not to work. Install to default c:\\tcl path.
        **It is not necessary or recommended to add the TCL bin
        directory to your system path**.
        (https://www.activestate.com/activetcl -- use Community Edition;
        8.5.18.0 x86 version is known to work
        <http://downloads.activestate.com/ActiveTcl/releases/8.5.18.0/ActiveTcl8.5.18.0.298892-win32-ix86-threaded.exe>)
      - [Windows Power
        Shell 3.0](https://technet.microsoft.com/en-us/library/hh847837.aspx#BKMK_InstallingOnWindows7andWindowsServer2008R2)
  - **Building the Android target:**
      - Java JDK **version 8 - do not use newer versions** - set JAVA\_HOME to point to it
      - Android NDK - set ANDROID\_NDK to point to it
      - Apache ant - must be in PATH

## System Setup

### Windows

  - Install cygwin
      - Be sure to install all above pre-requisites during setup

### MacOS

  - Install XCode (tested with 6.1)
  - Install XCode Command Line Tools (google it)
  - Run XCode to accept license stuff
  - Recommended: Install MacPorts to get dos2unix and apache ant

### Linux

  - Install packages to get the above pre-requisites.

## Using TAK Thirdparty Build System

  - cd takthirdparty
  - Simply type *make* to get some generic help and list the valid
    targets
  - To compile all packages, specify the target system and a target of
    "build":
    > *make TARGET=android-armeabi-v7a build*
  - To compile all packages used by gdal and gdal itself, specify the
    target system and a target of "build\_gdal":
    > *make TARGET=android-armeabi-v7a build\_gdal*
  - To compile all packages used by gdal and gdal itself **without the PAR-only KDU library**, additionally specify GDAL_USE_KDU=no:
    > *make TARGET=android-armeabi-v7a GDAL\_USE\_KDU=no build\_gdal*
  - To compile all packages used by commoncommo and commoncommo itself,
    specify the target system and a target of "build\_commoncommo":
    > *make TARGET=android-armeabi-v7a build\_commoncommo*
  - To compile all packages used by SpatiaLite and SpatiaLite itself,
    specify the target system and a target of "build\_spatialite":
    > *make TARGET=android-armeabi-v7a build\_spatialite*
  - When targeting Win32, build\_commoncommo intentionally does not
    build commoncommo itself; rather it builds only its dependencies.
    Use Visual Studio to build commo itself.
  - By default, a "release" quality build is performed. To build a debug
    version, specify BUILD\_TYPE as debug:
    > *make TARGET=android-armeabi-v7a BUILD\_TYPE=debug*
  - To clean, use the "clean" target. This cleans each package for that
    target type and BUILD\_TYPE:
    > *make TARGET=android-armeabi-v7a clean*
  - To entirely remove everything and force full reconfiguration,
    specify the "veryclean" target:
    > *make TARGET=android-armeabi-v7a veryclean*
  - For developers, you can build individual packages. This is meant
    primarily to be used during development. **NOTE:** when building
    specific packages, dependent packages are not built; this is a
    shortcut to directly build the specific package mentioned. Example:
    > *make TARGET=android-armeabi-v7a curl*
  - You can clean specific packages with pkgname\_clean:
    > *make TARGET=android-armeabi-v7a curl\_clean*
  - The package names are the names of the files in the 'mk' directory.

## Notes

  - Sources for most packages are sourced from tar files in the
    distfiles directory.
  - gdal, kakadu, and a few others are sourced from their locations in
    version control. Whatever version of those packages that is
    checked out will be what is used for the build. **The build system
    does not pull anything from version control itself.**

### MrSID DSDK

The distribution does not include the MrSID DSDK, but will use it if
provided by the user. The DSDK package may be constructed as follows:  

1. Download DSDK for Android (known to work with 9.5.4.9709)
2. Unpack the distribution
3. Execute the following commands from the root of unpack location
```
cd Raster_DSDK
tar cf android.tar *
gzip -9 android.tar
```
4. Copy the `android.tar.gz` file to `takthirdparty/distfiles/mrsid/`  
  
The package will be automatically detected and MrSID support added to
the GDAL build.