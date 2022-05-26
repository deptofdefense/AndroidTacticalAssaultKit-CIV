#include "jglquadtilenode2.h"

#include <math/Matrix2.h>
#include <renderer/GL.h>

#include "common.h"

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_raster_tilereader_opengl_GLQuadTileNode2_setLCS
  (JNIEnv *env, jclass clazz, jlong mvpPtr, jint uMvp, jdouble tx, jdouble ty, jdouble tz)
{
    if(!mvpPtr) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return;
    }
    Matrix2 mvp(*JLONG_TO_INTPTR(Matrix2, mvpPtr));
    mvp.translate(tx, ty, tz);
    float mx[16];
    for(std::size_t i = 0u; i < 16u; i++) {
        double v;
        mvp.get(&v, i%4u, i/4u);
        mx[i] = static_cast<float>(v);
    }
    glUniformMatrix4fv(uMvp, 1u, GL_FALSE, mx);
}
