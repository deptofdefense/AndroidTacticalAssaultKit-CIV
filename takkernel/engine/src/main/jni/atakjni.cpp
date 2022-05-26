#include "atakjni.h"
#include "common.h"
#include <cstring>
#include <cmath>

#include <core/MapSceneModel2.h>

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_opengl_GLBatchGeometryRenderer_fillVertexArrays
  (JNIEnv *env, jclass clazz, jlong scenePtr, jint vertSize, jobject jtranslations, jobject jtexAtlasIndices, jint iconSize, jint textureSize, jobject jvertsTexCoords, jint count)
{
    const MapSceneModel2 *cscene = JLONG_TO_INTPTR(MapSceneModel2, scenePtr);

    jfloat *translations = GET_BUFFER_POINTER(jfloat, jtranslations);
    jint *texAtlasIndices = GET_BUFFER_POINTER(jint, jtexAtlasIndices);
    jfloat *vertsTexCoords = GET_BUFFER_POINTER(jfloat, jvertsTexCoords);

    jfloat *pVertsTexCoords = vertsTexCoords;
    float tx;
    float ty;

    float vertices[12];
    vertices[0] = -iconSize/2;  // upper-left
    vertices[1] = -iconSize/2;
    vertices[2] = iconSize/2;   // upper-right
    vertices[3] = -iconSize/2;
    vertices[4] = -iconSize/2;  // lower-left
    vertices[5] = iconSize/2;
    vertices[6] = iconSize/2;   // upper-right
    vertices[7] = -iconSize/2;
    vertices[8] = -iconSize/2;  // lower-left
    vertices[9] = iconSize/2;
    vertices[10] = iconSize/2;  // lower-right
    vertices[11] = iconSize/2;

    float iconX;
    float iconY;

    float fIconSize = iconSize-1;
    float fTextureSize = textureSize;

    int numIconsX = textureSize / iconSize;
    int iconIndex;

    if(vertSize == 2) {
    	const std::size_t vertStride = (vertSize+1u);
        for(int i = 0; i < count; i++) {
            tx = translations[i*vertStride];
            ty = translations[i*vertStride+1];
            const float scale = translations[i*vertStride+2];
            Point2<double> xyz;
            cscene->forward(&xyz, GeoPoint2(ty, tx));
            tx = (float)xyz.x;
            ty = (float)xyz.y;


            iconIndex = texAtlasIndices[i];

            iconX = (iconIndex % numIconsX) * iconSize;
			iconY = (iconIndex / numIconsX) * iconSize;

			(*pVertsTexCoords++) = vertices[0]*scale+tx;
			(*pVertsTexCoords++) = vertices[1]*scale+ty;
			(*pVertsTexCoords++) = iconX / fTextureSize;                 // upper-left
			(*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
			(*pVertsTexCoords++) = vertices[2]*scale+tx;
			(*pVertsTexCoords++) = vertices[3]*scale+ty;
			(*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // upper-right
			(*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
			(*pVertsTexCoords++) = vertices[4]*scale+tx;
			(*pVertsTexCoords++) = vertices[5]*scale+ty;
			(*pVertsTexCoords++) = iconX / fTextureSize;                 // lower-left
			(*pVertsTexCoords++) = iconY / fTextureSize;
			(*pVertsTexCoords++) = vertices[6]*scale+tx;
			(*pVertsTexCoords++) = vertices[7]*scale+ty;
			(*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // upper-right
			(*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
			(*pVertsTexCoords++) = vertices[8]*scale+tx;
			(*pVertsTexCoords++) = vertices[9]*scale+ty;
			(*pVertsTexCoords++) = iconX / fTextureSize;                 // lower-left
			(*pVertsTexCoords++) = iconY / fTextureSize;
			(*pVertsTexCoords++) = vertices[10]*scale+tx;
			(*pVertsTexCoords++) = vertices[11]*scale+ty;
			(*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // lower-right
			(*pVertsTexCoords++) = iconY / fTextureSize;
		}
    } else if(vertSize == 3) {
    	float tz;

    	const std::size_t vertStride = (vertSize+1u);
        for(int i = 0; i < count; i++) {
            tx = translations[i*vertStride];
            ty = translations[i*vertStride+1];
            tz = translations[i*vertStride+2];
            const float scale = translations[i*vertStride+3];
            Point2<double> xyz;
            cscene->forward(&xyz, GeoPoint2(ty, tx, tz, AltitudeReference::HAE));
            tx = (float)xyz.x;
            ty = (float)xyz.y;
            tz = (float)xyz.z;

            iconIndex = texAtlasIndices[i];

            iconX = (iconIndex % numIconsX) * iconSize;
			iconY = (iconIndex / numIconsX) * iconSize;

			(*pVertsTexCoords++) = vertices[0]*scale+tx;
			(*pVertsTexCoords++) = vertices[1]*scale+ty;
			(*pVertsTexCoords++) = tz;
			(*pVertsTexCoords++) = iconX / fTextureSize;                 // upper-left
			(*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
			(*pVertsTexCoords++) = vertices[2]*scale+tx;
			(*pVertsTexCoords++) = vertices[3]*scale+ty;
			(*pVertsTexCoords++) = tz;
			(*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // upper-right
			(*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
			(*pVertsTexCoords++) = vertices[4]*scale+tx;
			(*pVertsTexCoords++) = vertices[5]*scale+ty;
			(*pVertsTexCoords++) = tz;
			(*pVertsTexCoords++) = iconX / fTextureSize;                 // lower-left
			(*pVertsTexCoords++) = iconY / fTextureSize;
			(*pVertsTexCoords++) = vertices[6]*scale+tx;
			(*pVertsTexCoords++) = vertices[7]*scale+ty;
			(*pVertsTexCoords++) = tz;
			(*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // upper-right
			(*pVertsTexCoords++) = (iconY + fIconSize) / fTextureSize;
			(*pVertsTexCoords++) = vertices[8]*scale+tx;
			(*pVertsTexCoords++) = vertices[9]*scale+ty;
			(*pVertsTexCoords++) = tz;
			(*pVertsTexCoords++) = iconX / fTextureSize;                 // lower-left
			(*pVertsTexCoords++) = iconY / fTextureSize;
			(*pVertsTexCoords++) = vertices[10]*scale+tx;
			(*pVertsTexCoords++) = vertices[11]*scale+ty;
			(*pVertsTexCoords++) = tz;
			(*pVertsTexCoords++) = (iconX + fIconSize) / fTextureSize;   // lower-right
			(*pVertsTexCoords++) = iconY / fTextureSize;
		}
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_geometry_opengl_GLBatchGeometryRenderer_expandLineStringToLines
  (JNIEnv *env, jclass clazz, jint size, jlong jvertsPtr, jint vertsOff, jlong jlinesPtr, jint linesOff, jint count)
{
    jfloat *verts = JLONG_TO_INTPTR(jfloat, jvertsPtr);
    jfloat *lines = JLONG_TO_INTPTR(jfloat, jlinesPtr);

    jfloat *pVerts = verts+vertsOff;
    jfloat *pLines = lines+linesOff;
    const int segElems = (2*size);
    const int cpySize = sizeof(jfloat)*segElems;
    for(int i = 0; i < count - 1; i++) {
    	memcpy(pLines, pVerts, cpySize);
    	pLines += segElems;
    	pVerts += size;
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLText_bufferChar
  (JNIEnv *env, jclass clazz, jint size, jlong texVertsPtr, jlong texCoordsPtr, jlong texIndicesPtr, jint n, jfloat x0, jfloat y0, jfloat x1, jfloat y1, jfloat z, jfloat u0, jfloat v0, jfloat u1, jfloat v1)
{
    jfloat *texVerts = JLONG_TO_INTPTR(jfloat, texVertsPtr);
    jfloat *texCoords = JLONG_TO_INTPTR(jfloat, texCoordsPtr);
    jshort *texIndices = JLONG_TO_INTPTR(jshort, texIndicesPtr);

    if(size == 2) {
        texVerts[0] = x0; // UL
        texVerts[1] = y0;
        texVerts[2] = x1; // UR
        texVerts[3] = y0;
        texVerts[4] = x1; // LR
        texVerts[5] = y1;
        texVerts[6] = x0; // LL
        texVerts[7] = y1;
    } else {
        texVerts[0] = x0; // UL
        texVerts[1] = y0;
        texVerts[2] = z;
        texVerts[3] = x1; // UR
        texVerts[4] = y0;
        texVerts[5] = z;
        texVerts[6] = x1; // LR
        texVerts[7] = y1;
        texVerts[8] = z;
        texVerts[9] = x0; // LL
        texVerts[10] = y1;
        texVerts[11] = z;
    }

    texCoords[0] = u0; // UL
    texCoords[1] = v0;
    texCoords[2] = u1; // UR
    texCoords[3] = v0;
    texCoords[4] = u1; // LR
    texCoords[5] = v1;
    texCoords[6] = u0; // LL
    texCoords[7] = v1;

    texIndices[0] = n*4+3; // LL
    texIndices[1] = n*4;   // UL
    texIndices[2] = n*4+2; // LR

    texIndices[3] = n*4;   // UL
    texIndices[4] = n*4+2; // LR
    texIndices[5] = n*4+1; // UR
}
