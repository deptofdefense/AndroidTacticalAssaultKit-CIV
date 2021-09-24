#ifndef TAK_ENGINE_RENDERER_GL_H_INCLUDED

// if the GLES version is not defined, default to v3
#ifndef TE_GLES_VERSION
#define TE_GLES_VERSION 3
#endif

#if TE_GLES_VERSION == 2
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#elif TE_GLES_VERSION == 3
#ifdef _MSC_VER
#define GL_GLEXT_PROTOTYPES
#endif
#include <GLES3/gl3.h>
#include <GLES2/gl2ext.h>
#else
unsupported_gles_version
#endif

#ifdef __APPLE_OSX__
#define TE_GLSL_VERSION_300_ES "#version 330\n"
#else
#define TE_GLSL_VERSION_300_ES "#version 300 es\n"
#endif

#endif
