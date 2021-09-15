#ifndef TAK_ENGINE_FORMATS_PFPS_DRSDRIVERDEFINITION_H_INCLUDED
#define TAK_ENGINE_FORMATS_PFPS_DRSDRIVERDEFINITION_H_INCLUDED

#include "feature/DefaultDriverDefinition2.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace Pfps {
class ENGINE_API DrsDriverDefinition : public TAK::Engine::Feature::DefaultDriverDefinition2 {
   public:
    DrsDriverDefinition() NOTHROWS;

   public:
    Util::TAKErr setGeometry(std::unique_ptr<atakmap::feature::FeatureDataSource::FeatureDefinition>& featureDefinition, const OGRFeature&,
                             const OGRGeometry&) const NOTHROWS override;
    Util::TAKErr getStyle(Port::String& value, const OGRFeature&, const OGRGeometry&) NOTHROWS override;

   private:
    Util::TAKErr getStyleImpl(TAK::Engine::Port::String& value, const OGRFeature& feature, const OGRGeometry& g) NOTHROWS;
};
}  // namespace Pfps
}  // namespace Formats
}  // namespace Engine
}  // namespace TAK

#endif
