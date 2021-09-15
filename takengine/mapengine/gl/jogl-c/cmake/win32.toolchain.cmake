# Toolchain file for 32-bit Windows operating systems. Using this toolchain assumes that the developer is generating a
# Visual Studio 2019 multi-configuration build.

# Set target platform and architecture to Windows x86.
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86)
set(CMAKE_GENERATOR_PLATFORM Win32)
set(CMAKE_GENERATOR_TOOLSET host=x86)

# Add WIN32 preprocessor definition, as seen in the VS project file this CMake project is sourced from (GLESv2-JOGL.vcxproj).
add_compile_definitions(WIN32)