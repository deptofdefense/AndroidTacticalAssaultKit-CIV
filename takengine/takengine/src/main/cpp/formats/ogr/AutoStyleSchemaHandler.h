#ifndef TAK_ENGINE_FORMATS_OGR_AUTOSTYLESCHEMA_H_INCLUDED
#define TAK_ENGINE_FORMATS_OGR_AUTOSTYLESCHEMA_H_INCLUDED

#include <map>

#include "feature/Style.h"
#include "formats/ogr/OGRFeatureDataStore.h"
#include "thread/Mutex.h"
#include "util/Memory.h"

namespace TAK {
    namespace Engine {
        namespace Formats {
            namespace OGR {
                class ENGINE_API AutoStyleSchemaHandler : public OGRFeatureDataStore::SchemaHandler
                {
                private :
                    struct LayerStyle
                    {
                        Feature::StylePtr_const point{ Feature::StylePtr_const(nullptr, nullptr) };
                        Feature::StylePtr_const linestring{ Feature::StylePtr_const(nullptr, nullptr) };
                        Feature::StylePtr_const polygon{ Feature::StylePtr_const(nullptr, nullptr) };
                    };
                public :
                    AutoStyleSchemaHandler() NOTHROWS;
                    AutoStyleSchemaHandler(const unsigned int *palette, const std::size_t paletteSize) NOTHROWS;
                    ~AutoStyleSchemaHandler() NOTHROWS;
                public :
                    virtual Util::TAKErr ignoreLayer(bool *value, OGRLayerH layer) const NOTHROWS;
                    virtual bool styleRequiresAttributes() const NOTHROWS;
                    virtual Util::TAKErr getFeatureStyle(Feature::StylePtr_const &value, OGRLayerH layer, OGRFeatureH feature, const atakmap::util::AttributeSet &attribs) NOTHROWS;
                    virtual Util::TAKErr getFeatureName(Port::String &value, OGRLayerH layer, OGRFeatureH feature, const atakmap::util::AttributeSet &attribs) NOTHROWS;
                    virtual Util::TAKErr getFeatureSetName(Port::String &value, OGRLayerH layer) NOTHROWS;
                private :
                    std::map<std::string, TAK::Engine::Port::String> nameColumn;
                    std::map<std::string, LayerStyle> layerStyle;
                    Thread::Mutex mutex;
                    Util::array_ptr<unsigned int> palette;
                    Util::array_ptr<bool> paletteInUse;
                    const std::size_t paletteSize;
                };
            }
        }
    }
}
#endif
