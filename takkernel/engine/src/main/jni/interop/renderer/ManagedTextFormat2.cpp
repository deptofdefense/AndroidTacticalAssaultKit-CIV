#include "interop/renderer/ManagedTextFormat2.h"

#include <util/IO2.h>

#include "common.h"
#include "interop/JNIIntArray.h"
#include "interop/JNIStringUTF.h"
#include "interop/java/JNIEnum.h"
#include "interop/java/JNILocalRef.h"

using namespace TAKEngineJNI::Interop::Renderer;

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace {
    struct
    {
        jclass id;
        jmethodID measureTextWidth;
        jmethodID measureTextHeight;
        jmethodID getCharWidth;
        jmethodID getFontSize;
        jmethodID getTallestGlyphHeight;
        jmethodID getBaselineSpacing;
        jmethodID renderGlyph;
        jmethodID getBaselineOffsetFromBottom;
    } MapTextFormat_class;

    struct {
        jclass id;
        jmethodID getPixels;
        jmethodID recycle;
        jmethodID createBitmap;

        struct {
            jobject ARGB_8888;
        } Config_enum;
    } Bitmap_class;

    struct {
        jclass id;
        jmethodID init;
    } Canvas_class;

    struct {
        jclass id;
        jmethodID createTextFormat;
    } TextFormatFactory_class;

    struct {
        jclass id;
        jmethodID init;
    } GLLabelManager_TextFormatParams_class;

    bool ManagedTextFormat2_init(JNIEnv &env) NOTHROWS;
    bool ManagedTextFormatFactory_init(JNIEnv &env) NOTHROWS;
}

