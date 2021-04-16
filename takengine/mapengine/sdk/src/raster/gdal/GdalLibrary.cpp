#include "GdalLibrary.h"

#include <platformstl/filesystem/path.hpp>

#include "util/IO.h"

using namespace TAK::Engine::Thread;

namespace atakmap {
    namespace raster {
        namespace gdal {

            Mutex GdalLibrary::mutex;
            bool GdalLibrary::initialized = false;
            bool GdalLibrary::initSuccess = false;
            OGRSpatialReference *GdalLibrary::EPSG_4326 = nullptr;


            namespace {
                bool initImpl(const char *gdalDir)
                {
                    // NOTE: AllRegister automatically loads the shared libraries
                    GDALAllRegister();
                    CPLSetConfigOption("GDAL_DATA", util::getFileAsAbsolute(gdalDir).c_str());
                    // debugging
                    CPLSetConfigOption("CPL_DEBUG", "OFF");
                    CPLSetConfigOption("CPL_LOG_ERRORS", "ON");

                    CPLSetConfigOption("GDAL_DISABLE_READDIR_ON_OPEN", "TRUE");

                    if (!util::pathExists(gdalDir))
                        util::createDir(gdalDir);
                    platformstl::basic_path<char> gdalDataVer(gdalDir);
                    gdalDataVer.push("gdal.version");
                    bool unpackData = true;
                    if (util::pathExists(gdalDataVer.c_str())) {
                        util::FileInput input;
                        int devVersion = 0;
                        try {
                            input.open(gdalDataVer.c_str());
                            devVersion = input.readAsciiInt(util::getFileSize(gdalDataVer.c_str()));
                        } catch (util::IO_Error &) {
                        }
                        try { input.close(); } catch (util::IO_Error &) {}

                        int libVersion = strtol(GDALVersionInfo("VERSION_NUM"), nullptr, 10);

                        unpackData = (libVersion > devVersion);
                    }

#if 0
                    if (unpackData) {
                        FileOutputStream fileOutputStream = null;

                        InputStream inputStream = null;
                        URL url = null;

                        // obtain the data files listing
                        url = GdalLibrary.class.getClassLoader().getResource("gdal/data/gdaldata.files");
                        if (url == null)
                            return;

                        Collection<String> dataFiles = new LinkedList<String>();

                        InputStreamReader inputStreamReader;
                        BufferedReader bufferedReader;
                        try {
                            inputStream = url.openStream();

                            inputStreamReader = new InputStreamReader(inputStream);
                            bufferedReader = new BufferedReader(inputStreamReader);
                            String line = bufferedReader.readLine();
                            while (line != null) {
                                dataFiles.add(line);
                                line = bufferedReader.readLine();
                            }
                        } finally {
                            if (inputStream != null)
                                try {
                                inputStream.close();
                            } catch (IOException ignored) {
                            }
                        }

                        Iterator<String> iter = dataFiles.iterator();
                        String dataFileName;
                        byte[] transfer = new byte[8192];
                        int transferSize;
                        while (iter.hasNext()) {
                            dataFileName = iter.next();
                            url = GdalLibrary.class.getClassLoader().getResource("gdal/data/" + dataFileName);
                            if (url == null) {
                                // XXX - warn file not found
                                continue;
                            }
                            inputStream = null;
                            fileOutputStream = null;
                            try {
                                inputStream = url.openStream();
                                fileOutputStream = new FileOutputStream(new File(gdalDir, dataFileName));

                                do {
                                    transferSize = inputStream.read(transfer);
                                    if (transferSize > 0)
                                        fileOutputStream.write(transfer, 0, transferSize);
                                } while (transferSize >= 0);
                            } finally {
                                if (fileOutputStream != null)
                                    fileOutputStream.close();
                                if (inputStream != null)
                                    inputStream.close();
                            }

                        }

                        // write out the gdal version that the data files correspond to
                        try {
                            fileOutputStream = new FileOutputStream(gdalDataVer);
                            fileOutputStream.write(gdal.VersionInfo("VERSION_NUM").getBytes());
                        } catch (IOException ignored) {
                            // not really a major issue
                        } finally {
                            if (fileOutputStream != null)
                                try {
                                fileOutputStream.close();
                            } catch (IOException ignored) {
                            }
                        }
                    }
#endif

                    GdalLibrary::EPSG_4326 = new OGRSpatialReference();
                    GdalLibrary::EPSG_4326->importFromEPSG(4326);

                    return true;
                }
            }

            bool GdalLibrary::init(const char *gdalDataDir)
            {
                bool ret;
                mutex.lock();
                if (!initialized) {
                    try {
                        initSuccess = initImpl(gdalDataDir);
                    } catch (...) {
                    }
                    initialized = true;
                }
                ret = initSuccess;
                mutex.unlock();
                return ret;
            }

            bool GdalLibrary::isInitialized()
            {
                mutex.lock();
                bool ret = initialized;
                mutex.unlock();
                return ret;
            }

            int GdalLibrary::getSpatialReferenceID(OGRSpatialReference *srs)
            {
                if (srs == nullptr)
                    return -1;

                const char *value = srs->GetAttrValue("AUTHORITY", 0);
                if (value == nullptr || strcmp(value, "EPSG") != 0)
                    return -1;

                value = srs->GetAttrValue("AUTHORITY", 1);
                if (value == nullptr)
                    return -1;

                char *end = nullptr;
                long v = strtol(value, &end, 10);
                if ((v == 0 && end == value) || *end != '\0' || ((v == LONG_MAX || v == LONG_MIN) && errno == ERANGE) || v > INT_MAX || v < INT_MIN)
                    return -1;

                return (int)v;
            }
            
            std::shared_ptr<atakmap::raster::tilereader::TileReader::AsynchronousIO> GdalLibrary::getMasterIOThread() {
                //XXX-- init once
                static std::shared_ptr<atakmap::raster::tilereader::TileReader::AsynchronousIO> asyncIO(new atakmap::raster::tilereader::TileReader::AsynchronousIO());
                return asyncIO;
            }


        }
    }
}
