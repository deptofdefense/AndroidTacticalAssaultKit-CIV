#ifndef TAKENGINEJNI_INTEROP_FEATURE_MANAGEDFEATUREDDDATASOURCE2_H_INCLUDED
#define TAKENGINEJNI_INTEROP_FEATURE_MANAGEDFEATUREDDDATASOURCE2_H_INCLUDED

#include <jni.h>

#include <feature/FeatureDataSource2.h>
#include <port/String.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Feature {
            class ManagedFeatureDataSource2 : public TAK::Engine::Feature::FeatureDataSource2
            {
            public :
                ManagedFeatureDataSource2(JNIEnv *env, jobject impl) NOTHROWS;
                virtual ~ManagedFeatureDataSource2() NOTHROWS;
            public :
                virtual TAK::Engine::Util::TAKErr parse(TAK::Engine::Feature::FeatureDataSource2::ContentPtr &content, const char *file) NOTHROWS;
                virtual const char *getName() const NOTHROWS;
                virtual TAK::Engine::Feature::AltitudeMode getAltitudeMode() const NOTHROWS;
                virtual double getExtrude() const NOTHROWS;
                virtual int parseVersion() const NOTHROWS;
            private :
                jobject impl;
                TAK::Engine::Port::String name;
                TAK::Engine::Feature::AltitudeMode altMode;
                double extrude;
            };
        }
    }
}

#endif
