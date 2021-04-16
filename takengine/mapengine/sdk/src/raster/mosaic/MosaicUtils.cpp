#include "MosaicUtils.h"
#include "raster/PrecisionImageryFactory.h"
#include "raster/pfps/PfpsUtils.h"
#include "raster/gdal/GdalDatasetProjection.h"
#include "raster/gdal/GdalLayerInfo.h"
#include "raster/DatasetDescriptor.h"
#include "raster/ImageDatasetDescriptor.h"
#include "raster/PrecisionImageryFactory.h"
#include "util/IO.h"
#include "math/Utils.h"

using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Util;

namespace atakmap {
    namespace raster {
        namespace mosaic {

            namespace {
                const int INTERVAL = 50;
                const char *SUBDIR_NITF = "nitf";
                const char *SUBDIR_PRI = "pri";
                const char *SUBDIR_PFI = "pfi";
                const char *SUBDIR_GEOTIFF = "geotiff";
                const char *SUBDIR_MRSID = "mrsid";
                const char *SUBDIR_RPF = "rpf";

                const char *SUBDIRS[] = {
                    SUBDIR_NITF,
                    SUBDIR_PRI,
                    SUBDIR_PFI,
                    SUBDIR_GEOTIFF,
                    SUBDIR_MRSID,
                    nullptr
                };

                bool isInSubdirs(const char *s) {
                    int i = 0;
                    while (SUBDIRS[i]) {
                        if (strcmp(SUBDIRS[i], s) == 0)
                            return true;
                        i++;
                    }
                    return false;
                }

            }


            bool MosaicUtils::isMosaicDir(const char *f, int limit)
            {
                if (!util::isDirectory(f))
                    return false;
                if (pfps::PfpsUtils::isPfpsDataDir(f))
                    return true;
                // didn't look like PFPS, check for our directories
                int hits = 0;
                std::vector<std::string> children = util::getDirContents(f);

                int checkLimit = math::min((int)children.size(), limit);
                for (int i = 0; i < checkLimit; i++) {
                    std::string clower = util::toLowerCase(util::getFileName(children[i].c_str()));
                    if (isInSubdirs(clower.c_str())) {
                        if (!util::isDirectory(children[i].c_str()))
                            continue;
                        if (util::getDirContents(children[i].c_str()).size() > 0)
                            hits++;
                    }
                }

                return (hits > 0);

            }

            void MosaicUtils::buildMosaicDatabase(const char *mosaicDir, const char *databaseFile,
                                                  BuildMosaicCallback *callback)
            {
                ATAKMosaicDatabase database;

                int count = 0;

                try {
                    database.open(databaseFile);
                    database.beginTransaction();

                    std::vector<std::string> subdirs = util::getDirContents(mosaicDir);
                    for (size_t i = 0; i < subdirs.size(); i++) {
                        if (!util::isDirectory(subdirs[i].c_str()))
                            continue;
                        std::string lowerFname = util::toLowerCase(util::getFileName(subdirs[i].c_str()));
                        const char *clowerFname = lowerFname.c_str();
                        if (strcmp(SUBDIR_RPF, clowerFname) == 0) {
                            pfps::PfpsUtils::createRpfDataDatabase(&database, subdirs[i].c_str());
                        } else if (strcmp(SUBDIR_MRSID, clowerFname) == 0) {
#if 0
                            // mr. sid disabled and fallback to generic for now per Chris
                            buildMrsidSubDatabase(&database, subdirs[i].c_str(), mosaicDir, &count, callback);
#else
                            buildGenericSubDatabase(&database, subdirs[i].c_str(), mosaicDir, &count, callback);
#endif
                        } else if (isInSubdirs(clowerFname)) {
                            buildGenericSubDatabase(&database, subdirs[i].c_str(), mosaicDir, &count, callback);
                        }
                    }

                    database.setTransactionSuccessful();
                    database.endTransaction();
                    database.close();
                } catch (...) {
                    database.endTransaction();
                    database.close();
                    throw;
                }
            }

