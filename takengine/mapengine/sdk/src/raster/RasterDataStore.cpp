#include "raster/RasterDataStore.h"

using namespace atakmap::raster;

RasterDataStore::DatasetDescriptorCursor*
RasterDataStore::queryDatasets ()
    const
  { return queryDatasets (DatasetQueryParameters ()); }


std::size_t
RasterDataStore::queryDatasetsCount ()
    const
  { return queryDatasetsCount (DatasetQueryParameters ()); }
