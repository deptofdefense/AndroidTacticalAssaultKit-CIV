from conans import ConanFile

class TTPDistConan(ConanFile):
    name = "tinygltf-tak"
    description = "TinyGLTF header-only library"
    version = "2.4.1"
    license = "MIT"

    def package(self):
        self.copy("json.hpp", dst="include/tinygltf", src=".")
        self.copy("stb_image.h", dst="include/tinygltf", src=".")
        self.copy("stb_image_write.h", dst="include/tinygltf", src=".")
        self.copy("tiny_gltf.h", dst="include/tinygltf", src=".")
        self.copy("README.md", dst=".", src=".")
        self.copy("LICENSE", dst=".", src=".")
