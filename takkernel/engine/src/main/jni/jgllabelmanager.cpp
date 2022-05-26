#include "jgllabelmanager.h"

#include <vector>

#include <cmath>
#include <math/Point2.h>
#include <math/Rectangle.h>

#include <thread/RWMutex.h>

#include <renderer/core/GLMapRenderGlobals.h>
#include <renderer/core/GLLabelManager.h>
#include <renderer/core/GLMapView2.h>

#include "common.h"
#include "interop/Pointer.h"
#include "interop/JNIDoubleArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/math/Interop.h"

#define TAG "TEST_DEBUG"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Feature;

using namespace TAKEngineJNI::Interop;

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_resetFont
   (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->resetFont();
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_removeLabel
   (JNIEnv *env, jclass clazz, jlong ptr, jint id)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->removeLabel(id);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setVisible__JIZ
   (JNIEnv *env, jclass clazz, jlong ptr, jint id, jboolean visible)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->setVisible(id, visible);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setAlwaysRender
   (JNIEnv *env, jclass clazz, jlong ptr, jint id, jboolean always_render)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->setAlwaysRender(id, always_render);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setMaxDrawResolution
   (JNIEnv *env, jclass clazz, jlong ptr, jint id, jdouble max_draw_resolution)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->setMaxDrawResolution(id, max_draw_resolution);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setColor
   (JNIEnv *env, jclass clazz, jlong ptr, jint id, jint color)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->setColor(id, color);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setBackColor
   (JNIEnv *env, jclass clazz, jlong ptr, jint id, jint color)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
   {
       glLabelManager->setBackColor(id, color);
   }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setFill
   (JNIEnv *env, jclass clazz, jlong ptr, jint id, jboolean fill)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
   {
       glLabelManager->setFill(id, fill);
   }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setVisible__JZ
        (JNIEnv *env, jclass clazz, jlong ptr, jboolean visible)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->setVisible(visible);
    }
}


JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setAlignment
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jint alignment)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        switch(alignment) {
            case com_atakmap_map_opengl_GLLabelManager_TEXT_ALIGNMENT_LEFT :
                glLabelManager->setAlignment(id, TETA_Left);
                break;
            case com_atakmap_map_opengl_GLLabelManager_TEXT_ALIGNMENT_CENTER :
                glLabelManager->setAlignment(id, TETA_Center);
                break;
            case com_atakmap_map_opengl_GLLabelManager_TEXT_ALIGNMENT_RIGHT :
                glLabelManager->setAlignment(id, TETA_Right);
                break;
        }
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setVerticalAlignment
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jint alignment)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        switch(alignment) {
            case com_atakmap_map_opengl_GLLabelManager_VERTICAL_ALIGNMENT_TOP :
                glLabelManager->setVerticalAlignment(id, TEVA_Top);
                break;
            case com_atakmap_map_opengl_GLLabelManager_VERTICAL_ALIGNMENT_MIDDLE :
                glLabelManager->setVerticalAlignment(id, TEVA_Middle);
                break;
            case com_atakmap_map_opengl_GLLabelManager_VERTICAL_ALIGNMENT_BOTTOM :
                glLabelManager->setVerticalAlignment(id, TEVA_Bottom);
                break;
        }
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setText
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jstring text)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        JNIStringUTF textString(*env, text);
        glLabelManager->setText(id, (TAK::Engine::Port::String) textString);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setAltitudeMode
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jint maltMode)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        switch(maltMode) {
            case com_atakmap_map_opengl_GLLabelManager_ALTMODE_CLAMP_TO_GROUND :
                glLabelManager->setAltitudeMode(id, TEAM_ClampToGround);
                break;
            case com_atakmap_map_opengl_GLLabelManager_ALTMODE_RELATIVE :
                glLabelManager->setAltitudeMode(id, TEAM_Relative);
                break;
            case com_atakmap_map_opengl_GLLabelManager_ALTMODE_ABSOLUTE :
                glLabelManager->setAltitudeMode(id, TEAM_Absolute);
                break;
        }
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setGeometry
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jlong geometryPtr)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        Geometry2Ptr_const geometry2PtrConst(nullptr, nullptr);
        if(geometryPtr) {
            Geometry_clone(geometry2PtrConst, *JLONG_TO_INTPTR(Geometry2, geometryPtr));
        }
        glLabelManager->setGeometry(id, *geometry2PtrConst);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setDesiredOffset
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jdouble x, jdouble y, jdouble z)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL) {
        TAK::Engine::Math::Point2<double> desiredOffset(x, y, z);
        glLabelManager->setDesiredOffset(id, desiredOffset);
    }

}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getSize
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jobject sizeRect)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        atakmap::math::Rectangle<double> labelRect;
        glLabelManager->getSize(id, labelRect);
        Math::Interop_marshal(sizeRect, *env, Rectangle2<double>(labelRect.x, labelRect.y, labelRect.width, labelRect.height));
    }
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLLabelManager_addLabel
        (JNIEnv *env, jclass clazz, jlong ptr, jstring label)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        GLLabel glLabel;

        JNIStringUTF labelString(*env, label);
        glLabel.setText((TAK::Engine::Port::String) labelString);
        return glLabelManager->addLabel(glLabel);
    }
    return -1;
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_release
    (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->release();
    }
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getRenderPass
        (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        return glLabelManager->getRenderPass();
    }

    return 0;
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_start
        (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->start();
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_stop
        (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->stop();
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setTextFormat
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jstring fontName, jfloat size,
         jboolean bold, jboolean italic, jboolean underline, jboolean strikethrough)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL) {
        JNIStringUTF fontNameString(*env, fontName);
        TextFormatParams format((TAK::Engine::Port::String) fontNameString,
                                                        size);
        format.bold = bold;
        format.italic = italic;
        format.underline = underline;
        format.strikethrough = strikethrough;
        glLabelManager->setTextFormat(id, &format);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setRotation
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jfloat rotation, jboolean absolute)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->setRotation(id, rotation, absolute);
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setPriority
        (JNIEnv *env, jclass clazz, jlong ptr, jint id, jint priority)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        switch(priority) {
            case com_atakmap_map_opengl_GLLabelManager_PRIORITY_HIGH :
                glLabelManager->setPriority(id, TEP_High);
                break;
            case com_atakmap_map_opengl_GLLabelManager_PRIORITY_STANDARD :
                glLabelManager->setPriority(id, TEP_Standard);
                break;
            case com_atakmap_map_opengl_GLLabelManager_PRIORITY_LOW :
                glLabelManager->setPriority(id, TEP_Low);
                break;
        }
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setLabelRotation
        (JNIEnv *env, jclass clazz, jlong ptr, jfloat labelRotation)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->labelRotation = labelRotation;
    }
}