            void MosaicUtils::buildGenericSubDatabase(ATAKMosaicDatabase *database, const char *subdir,
                                                      const char *relativeTo, int *count,
                                                      BuildMosaicCallback *callback)
            {
                std::string genericType = util::toLowerCase(util::getFileName(subdir));
                bool nitfChecks = genericType.compare("nitf") == 0;
                std::vector<std::string> children = util::getDirContents(subdir);
                int width;
                int height;
                math::PointD scratch(0, 0);
                core::GeoPoint ul;
                core::GeoPoint ur;
                core::GeoPoint lr;
                core::GeoPoint ll;
                std::string type;
                bool isDefined;
                //String path;
                for (size_t i = 0; i < children.size(); i++) {
                    int tempCount = *count;
                    *count += 1;
                    if (callback != nullptr && (tempCount % INTERVAL) == 0){
                        callback->onProgressUpdate(tempCount);
                    }

                    if (util::isDirectory(children[i].c_str())) {
                        //Log.w(TAG, "Skipping " + subdir.getName() + " subdirectory, " + children[i]);
                        continue;
                    }

                    width = 0;
                    height = 0;
                    isDefined = false;

                    std::string path = util::getFileAsAbsolute(children[i].c_str());
                    if (nitfChecks) {
                        if (PrecisionImageryFactory_isSupported(path.c_str()) == TE_Ok) {
                            do {
                                TAKErr code(TE_Ok);
                                PrecisionImageryPtr image(nullptr, nullptr);
                                code = PrecisionImageryFactory_create(image, path.c_str());
                                TE_CHECKBREAK_CODE(code);
                                ImageInfo info;
                                code = image->getInfo(&info);
                                TE_CHECKBREAK_CODE(code);

                                ul.set(info.upperLeft.latitude, info.upperLeft.longitude);
                                ur.set(info.upperRight.latitude, info.upperRight.longitude);
                                lr.set(info.lowerRight.latitude, info.lowerRight.longitude);
                                ll.set(info.lowerLeft.latitude, info.lowerLeft.longitude);

                                type = info.type;
                                width = info.width;
                                height = info.height;

                                isDefined = true;
                            } while (false);
                        }
                    }
                    if (!isDefined) {
                        GDALDataset *dataset = nullptr;
                        core::Projection *proj = nullptr;
                        try {
                            dataset = (GDALDataset *)GDALOpen(path.c_str(), GA_ReadOnly);
                            if (dataset == nullptr) {
                                //Log.w(TAG, "Unable to open mosaic frame: " + children[i]);
                                continue;
                            }

                            proj = gdal::GdalDatasetProjection::getInstance(dataset);

                            width = dataset->GetRasterXSize();
                            height = dataset->GetRasterYSize();

                            scratch.x = 0;
                            scratch.y = 0;
                            proj->inverse(&scratch, &ul);
                            scratch.x = width;
                            scratch.y = 0;
                            proj->inverse(&scratch, &ur);
                            scratch.x = width;
                            scratch.y = height;
                            proj->inverse(&scratch, &lr);
                            scratch.x = 0;
                            scratch.y = height;
                            proj->inverse(&scratch, &ll);



                            type = genericType;
                        } catch (...) {
                            if (proj)
                                delete proj;
                            if (dataset)
                                GDALClose(dataset);
                            throw;
                        }
                        delete proj;
                        GDALClose(dataset);
                    }
                    double gsd = DatasetDescriptor::computeGSD(width, height, ul, ur, lr, ll);
                    database->insertRow(util::computeRelativePath(relativeTo, children[i].c_str()),
                                       type,
                                       ul,
                                       ur,
                                       lr,
                                       ll,
                                       gsd * (double)(1 << DEFAULT_NUM_LEVELS),
                                       gsd,
                                       width,
                                       height);
                }
            }
            
            
            void MosaicUtils::buildMrsidSubDatabase(ATAKMosaicDatabase *database, const char *subdir,
                                                    const char *relativeTo, int *count,
                                                    BuildMosaicCallback *callback)
            {
                std::vector<std::string> children = util::getDirContents(subdir);
                int width;
                int height;
                core::GeoPoint ul;
                core::GeoPoint ur;
                core::GeoPoint lr;
                core::GeoPoint ll;
                for (size_t i = 0; i < children.size(); i++) {
                    int tempCount = *count;
                    *count += 1;
                    if (callback != nullptr && (tempCount % INTERVAL) == 0){
                        callback->onProgressUpdate(tempCount);
                    }

                    if (util::isDirectory(children[i].c_str())) {
                        //Log.w(TAG, "Skipping " + subdir.getName() + " subdirectory, " + children[i]);
                        continue;
                    }

                    DatasetDescriptor *info = nullptr;
#if 0
                    try {
                        info = MrsidLayerInfoSpi.getLayerInfo(children[i]);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting mrsid LayerInfo for '" + children[i].getAbsolutePath()
                              + "'");
                        android.util.Log.e(TAG, "error: ", e);
                    }
#endif
                    if (info != nullptr) {
                        auto *image = (ImageDatasetDescriptor *)info;

                        width = static_cast<int>(image->getWidth());
                        height = static_cast<int>(image->getHeight());
                        ul = image->getUpperLeft();
                        ur = image->getUpperRight();
                        lr = image->getLowerRight();
                        ll = image->getLowerLeft();

                        TAK::Engine::Port::String type = info->getImageryTypes()[0];
                        double minGsd = info->getMinResolution(type.get());
                        double maxGsd = info->getMaxResolution(type.get());
                        database->insertRow(util::computeRelativePath(relativeTo, children[i].c_str()),
                                            type.get(),
                                            ul, ur, lr, ll,
                                            minGsd,
                                            maxGsd,
                                            width, height);
                    }
                }
            }
        }
    }
}
