# Create a shared library from our GLESv2-JOGL source, and include module definition file.
add_library(libGLESv2
    SHARED GLES3/gl32.c
           GLFunctionTable.c
           ../../vc/libGLESv2.def)

# Set target properties, where all VS properties are based on existing VS project properties found in GLESv2-JOGL.vcxproj.
set_target_properties(libGLESv2
    PROPERTIES VS_GLOBAL_ROOTNAMESPACE GLESv2JOGL
               VS_KEYWORD Win32Proj)

# Include the khronos directories that contain required headers.
target_include_directories(libGLESv2
    PRIVATE .
            ../../khronos/EGL/api
            ../../khronos/OpenGL/api)

# Add include and link dependency directories pointing to the JNI libraries.
target_include_directories(libGLESv2
    PRIVATE ${JNI_INCLUDE_DIRS})

target_link_libraries(libGLESv2
    PRIVATE ${JNI_LIBRARIES})

# If targeting a Windows machine, export the GL function prototypes so that they're visible to dependent projects, and
# set the calling convention of those prototypes to stdcall (a.k.a. WINAPI). Otherwise, explicitly set the visibility
# of the function prototypes so that they're exposed through the compiled .so or .dylib (when targeting Linux or MacOS).
if(WIN32)
    target_compile_definitions(libGLESv2
        PRIVATE GL_APICALL=__declspec\(dllexport\)  # Parentheses need to be escaped
            	GL_APIENTRY=__stdcall)
else()
    target_compile_definitions(libGLESv2
	    PRIVATE GL_APICALL=__attribute__\(\(visibility\("default"\)\)\))  # Parentheses need to be escaped
endif()

# Define _DEBUG if building the Debug configuration, otherwise define NDEBUG.
target_compile_definitions(libGLESv2
    PRIVATE $<IF:$<CONFIG:Debug>,_DEBUG,_NDEBUG>
            _CONSOLE)