ManagedTextFormat2::ManagedTextFormat2(JNIEnv &env_, jobject mtextformat_, jobject mglyphrenderer_) NOTHROWS :
    mimpl(env_.NewGlobalRef(mtextformat_)),
    mglyphRenderer(mglyphrenderer_ ? env_.NewGlobalRef(mglyphrenderer_) : env_.NewGlobalRef(mtextformat_))
{
    static bool clinit = ManagedTextFormat2_init(env_);
}
ManagedTextFormat2::~ManagedTextFormat2() NOTHROWS
{
    if(mimpl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(mimpl);
        mimpl = NULL;
    }
    if(mglyphRenderer) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(mglyphRenderer);
        mglyphRenderer = NULL;
    }
}
float ManagedTextFormat2::getStringWidth(const char *text) NOTHROWS
{
    LocalJNIEnv env;
    Java::JNILocalRef mtext(*env, env->NewStringUTF(text));
    return env->CallIntMethod(mimpl, MapTextFormat_class.measureTextWidth, (jstring)mtext);
}
float ManagedTextFormat2::getCharPositionWidth(const char *text, int position) NOTHROWS
{
    return getCharWidth(text[position]);
}
float ManagedTextFormat2::getCharWidth(const unsigned int chr) NOTHROWS
{
    LocalJNIEnv env;
    return env->CallFloatMethod(mimpl, MapTextFormat_class.getCharWidth, (jchar)chr);
}
float ManagedTextFormat2::getCharHeight() NOTHROWS
{
    LocalJNIEnv env;
    return (float)env->CallIntMethod(mimpl, MapTextFormat_class.getTallestGlyphHeight);
}
float ManagedTextFormat2::getDescent() NOTHROWS
{
    LocalJNIEnv env;
    return env->CallIntMethod(mimpl, MapTextFormat_class.getBaselineOffsetFromBottom);
}
float ManagedTextFormat2::getStringHeight(const char *text) NOTHROWS
{
    LocalJNIEnv env;
    Java::JNILocalRef mtext(*env, env->NewStringUTF(text));
    return env->CallIntMethod(mimpl, MapTextFormat_class.measureTextHeight, (jstring)mtext);
}
float ManagedTextFormat2::getBaselineSpacing() NOTHROWS
{
    LocalJNIEnv env;
    return env->CallIntMethod(mimpl, MapTextFormat_class.getBaselineSpacing);
}
int ManagedTextFormat2::getFontSize() NOTHROWS
{
    LocalJNIEnv env;
    return env->CallIntMethod(mimpl, MapTextFormat_class.getFontSize);
}
TAKErr ManagedTextFormat2::loadGlyph(TAK::Engine::Renderer::BitmapPtr &value, const unsigned int c) NOTHROWS
{
    /*
            glyphBitmap = Bitmap.createBitmap(
                    (int) Math.max(Math.ceil(this.textFormat.getCharWidth(s.charAt(off))),1),
                    (int) Math.ceil(this.charMaxHeight), Bitmap.Config.ARGB_8888);
            canvas = new Canvas(glyphBitmap);
     */
    LocalJNIEnv env;
    // create managed Bitmap
    const int glyphWidth = std::max((int)env->CallFloatMethod(mglyphRenderer, MapTextFormat_class.getCharWidth, (jchar)c), 1);
    const int glyphHeight = env->CallIntMethod(mglyphRenderer, MapTextFormat_class.getTallestGlyphHeight);
    Java::JNILocalRef mbitmap(*env, env->CallStaticObjectMethod(Bitmap_class.id, Bitmap_class.createBitmap, glyphWidth, glyphHeight, Bitmap_class.Config_enum.ARGB_8888));
    if(env->ExceptionCheck()) {
        env->ExceptionClear();
        return TE_Err;
    }
    // create canvas
    Java::JNILocalRef mcanvas(*env, env->NewObject(Canvas_class.id, Canvas_class.init, mbitmap.get()));
    if(env->ExceptionCheck()) {
        env->ExceptionClear();
        return TE_Err;
    }
    // render the glyph
    char cc[5u];
    if(c < 128u) {
        // 7 bits, emit
        cc[0] = c;
        cc[1] = '\0';
    } else if(c < 0x800) {
        // 11 bits
        cc[0] = 0xC0 | ((c >> 6)&0x1F);
        cc[1] = 0x80 | (c&0x3F);
        cc[2] = '\0';
    } else if(c < 0x10000) {
        // 16 bits
        cc[0] = 0xE0 | ((c >> 12)&0x0F);
        cc[1] = 0x80 | ((c>>6)&0x3F);
        cc[2] = 0x80 | (c&0x3F);
        cc[3] = '\0';
    } else {
        // truncated to 21 bits
        cc[0] = 0xF0 | ((c >> 18)&0x07);
        cc[1] = 0x80 | ((c>>12)&0x3F);
        cc[2] = 0x80 | ((c>>6)&0x3F);
        cc[3] = 0x80 | (c&0x3F);
        cc[4] = '\0';
    }
    Java::JNILocalRef mc(*env, env->NewStringUTF(cc));
    env->CallVoidMethod(mglyphRenderer, MapTextFormat_class.renderGlyph, mcanvas.get(), (jstring)mc, 0, (jfloat)0, (jfloat)0);
    if(env->ExceptionCheck()) {
        env->ExceptionClear();
        return TE_Err;
    }
    // obtain pixel data
    Java::JNILocalRef margbref(*env, env->NewIntArray(glyphWidth*glyphHeight));
    env->CallVoidMethod(mbitmap, Bitmap_class.getPixels, margbref.get(), 0, glyphWidth, 0, 0, glyphWidth, glyphHeight);
    if(env->ExceptionCheck()) {
        env->ExceptionClear();
        return TE_Err;
    }
    env->CallVoidMethod(mbitmap, Bitmap_class.recycle);
    // create native bitmap
    if(TE_PlatformEndian == TE_LittleEndian)
        value = BitmapPtr(new(std::nothrow) Bitmap2(glyphWidth, glyphHeight, Bitmap2::BGRA32), Memory_deleter_const<Bitmap2>);
    else
        value = BitmapPtr(new(std::nothrow) Bitmap2(glyphWidth, glyphHeight, Bitmap2::ARGB32), Memory_deleter_const<Bitmap2>);
    if(!value)
        return TE_OutOfMemory;

    JNIIntArray margb(*env, (jintArray)margbref.get(), JNI_ABORT);
    memcpy(value->getData(), margb.get<const uint8_t>(), (glyphWidth*glyphHeight)*sizeof(jint));

    if(!ATAKMapEngineJNI_equals(env, mimpl, mglyphRenderer)) {
        // resize the bitmap
        const int charWidth = std::max((int)env->CallFloatMethod(mimpl, MapTextFormat_class.getCharWidth, (jchar)c), 1);
        const int charHeight = env->CallIntMethod(mimpl, MapTextFormat_class.getTallestGlyphHeight);
        if(charWidth != value->getWidth() || charHeight != value->getHeight())
            value = BitmapPtr(new(std::nothrow) Bitmap2(*value, (std::size_t)charWidth, (std::size_t)charHeight), Memory_deleter_const<Bitmap2>);
    }

    return TE_Ok;
}

//
// ManagedTextFormatFactory
//

