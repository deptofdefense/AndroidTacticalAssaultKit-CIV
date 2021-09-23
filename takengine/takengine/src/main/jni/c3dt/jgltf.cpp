#include "jgltf.h"

#include <memory>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#include <thread/Lock.h>
#include <thread/Mutex.h>
#include <util/NonHeapAllocatable.h>

#include "DebugTrace.h"
#include "GLTF.h"
#include "GLTFRenderer.h"
#include "shaders.h"
#include "common.h"
#include "interop/java/JNILocalRef.h"
#include "interop/JNIByteArray.h"
#include "interop/JNIDoubleArray.h"
#include "interop/JNIStringUTF.h"

using namespace TAKEngineJNI;

namespace
{
    struct GLTF
    {
        tinygltfloader::Scene *v1;
        tinygltf::Model *v2;
    };

    struct {
        jclass id;
        jfieldID width;
        jfieldID height;
        jfieldID bits;
        jfieldID component;
        jfieldID pixelType;
        jfieldID bytes;
    } GLTFBitmap_class;

    struct {
        jclass id;
        jmethodID loadBitmap;
    } GLTF_class;

    class JNIGLTFBitmapLoader : public GLTFBitmapLoader,
                                TAK::Engine::Util::NonHeapAllocatable
    {
    public:
        JNIGLTFBitmapLoader(JNIEnv &env, jobject mhandler) NOTHROWS;
        ~JNIGLTFBitmapLoader() NOTHROWS;
        virtual bool load(const char* uri) NOTHROWS;
        virtual void unload() NOTHROWS;
    private:
        JNIEnv &env;
        jobject bmp;
        jobject mhandler;
    };

    bool GLTF_class_init(JNIEnv &env) NOTHROWS;
}

