from conans import ConanFile

class TTPDistConan(ConanFile):
    name = "tinygltfloader-tak"
    description = "TinyGLTFLoader header-only parsing library"
    version = "0.9.5"

    def package(self):
        self.copy("stb_image.h", dst="include/tinygltfloader", src=".")
        self.copy("json.hpp", dst="include/tinygltfloader", src=".")
        self.copy("tiny_gltf_loader.h", dst="include/tinygltfloader", src=".")
        self.copy("README.md", dst=".", src=".")
