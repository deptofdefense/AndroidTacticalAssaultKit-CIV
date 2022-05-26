#ifndef TAKENGINEJNI_INTEROP_ELEVATION_MANAGEDELEVATIONSOURCE_H_INCLUDED
#define TAKENGINEJNI_INTEROP_ELEVATION_MANAGEDELEVATIONSOURCE_H_INCLUDED

#include <jni.h>

#include <map>

#include <elevation/ElevationSource.h>
#include <thread/Mutex.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Elevation {
        class ManagedElevationSource : public TAK::Engine::Elevation::ElevationSource
            {
            public:
                ManagedElevationSource(JNIEnv *env, jobject impl) NOTHROWS;
                ~ManagedElevationSource() NOTHROWS;
            public:
                virtual const char *getName() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr query(TAK::Engine::Elevation::ElevationChunkCursorPtr &value, const TAK::Engine::Elevation::ElevationSource::QueryParameters &params) NOTHROWS;
                virtual TAK::Engine::Feature::Envelope2 getBounds() const NOTHROWS;
                virtual TAK::Engine::Util::TAKErr addOnContentChangedListener(TAK::Engine::Elevation::ElevationSource::OnContentChangedListener *l) NOTHROWS;
                virtual TAK::Engine::Util::TAKErr removeOnContentChangedListener(TAK::Engine::Elevation::ElevationSource::OnContentChangedListener *l) NOTHROWS;
            public :
                jobject impl;
                TAK::Engine::Port::String name;
                std::map<const TAK::Engine::Elevation::ElevationSource::OnContentChangedListener *, jobject> listeners;
                TAK::Engine::Thread::Mutex mutex;
            };
        }
    }
}

#endif