JNIEXPORT jlong JNICALL Java_com_atakmap_map_formats_c3dt_GLTF_createFromFile
  (JNIEnv *env, jclass, jstring mpath)
{
    debug_trace(Java_com_atakmap_map_formats_c3dt_GLTF_createFromFile);
    return 0LL;
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_formats_c3dt_GLTF_createFromBytes
  (JNIEnv *env, jclass clazz, jbyteArray mdata, jint off, jint len, jstring mbaseDir, jobject mhandler)
{
    debug_trace(Java_com_atakmap_map_formats_c3dt_GLTF_createFromBytes);
    if(!mdata)
        return 0LL;
    if(len <= 0)
        return 0LL;
    std::string err;
    std::string warn;
    Interop::JNIByteArray cdata(*env, mdata, JNI_ABORT);
    std::string baseDir;
    if(mbaseDir) {
        TAK::Engine::Port::String cbaseDir;
        Interop::JNIStringUTF_get(cbaseDir, *env, mbaseDir);
        baseDir = cbaseDir;
    }

    GLTF gltf;
    gltf.v1 = nullptr;
    gltf.v2 = nullptr;

    JNIGLTFBitmapLoader bitmapLoader(*env, mhandler);

    const unsigned char *binary = cdata.get<const unsigned char>() + off;
    switch(GLTF_getVersion(binary, len)) {
    case 1 :
        gltf.v1 = GLTF_loadV1(binary, len, baseDir);
        GLTF_loadExtImagesV1(gltf.v1, &bitmapLoader, baseDir);
        break;
    case 2 :
        gltf.v2 = GLTF_loadV2(binary, len, baseDir);
        GLTF_loadExtImagesV2(gltf.v2, &bitmapLoader, baseDir);
        break;
    default :
        return 0LL;
    }

    return (jlong)(intptr_t)new GLTF(gltf);
}
JNIEXPORT void JNICALL Java_com_atakmap_map_formats_c3dt_GLTF_destroy
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    debug_trace(Java_com_atakmap_map_formats_c3dt_GLTF_destroy);
    std::unique_ptr<GLTF> cmodel((GLTF *)(intptr_t)ptr);
    if(cmodel.get()) {
        GLTF_destructV1(cmodel->v1);
        GLTF_destructV2(cmodel->v2);
    }
    cmodel.reset();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_formats_c3dt_GLTF_draw
  (JNIEnv * env, jclass clazz, jlong vao, jboolean useShader, jint MVP_u, jdoubleArray mMVP)
{
    debug_trace(Java_com_atakmap_map_formats_c3dt_GLTF_draw);
    static Shaders shader;

    if(useShader) {
        glUseProgram(shader.pid);

        // grab uniforms to modify
        MVP_u = glGetUniformLocation(shader.pid, "MVP");
        GLuint sun_position_u = glGetUniformLocation(shader.pid, "sun_position");
        GLuint sun_color_u = glGetUniformLocation(shader.pid, "sun_color");
        GLuint tex_u = glGetUniformLocation(shader.pid, "tex");

        float sun_position[3] =  { 3.0, 10.0, -5.0 };
        float sun_color[3] = { 1.0, 1.0, 1.0 };

        GLint activeTex;
        glGetIntegerv(GL_ACTIVE_TEXTURE, &activeTex);

        glUniform3fv(sun_position_u, 1, sun_position);
        glUniform3fv(sun_color_u, 1, sun_color);
        glUniform1i(tex_u, activeTex - GL_TEXTURE0);
    }

    Interop::JNIDoubleArray mvp(*env, mMVP, JNI_ABORT);
    float cmvp[16];
    for(std::size_t i = 0u; i < 16u; i++)
        cmvp[i] = (float)mvp[i];
    glUniformMatrix4fv(MVP_u, 1, GL_FALSE, cmvp);


    GLRendererState *renderer = (GLRendererState *)(intptr_t)vao;
    if(renderer)
        Renderer_draw(*renderer, MVP_u, mvp.get<const double>());
}
JNIEXPORT jlong JNICALL Java_com_atakmap_map_formats_c3dt_GLTF_bindModel
  (JNIEnv *env, jclass clazz, jlong ptr)
{
    debug_trace(Java_com_atakmap_map_formats_c3dt_GLTF_bindModel);
    GLTF *gltf = (GLTF *)(intptr_t)ptr;
    if(!gltf) {
#ifdef __ANDROID__
        __android_log_print(ANDROID_LOG_ERROR, "Cesium3DTiles", "Cannot bind NULL model");
#endif
        return 0LL;
    }

    std::unique_ptr<GLRendererState> retval(new GLRendererState());
    bool bound = false;
    if(gltf->v1)
        bound = Renderer_bindModel(retval.get(), *gltf->v1);
    else if(gltf->v2)
        bound = Renderer_bindModel(retval.get(), *gltf->v2);
    else
        return 0LL;

    if(!bound)
        return 0LL;
    return (jlong)(intptr_t)retval.release();
}
JNIEXPORT void JNICALL Java_com_atakmap_map_formats_c3dt_GLTF_releaseModel
  (JNIEnv *env, jclass clazz, jlong vao)
{
    debug_trace(Java_com_atakmap_map_formats_c3dt_GLTF_releaseModel);
    std::unique_ptr<GLRendererState> renderer((GLRendererState *)(intptr_t)vao);
    if(renderer.get())
        Renderer_release(*renderer);
}

namespace {
    bool GLTF_class_init(JNIEnv &env) NOTHROWS
    {
        GLTF_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/formats/c3dt/GLTF");
        GLTF_class.loadBitmap = env.GetStaticMethodID(GLTF_class.id, "loadExternalTextureBitmap",
                "(Lcom/atakmap/map/formats/c3dt/ContentSource;Ljava/lang/String;)Lcom/atakmap/map/formats/c3dt/GLTFBitmap;");
        if (!GLTF_class.loadBitmap) return false;
        
        GLTFBitmap_class.id = ATAKMapEngineJNI_findClass(&env, "com/atakmap/map/formats/c3dt/GLTFBitmap");
        if (!GLTFBitmap_class.id) return false;
        GLTFBitmap_class.width = env.GetFieldID(GLTFBitmap_class.id, "width", "I");
        if (!GLTFBitmap_class.width) return false;
        GLTFBitmap_class.height = env.GetFieldID(GLTFBitmap_class.id, "height", "I");
        if (!GLTFBitmap_class.height) return false;
        GLTFBitmap_class.bits = env.GetFieldID(GLTFBitmap_class.id, "bits", "I");
        if (!GLTFBitmap_class.bits) return false;
        GLTFBitmap_class.component = env.GetFieldID(GLTFBitmap_class.id, "component", "I");
        if (!GLTFBitmap_class.component) return false;
        GLTFBitmap_class.pixelType = env.GetFieldID(GLTFBitmap_class.id, "pixelType", "I");
        if (!GLTFBitmap_class.pixelType) return false;
        GLTFBitmap_class.bytes = env.GetFieldID(GLTFBitmap_class.id, "bytes", "Ljava/nio/ByteBuffer;");
        if (!GLTFBitmap_class.bytes) return false;

        return true;
    }

    JNIGLTFBitmapLoader::JNIGLTFBitmapLoader(JNIEnv& env, jobject mhandler) NOTHROWS:
     env(env),
     mhandler(mhandler),
     bmp(nullptr) { }

    JNIGLTFBitmapLoader::~JNIGLTFBitmapLoader() NOTHROWS
    {
        unload();
    }

    bool JNIGLTFBitmapLoader::load(const char* uri) NOTHROWS {
        if (!uri)
            return false;

        static bool clinit = GLTF_class_init(env);
        if(!clinit)
            return false;

        unload();

        Interop::Java::JNILocalRef uriObj(env, env.NewStringUTF(uri));
        Interop::Java::JNILocalRef mbmp(env, env.CallStaticObjectMethod(GLTF_class.id, GLTF_class.loadBitmap, mhandler, (jstring)uriObj));
        if (!mbmp)
            return false;

        bmp = env.NewGlobalRef(mbmp);
        this->width = env.GetIntField(bmp, GLTFBitmap_class.width);
        this->height = env.GetIntField(bmp, GLTFBitmap_class.height);
        this->bits = env.GetIntField(bmp, GLTFBitmap_class.bits);
        this->pixel_type = env.GetIntField(bmp, GLTFBitmap_class.pixelType);
        this->component = env.GetIntField(bmp, GLTFBitmap_class.component);

        Interop::Java::JNILocalRef bytes(env, env.GetObjectField(bmp, GLTFBitmap_class.bytes));
        this->begin = static_cast<unsigned char *>(env.GetDirectBufferAddress(bytes));
        this->end = this->begin + env.GetDirectBufferCapacity(bytes);

        return true;
    }

    void JNIGLTFBitmapLoader::unload() NOTHROWS
    {
        if(bmp) {
            env.DeleteGlobalRef(bmp);
            bmp = nullptr;
        }
    }
}
