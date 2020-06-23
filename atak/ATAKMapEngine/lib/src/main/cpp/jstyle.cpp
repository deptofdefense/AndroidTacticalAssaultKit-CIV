#include "jstyle.h"

#include <feature/Style.h>
#include <util/Memory.h>

#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/JNILongArray.h"
#include "interop/Pointer.h"

using namespace TAK::Engine::Util;

using namespace atakmap::feature;

using namespace TAKEngineJNI::Interop;

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getTESC_1BasicStrokeStyle
  (JNIEnv *env, jclass clazz)
{
    return TESC_BasicStrokeStyle;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getTESC_1BasicFillStyle
  (JNIEnv *env, jclass clazz)
{
    return TESC_BasicFillStyle;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getTESC_1BasicPointStyle
  (JNIEnv *env, jclass clazz)
{
    return TESC_BasicPointStyle;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getTESC_1IconPointStyle
  (JNIEnv *env, jclass clazz)
{
    return TESC_IconPointStyle;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getTESC_1LabelPointStyle
  (JNIEnv *env, jclass clazz)
{
    return TESC_LabelPointStyle;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getTESC_1CompositeStyle
  (JNIEnv *env, jclass clazz)
{
    return TESC_CompositeStyle;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getTESC_1PatternStrokeStyle
  (JNIEnv *env, jclass clazz)
{
    return TESC_PatternStrokeStyle;
}

/*****************************************************************************/
// Style

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_feature_style_Style_destruct
  (JNIEnv *env, jclass clazz, jobject pointer)
{
    Pointer_destruct_iface<Style>(env, pointer);
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_style_Style_clone
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Style *style = JLONG_TO_INTPTR(Style, ptr);
    if(!style)
        return NULL;
    StylePtr retval(style->clone(), Style::destructStyle);
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getClass
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Style *style = JLONG_TO_INTPTR(Style, ptr);
    if(!style) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return 0;
    }
    return style->getClass();
}

#define STYLE_IMPL_ACCESSOR_BODY(sc, acc) \
    Style *style = JLONG_TO_INTPTR(Style, ptr); \
    if(!style || style->getClass() != TESC_##sc) { \
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg); \
        return 0; \
    } \
    const sc &impl = static_cast<sc &>(*style); \
    return impl.acc();
    
/*****************************************************************************/
// BasicFillStyle

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_style_Style_BasicFillStyle_1create
  (JNIEnv *env, jclass clazz, jint color)
{
    try {
        StylePtr retval(new BasicFillStyle(color), Memory_deleter_const<Style, BasicFillStyle>);
        return NewPointer(env, std::move(retval));
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
        return NULL;
    }
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_BasicFillStyle_1getColor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(BasicFillStyle, getColor)
}

/*****************************************************************************/
// BasicStrokeStyle

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_style_Style_BasicStrokeStyle_1create
  (JNIEnv *env, jclass clazz, jint color, jfloat strokeWidth)
{
    try {
        StylePtr retval(new BasicStrokeStyle(color, strokeWidth), Memory_deleter_const<Style, BasicStrokeStyle>);
        return NewPointer(env, std::move(retval));
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
        return NULL;
    }
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_BasicStrokeStyle_1getColor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(BasicStrokeStyle, getColor)
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_feature_style_Style_BasicStrokeStyle_1getStrokeWidth
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(BasicStrokeStyle, getStrokeWidth)
}

/*****************************************************************************/
// BasicPointStyle

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_style_Style_BasicPointStyle_1create
  (JNIEnv *env, jclass clazz, jint color, jfloat size)
{
    try {
        StylePtr retval(new BasicPointStyle(color, size), Memory_deleter_const<Style, BasicPointStyle>);
        return NewPointer(env, std::move(retval));
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
        return NULL;
    }
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_BasicPointStyle_1getColor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(BasicPointStyle, getColor)
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_feature_style_Style_BasicPointStyle_1getSize
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(BasicPointStyle, getSize)
}

/*****************************************************************************/
// IconPointStyle

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_style_Style_IconPointStyle_1create
  (JNIEnv *env, jclass clazz, jint color, jstring juri, jfloat width, jfloat height, jint halign, jint valign, jfloat rotation, jboolean isRotationAbsolute)
{
    try {
        JNIStringUTF uri(*env, juri);

        StylePtr retval(new IconPointStyle(color,
                                             uri,
                                             width,
                                             height,
                                             (IconPointStyle::HorizontalAlignment)halign,
                                             (IconPointStyle::VerticalAlignment)valign,
                                             rotation,
                                             isRotationAbsolute),
                        Memory_deleter_const<Style, IconPointStyle>);
        return NewPointer(env, std::move(retval));
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
        return NULL;
    }
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_IconPointStyle_1getColor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(IconPointStyle, getColor)
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_style_Style_IconPointStyle_1getUri
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Style *style = JLONG_TO_INTPTR(Style, ptr);
    if(!style || style->getClass() != TESC_IconPointStyle) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    const IconPointStyle &impl = static_cast<IconPointStyle &>(*style);
    return env->NewStringUTF(impl.getIconURI());
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_feature_style_Style_IconPointStyle_1getWidth
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(IconPointStyle, getWidth)
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_feature_style_Style_IconPointStyle_1getHeight
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(IconPointStyle, getHeight)
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_IconPointStyle_1getHorizontalAlignment
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(IconPointStyle, getHorizontalAlignment)
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_IconPointStyle_1getVerticalAlignment
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(IconPointStyle, getVerticalAlignment)
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_feature_style_Style_IconPointStyle_1getRotation
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(IconPointStyle, getRotation)
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_style_Style_IconPointStyle_1isRotationAbsolute
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(IconPointStyle, isRotationAbsolute)
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getIconPointStyle_1HorizontalAlignment_1LEFT
  (JNIEnv *env, jclass clazz)
{
    return IconPointStyle::LEFT;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getIconPointStyle_1HorizontalAlignment_1H_1CENTER
  (JNIEnv *env, jclass clazz)
{
    return IconPointStyle::H_CENTER;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getIconPointStyle_1HorizontalAlignment_1RIGHT
  (JNIEnv *env, jclass clazz)
{
    return IconPointStyle::RIGHT;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getIconPointStyle_1VerticalAlignment_1ABOVE
  (JNIEnv *env, jclass clazz)
{
    return IconPointStyle::ABOVE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getIconPointStyle_1VerticalAlignment_1V_1CENTER
  (JNIEnv *env, jclass clazz)
{
    return IconPointStyle::V_CENTER;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getIconPointStyle_1VerticalAlignment_1BELOW
  (JNIEnv *env, jclass clazz)
{
    return IconPointStyle::BELOW;
}

/*****************************************************************************/
// LabelPointStyle

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1create
  (JNIEnv *env, jclass clazz, jstring jtext, jint color, jint bgColor, jint scrollMode, jfloat size, jint halign, jint valign, jfloat rotation, jboolean isRotationAbsolute)
{
    try {
        JNIStringUTF text(*env, jtext);
        StylePtr retval(new LabelPointStyle(text,
                                              color,
                                              bgColor,
                                              (LabelPointStyle::ScrollMode)scrollMode,
                                              size,
                                              (LabelPointStyle::HorizontalAlignment)halign,
                                              (LabelPointStyle::VerticalAlignment)valign,
                                              rotation,
                                              isRotationAbsolute),
                        Memory_deleter_const<Style, LabelPointStyle>);
        return NewPointer(env, std::move(retval));
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
        return NULL;
    }
}
JNIEXPORT jstring JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1getText
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    Style *style = JLONG_TO_INTPTR(Style, ptr);
    if(!style || style->getClass() != TESC_LabelPointStyle) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    const LabelPointStyle &impl = static_cast<LabelPointStyle &>(*style);
    return env->NewStringUTF(impl.getText());
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1getTextColor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(LabelPointStyle, getTextColor)
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1getBackgroundColor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(LabelPointStyle, getBackgroundColor)
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1getScrollMode
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(LabelPointStyle, getScrollMode)
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1getTextSize
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(LabelPointStyle, getTextSize)
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1getHorizontalAlignment
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(LabelPointStyle, getHorizontalAlignment)
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1getVerticalAlignment
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(LabelPointStyle, getVerticalAlignment)
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1getRotation
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(LabelPointStyle, getRotation)
}
JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1isRotationAbsolute
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(LabelPointStyle, isRotationAbsolute)
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getLabelPointStyle_1HorizontalAlignment_1LEFT
  (JNIEnv *env, jclass clazz)
{
    return LabelPointStyle::LEFT;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getLabelPointStyle_1HorizontalAlignment_1H_1CENTER
  (JNIEnv *env, jclass clazz)
{
    return LabelPointStyle::H_CENTER;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getLabelPointStyle_1HorizontalAlignment_1RIGHT
  (JNIEnv *env, jclass clazz)
{
    return LabelPointStyle::RIGHT;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getLabelPointStyle_1VerticalAlignment_1ABOVE
  (JNIEnv *env, jclass clazz)
{
    return LabelPointStyle::ABOVE;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getLabelPointStyle_1VerticalAlignment_1V_1CENTER
  (JNIEnv *env, jclass clazz)
{
    return LabelPointStyle::V_CENTER;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getLabelPointStyle_1VerticalAlignment_1BELOW
  (JNIEnv *env, jclass clazz)
{
    return LabelPointStyle::BELOW;
}

JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getLabelPointStyle_1ScrollMode_1DEFAULT
  (JNIEnv *env, jclass clazz)
{
    return LabelPointStyle::DEFAULT;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getLabelPointStyle_1ScrollMode_1ON
  (JNIEnv *env, jclass clazz)
{
    return LabelPointStyle::ON;
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_getLabelPointStyle_1ScrollMode_1OFF
  (JNIEnv *env, jclass clazz)
{
    return LabelPointStyle::OFF;
}

/*****************************************************************************/
// CompositeStyle

JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_style_Style_CompositeStyle_1create
  (JNIEnv *env, jclass clazz, jlongArray jstylePtrs)
{
    if(!jstylePtrs) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }

    try {
        JNILongArray stylePtrs(*env, jstylePtrs, JNI_ABORT);

        // CompositeStyle is going to assume ownership, so we are going to pass
        // it clones
        std::vector<Style *> styles;
        styles.reserve(stylePtrs.length());

        for(std::size_t i = 0u; i < stylePtrs.length(); i++) {
            Style *s = JLONG_TO_INTPTR(Style, stylePtrs[i]);
            if(s)
                s = s->clone();
            styles.push_back(s);
        }

        StylePtr retval(new CompositeStyle(styles), Memory_deleter_const<Style, CompositeStyle>);
        return NewPointer(env, std::move(retval));
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
        return NULL;
    }
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_CompositeStyle_1getNumStyles
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(CompositeStyle, getStyleCount)
}
JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_style_Style_CompositeStyle_1getStyle
  (JNIEnv *env, jclass clazz, jlong ptr, jint idx)
{
    Style *style = JLONG_TO_INTPTR(Style, ptr);
    if(!style || style->getClass() != TESC_CompositeStyle) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_InvalidArg);
        return NULL;
    }
    const CompositeStyle &impl = static_cast<CompositeStyle &>(*style);
    try {
        const Style &s = impl.getStyle(idx);
        StylePtr retval(s.clone(), Style::destructStyle);
        return NewPointer(env, std::move(retval));
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
        return NULL;
    }
}

/*****************************************************************************/
// PatternStrokeStyle


JNIEXPORT jobject JNICALL Java_com_atakmap_map_layer_feature_style_Style_PatternStrokeStyle_1create
  (JNIEnv *env, jclass clazz, jlong pattern, jint patternLen, jint color, jfloat width)
{
    try {
        StylePtr retval(new PatternStrokeStyle(pattern, patternLen, color, width), Memory_deleter_const<Style, PatternStrokeStyle>);
        return NewPointer(env, std::move(retval));
    } catch(...) {
        ATAKMapEngineJNI_checkOrThrow(env, TE_Err);
        return NULL;
    }
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_layer_feature_style_Style_PatternStrokeStyle_1getPattern
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(PatternStrokeStyle, getPattern)
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_PatternStrokeStyle_1getPatternLength
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(PatternStrokeStyle, getPatternLength)
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_PatternStrokeStyle_1getColor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(PatternStrokeStyle, getColor)
}
JNIEXPORT jfloat JNICALL Java_com_atakmap_map_layer_feature_style_Style_PatternStrokeStyle_1getStrokeWidth
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(PatternStrokeStyle, getStrokeWidth)
}
