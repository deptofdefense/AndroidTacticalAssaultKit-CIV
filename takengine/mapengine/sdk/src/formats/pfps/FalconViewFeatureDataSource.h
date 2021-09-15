#ifndef TAK_ENGINE_FORMATS_PFPS_FALCONVIEWFEATUREDATASOURCE_H_INCLUDED
#define TAK_ENGINE_FORMATS_PFPS_FALCONVIEWFEATUREDATASOURCE_H_INCLUDED

#include "feature/Feature2.h"
#include "feature/FeatureDataSource2.h"
#include "port/Platform.h"

namespace TAK {
namespace Engine {
namespace Formats {
namespace PFPS {

class ENGINE_API FalconViewFeatureDataSource : public TAK::Engine::Feature::FeatureDataSource2 {
   public:
    FalconViewFeatureDataSource() NOTHROWS;

   public:
    virtual TAK::Engine::Util::TAKErr parse(ContentPtr &content, const char *file) NOTHROWS;
    virtual const char *getName() const NOTHROWS;
    virtual int parseVersion() const NOTHROWS;

   private:
};
}  // namespace PFPS
}  // namespace Feature
}  // namespace Engine
}  // namespace TAK

#endif
