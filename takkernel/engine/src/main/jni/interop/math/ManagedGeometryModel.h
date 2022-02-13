#ifndef TAKENGINEJNI_INTEROP_MATH_MANAGEDGEOMETRYMODEL_H_INCLUDED
#define TAKENGINEJNI_INTEROP_MATH_MANAGEDGEOMETRYMODEL_H_INCLUDED

#include <jni.h>

#include <math/GeometryModel2.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Math {
            class ManagedGeometryModel : public TAK::Engine::Math::GeometryModel2
            {
            public :
                ManagedGeometryModel(JNIEnv &env, jobject impl) NOTHROWS;
                ~ManagedGeometryModel() NOTHROWS;
            public : // GeometryModel2
                bool intersect(TAK::Engine::Math::Point2<double> *value, const TAK::Engine::Math::Ray2<double> &ray) const;
                TAK::Engine::Math::GeometryModel2::GeometryClass getGeomClass() const;
                void clone(TAK::Engine::Math::GeometryModel2Ptr &value) const;
            public :
                jobject impl;
            };
        }
    }
}

#endif
