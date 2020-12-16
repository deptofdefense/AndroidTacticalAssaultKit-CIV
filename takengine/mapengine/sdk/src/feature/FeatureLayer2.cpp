#include "feature/FeatureLayer2.h"

using namespace TAK::Engine::Feature;

FeatureLayer2::FeatureLayer2(const char* layerName, FeatureDataStore2Ptr &&dataStore_) NOTHROWS :
    AbstractLayer(layerName),
    ServiceManagerBase2(AbstractLayer::getMutex()),
    dataStore(std::move(dataStore_))
{}
            
FeatureDataStore2& FeatureLayer2::getDataStore() const NOTHROWS
{
    return *this->dataStore;
}
