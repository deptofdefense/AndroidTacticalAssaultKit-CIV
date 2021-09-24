#include "common.h"
#include "jglninepatch.h"

#include <cmath>

/*
 * Class:     com_atakmap_opengl_GLNinePatch
 * Method:    buildPatchVerts
 * Signature: (Ljava/nio/FloatBuffer;IFDFFFF)V
 */
JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLNinePatch_buildPatchVerts
  (JNIEnv *env, jclass clazz,
   jobject jverts,
   jint numVertsPerCorner,
   jfloat radius,
   jdouble radiansPerVert,
   jfloat x,
   jfloat y,
   jfloat width,
   jfloat height)
{
    jfloat *verts = GET_BUFFER_POINTER(jfloat, jverts);

    jfloat *pVerts = verts;
    int idx = 0;

    const int limit = (numVertsPerCorner*4);
    float tx = 0.0f;
    float ty = 0.0f;
    for(int i = 0; i < limit; i++) {
        if((i%numVertsPerCorner) == 0) {
            switch(i/numVertsPerCorner) {
                case 0 :
                    tx = width-(radius*1.0f);
                    ty = height-(radius*1.0f);
                    break;
                case 1 :
                    tx = radius;
                    ty = height-(radius*1.0f);
                    break;
                case 2 :
                    tx = radius;
                    ty = radius;
                    break;
                case 3 :
                    tx = width-(radius*1.0f);
                    ty = radius;
                    break;
                default :
                    // XXX - illegal state
                    break;
            }
        }
        (*pVerts++) = x + tx + (radius*(float)cos(radiansPerVert*i));
        (*pVerts++) = y + ty + (radius*(float)sin(radiansPerVert*i));
    }

    (*pVerts++) = verts[0];
    (*pVerts++) = verts[1];
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLNinePatch_buildPatchVerts3d
    (JNIEnv *env, jclass clazz,
     jobject jverts,
     jint numVertsPerCorner,
     jfloat radius,
     jdouble radiansPerVert,
     jfloat x,
     jfloat y,
     jfloat z,
     jfloat width,
     jfloat height)
{
  jfloat *verts = GET_BUFFER_POINTER(jfloat, jverts);

  jfloat *pVerts = verts;
  int idx = 0;

  const int limit = (numVertsPerCorner*4);
  float tx = 0.0f;
  float ty = 0.0f;
  for(int i = 0; i < limit; i++) {
      if((i%numVertsPerCorner) == 0) {
          switch(i/numVertsPerCorner) {
              case 0 :
                  tx = width-(radius*1.0f);
                  ty = height-(radius*1.0f);
                  break;
              case 1 :
                  tx = radius;
                  ty = height-(radius*1.0f);
                  break;
              case 2 :
                  tx = radius;
                  ty = radius;
                  break;
              case 3 :
                  tx = width-(radius*1.0f);
                  ty = radius;
                  break;
              default :
                  // XXX - illegal state
                  break;
          }
      }
      (*pVerts++) = x + tx + (radius*(float)cos(radiansPerVert*i));
      (*pVerts++) = y + ty + (radius*(float)sin(radiansPerVert*i));
      (*pVerts++) = z;
  }

  (*pVerts++) = verts[0];
  (*pVerts++) = verts[1];
  (*pVerts++) = verts[2];
}

/*
 * Class:     com_atakmap_opengl_GLNinePatch
 * Method:    fillCoordsBuffer
 * Signature: (FFFFFFFFLjava/nio/FloatBuffer;)V
 */
JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLNinePatch_fillCoordsBuffer
  (JNIEnv *env, jclass clazz,
   jfloat texPatchCol0, jfloat texPatchRow0,
   jfloat texPatchCol1, jfloat texPatchRow1,
   jfloat texPatchCol2, jfloat texPatchRow2,
   jfloat texPatchCol3, jfloat texPatchRow3,
    jobject jcoordBuffer)
{
    jfloat *coordBuffer = GET_BUFFER_POINTER(jfloat, jcoordBuffer);

    jfloat texPatchRows[4];
    jfloat texPatchCols[4];

    texPatchRows[0] = texPatchRow0;
    texPatchRows[1] = texPatchRow1;
    texPatchRows[2] = texPatchRow2;
    texPatchRows[3] = texPatchRow3;

    texPatchCols[0] = texPatchCol0;
    texPatchCols[1] = texPatchCol1;
    texPatchCols[2] = texPatchCol2;
    texPatchCols[3] = texPatchCol3;

    jfloat *pCoordBuffer = coordBuffer;

    for(int i = 0; i < 4; i++) {
        for(int j = 0; j < 4; j++) {
            *pCoordBuffer++ = texPatchCols[j];
            *pCoordBuffer++ = texPatchRows[i];
        }
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_opengl_GLNinePatch_fillCoordsBuffer3d
    (JNIEnv *env, jclass clazz,
     jfloat texPatchCol0, jfloat texPatchRow0,
     jfloat texPatchCol1, jfloat texPatchRow1,
     jfloat texPatchCol2, jfloat texPatchRow2,
     jfloat texPatchCol3, jfloat texPatchRow3,
     jfloat z,
     jobject jcoordBuffer)
{
    jfloat *coordBuffer = GET_BUFFER_POINTER(jfloat, jcoordBuffer);

    jfloat texPatchRows[4];
    jfloat texPatchCols[4];

    texPatchRows[0] = texPatchRow0;
    texPatchRows[1] = texPatchRow1;
    texPatchRows[2] = texPatchRow2;
    texPatchRows[3] = texPatchRow3;

    texPatchCols[0] = texPatchCol0;
    texPatchCols[1] = texPatchCol1;
    texPatchCols[2] = texPatchCol2;
    texPatchCols[3] = texPatchCol3;

    jfloat *pCoordBuffer = coordBuffer;

    for(int i = 0; i < 4; i++) {
        for(int j = 0; j < 4; j++) {
            *pCoordBuffer++ = texPatchCols[j];
            *pCoordBuffer++ = texPatchRows[i];
            *pCoordBuffer++ = z;
        }
    }
}
