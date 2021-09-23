#ifndef TAKENGINEJNI_INTEROP_FORMATS_QUANTIZEDMESH_MANAGEDQMESOURCELAYER_H_INCLUDED
#define TAKENGINEJNI_INTEROP_FORMATS_QUANTIZEDMESH_MANAGEDQMESOURCELAYER_H_INCLUDED

#include <jni.h>

#include <map>

#include <formats/quantizedmesh/QMESourceLayer.h>
#include <thread/Mutex.h>

namespace TAKEngineJNI {
    namespace Interop {
        namespace Formats {
            namespace QuantizedMesh {
                class ManagedQMESourceLayer : public TAK::Engine::Formats::QuantizedMesh::QMESourceLayer
                {
                public:
                    ManagedQMESourceLayer(JNIEnv *env, jobject impl) NOTHROWS;
                    ~ManagedQMESourceLayer() NOTHROWS;
                public:
                    virtual TAK::Engine::Util::TAKErr getMinZoom(int *value) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr getMaxZoom(int *value) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr isLocalDirectoryValid(bool *value) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr getClosestLevel(int *value, double geodeticSpan) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr getMaxLevel(int *value) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr getDirectory(TAK::Engine::Port::String *dirname) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr getLevelDirName(TAK::Engine::Port::String *dirname, int z) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr getTileFilename(TAK::Engine::Port::String *filename, int x, int y, int z) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr isValid(bool *value) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr isEnabled(bool *value) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr hasTile(bool *value, int x, int y, int level) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr getAvailableExtents(TAK::Engine::Port::Vector<TAK::Engine::Formats::QuantizedMesh::TileExtents> *extents, int level) const NOTHROWS;
                    virtual TAK::Engine::Util::TAKErr startDataRequest(int x, int y, int z) NOTHROWS;

                public :
                    jobject impl;
                    TAK::Engine::Thread::Mutex mutex;
                };
            }
        }
    }
}

#endif
