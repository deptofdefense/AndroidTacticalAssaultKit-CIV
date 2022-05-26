#ifndef TAKENGINEJNI_INTEROP_CORE_MANAGEDPROJECTION_H_INCLUDED
#define TAKENGINEJNI_INTEROP_CORE_MANAGEDPROJECTION_H_INCLUDED

#include <jni.h>

#include <core/Projection2.h>
#include <math/Point2.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Core {
            class ManagedProjection : public TAK::Engine::Core::Projection2
            {
            public :
                ManagedProjection(JNIEnv &env, jobject impl) NOTHROWS;
                ~ManagedProjection() NOTHROWS;
            public : // Projection
                int getSpatialReferenceID() const NOTHROWS;
                TAK::Engine::Util::TAKErr forward(TAK::Engine::Math::Point2<double> *proj, const TAK::Engine::Core::GeoPoint2 &geo) const NOTHROWS;
                TAK::Engine::Util::TAKErr inverse(TAK::Engine::Core::GeoPoint2 *geo, const TAK::Engine::Math::Point2<double> &proj) const NOTHROWS;
                double getMinLatitude() const NOTHROWS;
                double getMaxLatitude() const NOTHROWS;
                double getMinLongitude() const NOTHROWS;
                double getMaxLongitude() const NOTHROWS;
                bool is3D() const NOTHROWS;
            public :
                jobject impl;
            };
        }
    }
}

#endif