ManagedTextFormatFactory::ManagedTextFormatFactory(JNIEnv &env, jobject mfactory) NOTHROWS
    : mimpl(env.NewGlobalRef(mfactory))
{
    static bool cinit = ManagedTextFormatFactory_init(env);
}

ManagedTextFormatFactory::~ManagedTextFormatFactory() NOTHROWS
{
    if(mimpl) {
        LocalJNIEnv env;
        env->DeleteGlobalRef(mimpl);
        mimpl = NULL;
    }
}

TAK::Engine::Util::TAKErr ManagedTextFormatFactory::createTextFormat(TAK::Engine::Renderer::TextFormat2Ptr& value, const TAK::Engine::Renderer::TextFormatParams& params) NOTHROWS
{
    LocalJNIEnv env;

    Java::JNILocalRef mtext(*env, env->NewStringUTF(params.fontName));
    if(env->ExceptionCheck()) {
        env->ExceptionClear();
        return TE_Err;
    }

    // call createTextFormat
    Java::JNILocalRef mtextformat(*env, env->CallObjectMethod(mimpl, TextFormatFactory_class.createTextFormat, params.bold, params.italic,
                                                              (int)params.size, params.underline, params.strikethrough));
    if(env->ExceptionCheck()) {
        env->ExceptionClear();
        return TE_Err;
    }

    value = TextFormat2Ptr(new ManagedTextFormat2(*env, mtextformat), Memory_deleter_const<TextFormat2, ManagedTextFormat2>);
    return TE_Ok;
}

namespace
{
    bool ManagedTextFormat2_init(JNIEnv &env) NOTHROWS
    {
        MapTextFormat_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/android/maps/MapTextFormat");
        MapTextFormat_class.measureTextWidth = env.GetMethodID(MapTextFormat_class.id, "measureTextWidth", "(Ljava/lang/String;)I");
        MapTextFormat_class.measureTextHeight = env.GetMethodID(MapTextFormat_class.id, "measureTextHeight", "(Ljava/lang/String;)I");
        MapTextFormat_class.getCharWidth = env.GetMethodID(MapTextFormat_class.id, "getCharWidth", "(I)F");
        MapTextFormat_class.getFontSize = env.GetMethodID(MapTextFormat_class.id, "getFontSize", "()I");
        MapTextFormat_class.getTallestGlyphHeight = env.GetMethodID(MapTextFormat_class.id, "getTallestGlyphHeight", "()I");
        MapTextFormat_class.getBaselineSpacing = env.GetMethodID(MapTextFormat_class.id, "getBaselineSpacing", "()I");
        MapTextFormat_class.renderGlyph = env.GetMethodID(MapTextFormat_class.id, "renderGlyph", "(Landroid/graphics/Canvas;Ljava/lang/String;IFF)V");
        MapTextFormat_class.getBaselineOffsetFromBottom = env.GetMethodID(MapTextFormat_class.id, "getBaselineOffsetFromBottom", "()I");

        Bitmap_class.id = ATAKMapEngineJNI_findClass(&env, "android/graphics/Bitmap");
        Bitmap_class.getPixels = env.GetMethodID(Bitmap_class.id, "getPixels", "([IIIIIII)V");
        Bitmap_class.recycle = env.GetMethodID(Bitmap_class.id, "recycle", "()V");
        Bitmap_class.createBitmap = env.GetStaticMethodID(Bitmap_class.id, "createBitmap", "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");
        {
            Java::JNILocalRef e(env, nullptr);
            if(Java::JNIEnum_value(e, env, "android/graphics/Bitmap$Config", "ARGB_8888") != TE_Ok)
                return false;
            Bitmap_class.Config_enum.ARGB_8888 = env.NewWeakGlobalRef(e);
        }
        Canvas_class.id = ATAKMapEngineJNI_findClass(&env, "android/graphics/Canvas");
        Canvas_class.init = env.GetMethodID(Canvas_class.id, "<init>", "(Landroid/graphics/Bitmap;)V");


        return true;
    }

    bool ManagedTextFormatFactory_init(JNIEnv &env) NOTHROWS
    {
        TextFormatFactory_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/opengl/TextFormatFactory");
        TextFormatFactory_class.createTextFormat = env.GetMethodID(TextFormatFactory_class.id,
                "createTextFormat",
                "(ZZIZZ)Lcom/atakmap/android/maps/MapTextFormat;");
        return TextFormatFactory_class.id != 0 && TextFormatFactory_class.createTextFormat != 0;
    }
}
