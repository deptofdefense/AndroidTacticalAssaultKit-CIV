#include "raster/apass/ApassLayerInfoSpi.h"

#include <memory>
#include <stdexcept>

#include <platformstl/filesystem/filesystem_traits.hpp>
#include <platformstl/filesystem/path.hpp>

#include "raster/tilereader/TileReader.h"
#include "raster/mosaic/MosaicDatabase.h"
#include "raster/mosaic/ATAKMosaicDatabase.h"
#include "raster/mosaic/MosaicUtils.h"
#include "raster/MosaicDatasetDescriptor.h"
#include "raster/PrecisionImageryFactory.h"
#include "util/Distance.h"
#include "util/IO.h"
#include "math/Utils.h"
#include <platformstl/filesystem/path.hpp>

namespace atakmap {
    namespace raster {
        namespace apass {
            namespace {
                const double NOMINAL_METERS_PER_DEGREE = util::distance::calculateRange(core::GeoPoint(0.5, 0), core::GeoPoint(-0.5, 0));

            }

            const char *ApassLayerInfoSpi::IMAGES_TABLE_COLUMN_NAMES[] = {
                "path",
                "corner_ul_y",
                "corner_ul_x",
                "corner_ur_y",
                "corner_ur_x",
                "corner_lr_y",
                "corner_lr_x",
                "corner_ll_y",
                "corner_ll_x",
                "gsd",
                "targetting",
                "grg",
                "width",
                "height",
                nullptr
            };


            ApassLayerInfoSpi::ApassLayerInfoSpi()
              : DatasetDescriptor::Factory ("apass", 0)
            {

            }

            ApassLayerInfoSpi::~ApassLayerInfoSpi() NOTHROWS
            {

            }

            DatasetDescriptor::DescriptorSet*
            ApassLayerInfoSpi::createImpl (const char* filePath,
                                           const char* workingDir,
                                           CreationCallback* callback)
                const
            {
                if (callback && callback->isProbeOnly()) {
                    isSupported(filePath, callback);
                    return nullptr;
                }

                platformstl::basic_path<char> apassDatabaseFile(filePath);
                MosaicDatasetDescriptor::StringMap extraData;
                platformstl::basic_path<char> atakDbFile(workingDir);
                atakDbFile.push("mosaicdb.sqlite");

                if (!convertDatabase(apassDatabaseFile.c_str(), atakDbFile.c_str(), callback))
                    throw std::invalid_argument("");

                platformstl::basic_path<char> dbPath(workingDir);
                dbPath.push("spatialdb.sqlite");
                extraData["spatialdb"] = dbPath.make_absolute().c_str();

                platformstl::basic_path<char> tilecacheDir(workingDir);
                tilecacheDir.push("tilecache");
                tilecacheDir = tilecacheDir.make_absolute();
                platformstl::filesystem_traits<char>::remove_directory(tilecacheDir.c_str());
                if (util::createDir(tilecacheDir.c_str()))
                    extraData["tilecacheDir"] = tilecacheDir.c_str();

                std::unique_ptr<mosaic::MosaicDatabase> database
                    (new mosaic::ATAKMosaicDatabase());
                database->open(atakDbFile.c_str());

                std::map<std::string, mosaic::MosaicDatabase::Coverage *> dbCoverages = database->getCoverages();
                if (dbCoverages.size() == 0)
                    throw std::invalid_argument("");

                MosaicDatasetDescriptor::ResolutionMap resolutions;
                MosaicDatasetDescriptor::CoverageMap coverages;
                MosaicDatasetDescriptor::StringVector imageTypes;

                std::map<std::string, mosaic::MosaicDatabase::Coverage *>::iterator iter;
                for (iter = dbCoverages.begin(); iter != dbCoverages.end(); ++iter) {
                    mosaic::MosaicDatabase::Coverage *coverage = iter->second;
                    resolutions[iter->first.c_str()] = std::pair<double, double>(coverage->minGSD, coverage->maxGSD);
                    coverages[iter->first.c_str()] = coverage->geometry;
                    imageTypes.push_back(iter->first.c_str());
                }
                platformstl::basic_path<char> apassDBFileParent = apassDatabaseFile.pop();
                std::unique_ptr<DescriptorSet> ret (new DescriptorSet);
                ret->insert (new MosaicDatasetDescriptor(apassDBFileParent.get_file(),
                                                         // XXX
                                                         //GdalLayerInfo.getURI(f.getParentFile()).toString(),
                                                         nullptr,
                                                         getStrategy(),
                                                         "native-mosaic",
                                                         atakDbFile.c_str(),
                                                         database->getType(),
                                                         imageTypes,
                                                         resolutions,
                                                         coverages,
                                                         4326, // SRID of Equirectangular map projection
                                                         false,
                                                         workingDir,
                                                         extraData));
                return ret.release ();
            }