JNIEXPORT jfloat JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getLabelRotation
        (JNIEnv *env, jclass clazz, long ptr)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        return glLabelManager->labelRotation;
    }
    else
    {
        return 0.f;
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setAbsoluteLabelRotation
        (JNIEnv *env, jclass clazz, jlong ptr, jboolean absoluteLabelRotation)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->absoluteLabelRotation = absoluteLabelRotation;
    }
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getAbsoluteLabelRotation
        (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        return glLabelManager->absoluteLabelRotation;
    }
    else
    {
        return false;
    }
}

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setLabelFadeTimer
        (JNIEnv *env, jclass clazz, jlong ptr, jlong labelFadeTimer)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->labelFadeTimer = labelFadeTimer;
    }
}

JNIEXPORT jlong JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getLabelFadeTimer
        (JNIEnv *env, jclass clazz, jlong ptr)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        return glLabelManager->labelFadeTimer;
    }
    else
    {
        return 0LL;
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_setHints
  (JNIEnv *env, jclass clazz, jlong ptr, jint id, jint hints)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        glLabelManager->setHints(id, hints);
    }
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getHints
  (JNIEnv *env, jclass clazz, jlong ptr, jint id)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL) {
        return glLabelManager->getHints(id);
    } else {
        return 0;
    }
}
JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_draw
        (JNIEnv *env, jclass clazz, jlong ptr, jlong viewPtr, jint renderPass)
{
    GLLabelManager *glLabelManager = JLONG_TO_INTPTR(GLLabelManager, ptr);

    if (glLabelManager != NULL)
    {
        GLMapView2 *cView = JLONG_TO_INTPTR(GLMapView2, viewPtr);
        glLabelManager->draw(*cView, renderPass);
    }
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getHintWeightedFloat
  (JNIEnv *env, jclass clazz)
{
    return GLLabel::Hints::WeightedFloat;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getHintDuplicateOnSplit
  (JNIEnv *env, jclass clazz)
{
    return GLLabel::Hints::DuplicateOnSplit;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getHintAutoSurfaceOffsetAdjust
  (JNIEnv *env, jclass clazz)
{
    return GLLabel::Hints::AutoSurfaceOffsetAdjust;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getHintXRay
        (JNIEnv *env, jclass clazz)
{
    return GLLabel::Hints::XRay;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_opengl_GLLabelManager_getHintScrollingText
        (JNIEnv *env, jclass clazz)
{
    return GLLabel::Hints::ScrollingText;
}

/* TODO: Implement

JNIEXPORT void JNICALL Java_com_atakmap_map_opengl_GLLabelManager_draw(const GLMapView2& view, const int render_pass) NOTHROWS override;

*/
