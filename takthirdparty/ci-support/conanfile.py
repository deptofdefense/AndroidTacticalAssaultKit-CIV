from conans import ConanFile, AutoToolsBuildEnvironment, tools
from conans.tools import load
import re

class TTPDistConan(ConanFile):
    name = "ttp-dist"
    description = "TAK Third Party libraries"
    settings = "os", "compiler", "build_type", "arch"

    CONST_WINDOWS_OS = "Windows"
    CONST_x64_ARCH = "x86_64"

    CONST_WIN_64_COMPILE_LIBS = ["assimp.lib",
                                 "gdal_i.lib",
                                 "geos_c_i.lib",
                                 "geos_i.lib",
                                 "iconv.dll.lib",
                                 "libcrypto.lib",
                                 "libcurl.exp",
                                 "libcurl.lib",
                                 "libexpat.lib",
                                 "libkmlbase.lib",
                                 "libkmlconvenience.lib",
                                 "libkmldom.lib",
                                 "libkmlengine.lib",
                                 "libkmlregionator.lib",
                                 "libkmlxsd.lib",
                                 "libssl.lib",
                                 "libxml2.lib",
                                 "minizip_static.lib",
                                 "ogdi.lib",
                                 "proj_i.lib",
                                 "spatialite.lib",
                                 "spatialite_i.lib",
                                 "sqlite3_i.lib",
                                 "uriparser.lib",
                                 "zlibwapi.lib"]

    def set_version(self):
        content = load("gradle/versions.gradle")
        version = re.search(r"ttpDistVersion = '(.*)'", content).group(1)
        self.version = version.strip()

    def package(self):
        source = self._get_source()

        print(source)

        self.copy("*", dst=source + "/include", src="../" + source + "/include")
        self.copy("*", dst=source + "/bin", src="../" + source + "/bin")
        self.copy("*", dst=source + "/debuglib", src="../" + source + "/debuglib")

        # Ensure that we only package Win64 compile-time libraries that are needed, as opposed to everything under /lib
        if self.settings.os == self.CONST_WINDOWS_OS and self.settings.arch == self.CONST_x64_ARCH:
            for lib in self.CONST_WIN_64_COMPILE_LIBS:
                self.copy(f"{lib}", dst=source + "/lib", src="../" + source + "/lib")
        # If we're not packaging ttp-dist for Win64, then simply copy everything from /lib
        else:
            self.copy("*", dst=source + "/lib", src="../" + source + "/lib")

    def package_info(self):
        source = self._get_source()
        self.cpp_info.libdirs = [source + "/lib"]
        self.cpp_info.includedirs = [source+"/include",source+"/include/libkml",source+"/include/libkml/boost_1_34_1",source+"/include/kml/third_party/boost_1_34_1",
            source+"/include/libxml2/",source+"/include/libkml/zlib-1.2.3/contrib",source+"/include/kml/third_party/zlib-1.2.3/contrib"]

    def _get_source(self):
        source = ""
        if self.settings.os == self.CONST_WINDOWS_OS:
            source += "win"
            if self.settings.arch == self.CONST_x64_ARCH:
                source += "64"
            else:
                source += "32"
        elif self.settings.os == "Android":
            source += "android-"
            if self.settings.arch == "armv8":
                 source += "arm64-v8a"
            elif self.settings.arch == "armv7":
                 source += "armeabi-v7a"
            elif self.settings.arch == "x86":
                 source += "x86"
            else:
                raise Exception("Architecture not recognized")
        elif self.settings.os == "Macos":
            source = "macos-64"
        elif self.settings.os == "Linux":
            source = "linux-amd64"
        source += "-" + str(self.settings.build_type).lower()
        return source