            unsigned short
                ApassLayerInfoSpi::getVersion()
                const
                NOTHROWS
            {
                return 1;
            }


            void
                ApassLayerInfoSpi::isSupported(const char* filePath, CreationCallback*cb)
                const
            {
                // If this file is a database, and it has a table with the right columns
                // and at least one row, then it will probably be able to produce a layer
                // from this Spi.

                //if (!Databases.isSQLiteDatabase(file.getAbsolutePath())){
                //    return false;
                //}

                db::Database *apassDb = nullptr;
                db::Cursor *result = nullptr;
                std::vector<std::string> colNames = getColNames();
                try {
                    apassDb = db::openDatabase(filePath);


                    result = apassDb
                        ->query("images", &colNames, nullptr, nullptr,
                        nullptr, nullptr, nullptr, nullptr);

                    bool hasRes = result->moveToNext();
                    delete result;
                    delete apassDb;
                    cb->setProbeResult(hasRes);
                } catch (...) {
                    if (result != nullptr) {
                        delete result;
                    }
                    if (apassDb != nullptr){
                        delete apassDb;
                    }
                    cb->setError("Error probing apass file");
                    cb->setProbeResult(false);
                }
            }


            bool ApassLayerInfoSpi::convertDatabase(const char *apassDbPath, const char *atakDbPath, DatasetDescriptor::CreationCallback *callback)
            {
                db::Database *apassDb = nullptr;
                platformstl::basic_path<char> apassDbPathFile(atakDbPath);
                apassDbPathFile = apassDbPathFile.make_absolute();
                platformstl::basic_path<char> apassDataDir(apassDbPath);
                apassDataDir = apassDataDir.pop();

                auto *atakDb = new mosaic::ATAKMosaicDatabase();
                atakDb->create(atakDbPath);
                atakDb->beginTransaction();

                db::Cursor *result = nullptr;
                try {
                    apassDb = db::openDatabase(apassDbPathFile.c_str());

                    // Set<String> s = Databases.getColumnNames(apassDb, "images");
                    // if(s == null || !s.containsAll(IMAGES_TABLE_COLUMN_NAMES))
                    // return false;
                    std::vector<std::string> colNames = getColNames();
                    result = apassDb->
                        query("images", &colNames, nullptr, nullptr,
                        nullptr, nullptr, nullptr, nullptr);

                    platformstl::basic_path<char> frame;
                    core::GeoPoint ul;
                    core::GeoPoint ur;
                    core::GeoPoint lr;
                    core::GeoPoint ll;
                    int colPath = -1;
                    int colULLat = -1;
                    int colULLng = -1;
                    int colURLat = -1;
                    int colURLng = -1;
                    int colLRLat = -1;
                    int colLRLng = -1;
                    int colLLLat = -1;
                    int colLLLng = -1;
                    int colGsd = -1;
                    int colTargetting = -1;
                    int colGrg = -1;
                    int colWidth = -1;
                    int colHeight = -1;

                    platformstl::basic_path<char> path;
                    double apassGsd;
                    int width;
                    int height;
                    bool grg;
                    bool targetting;
                    std::string type;
                    double gsd;
                    std::string frameName;
                    int count = 0;

                    while (result->moveToNext()) {
                        if (callback != nullptr && (count % 100) == 0){
                            callback->setProgress(count);
                        }
                        count += 1;

                        if (colPath == -1) {
                            colPath = static_cast<int>(result->getColumnIndex("path"));
                            colULLat = static_cast<int>(result->getColumnIndex("corner_ul_y"));
                            colULLng = static_cast<int>(result->getColumnIndex("corner_ul_x"));
                            colURLat = static_cast<int>(result->getColumnIndex("corner_ur_y"));
                            colURLng = static_cast<int>(result->getColumnIndex("corner_ur_x"));
                            colLRLat = static_cast<int>(result->getColumnIndex("corner_lr_y"));
                            colLRLng = static_cast<int>(result->getColumnIndex("corner_lr_x"));
                            colLLLat = static_cast<int>(result->getColumnIndex("corner_ll_y"));
                            colLLLng = static_cast<int>(result->getColumnIndex("corner_ll_x"));
                            colGsd = static_cast<int>(result->getColumnIndex("gsd"));
                            colTargetting = static_cast<int>(result->getColumnIndex("targetting"));
                            colGrg = static_cast<int>(result->getColumnIndex("grg"));
                            colWidth = static_cast<int>(result->getColumnIndex("width"));
                            colHeight = static_cast<int>(result->getColumnIndex("height"));
                        }

                        path = result->getString(colPath);
                        frame = platformstl::basic_path<char>(apassDataDir);
                        frame = frame.push(path);
                        if (!frame.exists())
                            continue;

                        ul.set(result->getDouble(colULLat), result->getDouble(colULLng));
                        ur.set(result->getDouble(colURLat), result->getDouble(colURLng));
                        lr.set(result->getDouble(colLRLat), result->getDouble(colLRLng));
                        ll.set(result->getDouble(colLLLat), result->getDouble(colLLLng));

                        apassGsd = result->getDouble(colGsd);
                        grg = (result->getInt(colGrg) == 1);
                        targetting = (result->getInt(colTargetting) == 1);
                        width = result->getInt(colWidth);
                        height = result->getInt(colHeight);

                        gsd = NAN;

                        if (grg) {
                            type = "GRG";
                        } else if (targetting) {
                            type = "PFI";

                            // don't trust the databases registration; compute the
                            // corner coordinates ourselves based off of the image
                            // we'll just default to what was in the database if
                            // we fail
                            do {
                                TAK::Engine::Util::TAKErr code(TAK::Engine::Util::TE_Ok);
                                TAK::Engine::Raster::PrecisionImageryPtr image(nullptr, nullptr);
                                code = TAK::Engine::Raster::PrecisionImageryFactory_create(image, frame.c_str());
                                TE_CHECKBREAK_CODE(code);
                                TAK::Engine::Raster::ImageInfo pfi;
                                code = image->getInfo(&pfi);
                                TE_CHECKBREAK_CODE(code);

                                ul.set(pfi.upperLeft.latitude, pfi.upperLeft.longitude);
                                ur.set(pfi.upperRight.latitude, pfi.upperRight.longitude);
                                lr.set(pfi.lowerRight.latitude, pfi.lowerRight.longitude);
                                ll.set(pfi.lowerLeft.latitude, pfi.lowerLeft.longitude);
                            } while (false);
                            
#if 0
                        } else {
                            type = null;

                            frameName = frame.getName();
                            if (frameName.length() == 12) {
                                PfpsMapType pfpsType = PfpsUtils.getMapType(frameName);
                                if (pfpsType != null) {
                                    type = pfpsType.folderName;
                                    gsd = pfpsType.scale;
                                    if (pfpsType.scaleUnits == PfpsMapType.SCALE_UNIT_SCALE)
                                        gsd = PfpsUtils.cadrgScaleToCibResolution(1.0d / gsd);
                                }
                            }

                            if (type == null && frameName.lastIndexOf('.') > 0) {
                                type = frameName.substring(frameName.lastIndexOf('.') + 1);
                            } else if (type == null) {
                                type = "apass-chartType"
                                    + result.getInt(result.getColumnIndex("chartType"));
                            }
#endif
                        }

                        if (isnan(gsd))
                            gsd = apassGsd * NOMINAL_METERS_PER_DEGREE;

                        const int numLevels = math::min(mosaic::MosaicUtils::DEFAULT_NUM_LEVELS, tilereader::TileReader::getNumResolutionLevels(width, height, 512, 512));
                        atakDb->insertRow(result->getString(colPath),
                                         type,
                                         ul,
                                         ur,
                                         lr,
                                         ll,
                                         gsd * (double)(1 << numLevels),
                                         gsd,
                                         width,
                                         height);
                    }

                    atakDb->setTransactionSuccessful();

                    if (result != nullptr) {
                        delete result;
                        result = nullptr;
                    }
                    atakDb->endTransaction();
                    if (apassDb != nullptr) {
                        delete apassDb;
                        apassDb = nullptr;
                    }
                    atakDb->close();
                    delete atakDb;
                    atakDb = nullptr;

                    return true;
                } catch (...) {
                    if (result != nullptr)
                        delete result;
                    atakDb->endTransaction();
                    if (apassDb != nullptr)
                        delete apassDb;
                    atakDb->close();
                    delete atakDb;
                    throw;
                }
            }

            std::vector<std::string> ApassLayerInfoSpi::getColNames() {
                std::vector<std::string> colNames;
                const char **curCol = IMAGES_TABLE_COLUMN_NAMES;
                while (curCol) {
                    colNames.push_back(*curCol);
                }
                return colNames;
            }
        }
    }
}
