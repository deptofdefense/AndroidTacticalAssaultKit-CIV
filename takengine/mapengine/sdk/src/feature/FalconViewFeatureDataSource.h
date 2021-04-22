#ifndef TAK_ENGINE_FEATURE_FALCONVIEWFEATUREDATASOURCE_H_INCLUDED
#define TAK_ENGINE_FEATURE_FALCONVIEWFEATUREDATASOURCE_H_INCLUDED

#include "feature/Feature2.h"
#include "feature/FeatureDataSource2.h"
#include "port/Platform.h"

namespace TAK {
namespace Engine {
namespace Feature {

class ENGINE_API FalconViewFeatureDataSource : public FeatureDataSource2 {
   public:
    FalconViewFeatureDataSource() NOTHROWS;

   public:
    virtual TAK::Engine::Util::TAKErr parse(ContentPtr &content, const char *file) NOTHROWS;
    virtual const char *getName() const NOTHROWS;
    virtual int parseVersion() const NOTHROWS;

   private:
};
}  // namespace Feature
}  // namespace Engine
}  // namespace TAK

#endif
