from conans import ConanFile
import os

class KhronosConan(ConanFile):
    name = "khronos"
    version = "1.0"
    # No settings/options are necessary, this is header only
    no_copy_source = True

    def package(self):
        self.copy("*")
        
    def package_info(self):
        self.cpp_info.includedirs = ["EGL/api/","OpenGL/api/"]