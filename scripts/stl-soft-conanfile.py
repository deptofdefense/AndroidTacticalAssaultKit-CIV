from conans import ConanFile

class STLSoftConan(ConanFile):
    name = "stl-soft"
    version = "1.9.119"
    # No settings/options are necessary, this is header only
    no_copy_source = True

    def package(self):
        self.copy("*")
