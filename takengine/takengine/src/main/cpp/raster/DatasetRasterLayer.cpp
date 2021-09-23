
#include "raster/DatasetRasterLayerImpl.h"

using namespace atakmap::raster;

DatasetRasterLayerImpl::DatasetRasterLayerImpl (const char* name,
                                                RasterDataStore* dataStore,
                                                std::size_t datasetLimit)
: AbstractDataStoreRasterLayer (name, dataStore, RasterDataStore::DatasetQueryParameters()),
datasetLimit (datasetLimit)
{ }


atakmap::port::Iterator<const char *> *DatasetRasterLayerImpl::getSelectionOptions () const {
    return this->getDataStore()->getDatasetNames();
}
