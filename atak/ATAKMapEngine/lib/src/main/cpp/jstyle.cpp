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
    TAKErr code(TE_Ok);
    StylePtr retval(nullptr, nullptr);
    code = BasicFillStyle_create(retval, color);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
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
    TAKErr code(TE_Ok);
    StylePtr retval(nullptr, nullptr);
    code = BasicStrokeStyle_create(retval, color, strokeWidth);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
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
    TAKErr code(TE_Ok);
    StylePtr retval(nullptr, nullptr);
    code = BasicPointStyle_create(retval, color, size);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
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
    TAKErr code(TE_Ok);
    JNIStringUTF uri(*env, juri);
    StylePtr retval(nullptr, nullptr);
    code = IconPointStyle_create(retval, color,
                                         uri,
                                         width,
                                         height,
                                         (IconPointStyle::HorizontalAlignment)halign,
                                         (IconPointStyle::VerticalAlignment)valign,
                                         rotation,
                                         isRotationAbsolute);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
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
  (JNIEnv *env, jclass clazz, jstring jtext, jint color, jint bgColor, jint scrollMode, jfloat size, jint halign, jint valign, jfloat rotation, jboolean isRotationAbsolute, jdouble labelMinRenderResolution)
{
    TAKErr code(TE_Ok);
    JNIStringUTF text(*env, jtext);
    StylePtr retval(nullptr, nullptr);

    code =  LabelPointStyle_create(retval, text,
                                           color,
                                           bgColor,
                                           (LabelPointStyle::ScrollMode)scrollMode,
                                           size,
                                           (LabelPointStyle::HorizontalAlignment)halign,
                                           (LabelPointStyle::VerticalAlignment)valign,
                                           rotation,
                                           isRotationAbsolute, 0.0, 0.0,
                                           labelMinRenderResolution);

    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
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
JNIEXPORT jdouble JNICALL Java_com_atakmap_map_layer_feature_style_Style_LabelPointStyle_1getLabelMinRenderResolution
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(LabelPointStyle, getLabelMinRenderResolution)
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

    TAKErr code(TE_Ok);
    JNILongArray stylePtrs(*env, jstylePtrs, JNI_ABORT);

    std::vector<const Style *> styles;
    styles.reserve(stylePtrs.length());

    for(std::size_t i = 0u; i < stylePtrs.length(); i++) {
        Style *s = JLONG_TO_INTPTR(Style, stylePtrs[i]);
        styles.push_back(s);
    }

    StylePtr retval(nullptr, nullptr);
    code = CompositeStyle_create(retval, styles.empty() ? nullptr : &styles.at(0), styles.size());
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
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
  (JNIEnv *env, jclass clazz, jint factor, jshort pattern, jint color, jfloat width)
{
    TAKErr code(TE_Ok);
    StylePtr retval(nullptr, nullptr);
    code = PatternStrokeStyle_create(retval, factor, pattern, color, width);
    if(ATAKMapEngineJNI_checkOrThrow(env, code))
        return NULL;
    return NewPointer(env, std::move(retval));
}
JNIEXPORT jshort JNICALL Java_com_atakmap_map_layer_feature_style_Style_PatternStrokeStyle_1getPattern
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(PatternStrokeStyle, getPattern)
}
JNIEXPORT jint JNICALL Java_com_atakmap_map_layer_feature_style_Style_PatternStrokeStyle_1getFactor
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    STYLE_IMPL_ACCESSOR_BODY(PatternStrokeStyle, getFactor)
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
