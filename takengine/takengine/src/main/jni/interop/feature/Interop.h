#ifndef TAKENGINEJNI_INTEROP_FEATURE_INTEROP_H_INCLUDED
#define TAKENGINEJNI_INTEROP_FEATURE_INTEROP_H_INCLUDED

#include <jni.h>

#include <feature/Feature2.h>
#include <feature/Geometry2.h>
#include <feature/Style.h>
#include <port/Platform.h>
#include <util/Error.h>
#include <interop/java/JNILocalRef.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Feature {
            TAK::Engine::Util::TAKErr Interop_create(TAK::Engine::Feature::Geometry2Ptr &value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_create(TAK::Engine::Feature::Geometry2Ptr_const &value, JNIEnv *env, jobject jgeom) NOTHROWS;
            jobject Interop_create(JNIEnv *env, const TAK::Engine::Feature::Geometry2 &cgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(TAK::Engine::Feature::Geometry2 **value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(const TAK::Engine::Feature::Geometry2 **value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(std::shared_ptr<TAK::Engine::Feature::Geometry2> &value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(std::shared_ptr<const TAK::Engine::Feature::Geometry2> &value, JNIEnv *env, jobject jgeom) NOTHROWS;

            TAK::Engine::Util::TAKErr Interop_create(TAK::Engine::Feature::StylePtr &value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_create(TAK::Engine::Feature::StylePtr_const &value, JNIEnv *env, jobject jgeom) NOTHROWS;
            jobject Interop_create(JNIEnv *env, const atakmap::feature::Style &cstyle) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(atakmap::feature::Style **value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(const atakmap::feature::Style **value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(std::shared_ptr<atakmap::feature::Style> &value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(std::shared_ptr<const atakmap::feature::Style> &value, JNIEnv *env, jobject jgeom) NOTHROWS;

            TAK::Engine::Util::TAKErr Interop_create(TAK::Engine::Feature::AttributeSetPtr &value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_create(TAK::Engine::Feature::AttributeSetPtr_const &value, JNIEnv *env, jobject jgeom) NOTHROWS;
            jobject Interop_create(JNIEnv *env, const atakmap::util::AttributeSet &cattr) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(atakmap::util::AttributeSet **value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(const atakmap::util::AttributeSet **value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(std::shared_ptr<atakmap::util::AttributeSet> &value, JNIEnv *env, jobject jgeom) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_get(std::shared_ptr<const atakmap::util::AttributeSet> &value, JNIEnv *env, jobject jgeom) NOTHROWS;

            TAK::Engine::Util::TAKErr Interop_copy(TAK::Engine::Feature::Envelope2 *value, JNIEnv *env, jobject jenvelope) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_marshal(jobject value, JNIEnv &env, const TAK::Engine::Feature::Envelope2 &cenvelope) NOTHROWS;
            TAK::Engine::Util::TAKErr Interop_marshal(Java::JNILocalRef &value, JNIEnv &env, const TAK::Engine::Feature::Envelope2 &cenvelope) NOTHROWS;
        }
    }
}

#endif
