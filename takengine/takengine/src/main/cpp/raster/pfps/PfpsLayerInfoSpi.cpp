#include "PfpsLayerInfoSpi.h"

#include <platformstl/filesystem/path.hpp>

#include "raster/MosaicDatasetDescriptor.h"
#include "raster/mosaic/MosaicUtils.h"
#include "raster/mosaic/MosaicDatabase.h"
#include "raster/gdal/GdalLayerInfo.h"
#include "util/IO.h"

namespace atakmap {
    namespace raster {
        namespace pfps {

            namespace {
                class CallbackPassthrough : public mosaic::BuildMosaicCallback
                {
                    DatasetDescriptor::CreationCallback *cb;
                public:
                    CallbackPassthrough(DatasetDescriptor::CreationCallback *cb) :
                        cb(cb)
                    {

                    }
                    void onProgressUpdate(int itemsProcessed) override
                    {
                        cb->setProgress(itemsProcessed);
                    }
                };
            }

            PfpsLayerInfoSpi::PfpsLayerInfoSpi()
                : DatasetDescriptor::Factory("pfps", 3)
            {

            }


            PfpsLayerInfoSpi::~PfpsLayerInfoSpi() NOTHROWS
            {

            }


            unsigned short PfpsLayerInfoSpi::getVersion() const NOTHROWS
            {
                return 1;
            }

            void PfpsLayerInfoSpi::isSupported(const char* filePath, PfpsLayerInfoSpi::CreationCallback *cb) const
            {
                cb->setProbeResult(mosaic::MosaicUtils::isMosaicDir(filePath, static_cast<int>(cb->getProbeLimit())));
            }

            PfpsLayerInfoSpi::DescriptorSet *PfpsLayerInfoSpi::createImpl(const char* filePath,
                                                        const char* workingDir,
                                                        PfpsLayerInfoSpi::CreationCallback *cb)
                                                        const
            {
                if (cb && cb->isProbeOnly()) {
                    isSupported(filePath, cb);
                    return nullptr;
                }

                if (!util::isDirectory(filePath))
                    return nullptr;

                mosaic::MosaicDatabase *database = nullptr;
                DatasetDescriptor *tsInfo = nullptr;
                try {
                    platformstl::basic_path<char> workingDirBP(workingDir);
                    platformstl::basic_path<char> mosaicDbFile(workingDirBP);
                    mosaicDbFile.push("mosaicdb.sqlite");
                    //Log.d(TAG, "creating mosaic database file " + mosaicDatabaseFile.getName() + " for "
                    //      + f.getName());
                    //long s = SystemClock.elapsedRealtime();
                    if (cb != nullptr){
                        CallbackPassthrough cbp(cb);
                        mosaic::MosaicUtils::buildMosaicDatabase(filePath, mosaicDbFile.c_str(), &cbp);
                    } else{
                        mosaic::MosaicUtils::buildMosaicDatabase(filePath, mosaicDbFile.c_str());
                    }
                    //long e = SystemClock.elapsedRealtime();

                    //Log.d(TAG, "mosaic scan file: " + f);
                    //Log.d(TAG, "Generated Mosaic Database in " + (e - s) + " ms");

                    database = new mosaic::ATAKMosaicDatabase();
                    database->open(mosaicDbFile.c_str());

                    std::map<std::string, mosaic::MosaicDatabase::Coverage *> coverageMap = database->getCoverages();
                    if (coverageMap.size() == 0){
                        delete database;
                        database = nullptr;
                        util::deletePath(mosaicDbFile.c_str());
                        return nullptr;
                    }

                    MosaicDatasetDescriptor::StringMap extraData;
                    platformstl::basic_path<char> sdb(workingDirBP);
                    sdb.push("spatialdb.sqlite");
                    extraData["spatialdb"] = util::getFileAsAbsolute(sdb.c_str()).c_str();

                    platformstl::basic_path<char> tilecacheDir(workingDirBP);
                    tilecacheDir.push("tilecache");
                    util::deletePath(tilecacheDir.c_str());
                    if (util::createDir(tilecacheDir.c_str()))
                        extraData["tilecacheDir"] = util::getFileAsAbsolute(tilecacheDir.c_str()).c_str();

                    MosaicDatasetDescriptor::StringVector types;
                    MosaicDatasetDescriptor::ResolutionMap resolutions;
                    MosaicDatasetDescriptor::CoverageMap coverages;
                    for (auto iter = coverageMap.begin(); iter != coverageMap.end(); ++iter) {
                        std::string type = iter->first;
                        types.push_back(type.c_str());
                        mosaic::MosaicDatabase::Coverage *c = iter->second;
                        resolutions[type.c_str()] = std::pair<double, double>(c->minGSD, c->maxGSD);
                        coverages[type.c_str()] = c->geometry;
                    }

                    tsInfo = new MosaicDatasetDescriptor(util::getFileName(filePath).c_str(),
                                                         gdal::GdalLayerInfo::getURI(filePath).c_str(),
                                                         getStrategy(),
                                                         "native-mosaic",
                                                         mosaicDbFile.c_str(),
                                                         database->getType(),
                                                         types,
                                                         resolutions,
                                                         coverages,
                                                         4326, // SRID of Equirectangular map projection
                                                         false,
                                                         workingDir,
                                                         extraData);

                    delete database;
                    database = nullptr;

                    std::unique_ptr<DescriptorSet> ret(new DescriptorSet);
                    ret->insert(tsInfo);
                    return ret.release();

                } catch (...) {
                    if (database)
                        delete database;
                    throw;
                }
            }



        }
    }
}

