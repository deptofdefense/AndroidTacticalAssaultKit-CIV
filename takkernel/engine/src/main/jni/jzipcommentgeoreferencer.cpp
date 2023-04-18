#include "com_atakmap_map_layer_model_zipcomment_ZipCommentGeoreferencer.h"

#include "model/ZipCommentGeoreferencer.h"
#include "common.h"
#include "interop/JNIStringUTF.h"
#include "interop/model/Interop.h"

using namespace TAKEngineJNI::Interop;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;

namespace {

    struct
    {
        jclass id;
        jfieldID pointer;
        jmethodID ctor;
        jmethodID init;
    } ModelInfo_class;

}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_model_zipcomment_ZipCommentGeoreferencer_locate
  (JNIEnv *env, jclass clazz, jobject jModelInfo)
{
    TAKErr code(TE_Ok);

    SceneInfo sceneInfo;
    code = Model::Interop_marshal(sceneInfo, env, jModelInfo);
    if (code != TE_Ok)
    {
        // Log something?
        return JNI_FALSE;
    }

    ZipCommentGeoreferencer zipCommentGeoreferencer;
    code = zipCommentGeoreferencer.locate(sceneInfo);
    if (code != TE_Ok)
    {
        if (code != TE_Unsupported)
        {
            // Log something?
        }
        return JNI_FALSE;
    }

    code = Model::Interop_marshal(jModelInfo, env, sceneInfo);
    if (code != TE_Ok)
    {
        // Log something?
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_atakmap_map_layer_model_zipcomment_ZipCommentGeoreferencer_isGeoReferenced
  (JNIEnv *env, jclass clazz, jstring juri)
{
    JNIStringUTF uri(*env, juri);
    const char *curi = uri;
    return ZipCommentGeoreferencer::isGeoReferenced(curi) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL Java_com_atakmap_map_layer_model_zipcomment_ZipCommentGeoreferencer_removeGeoReference
  (JNIEnv *env, jclass clazz, jstring juri)
{
    JNIStringUTF uri(*env, juri);
    const char *curi = uri;
    ZipCommentGeoreferencer::removeGeoReference(curi);  // ignoring the return.
}
