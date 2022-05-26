#include "GdalLibrary.h"

#ifndef __APPLE__
#include <platformstl/filesystem/path.hpp>
#else
#include <sstream>
#endif

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
#ifdef __APPLE__
                    std::string gdalDataVer;
                    {
                        std::ostringstream strm;
                        strm << gdalDir << '/' << "gdal.version";
                        gdalDataVer = strm.str();
                    }
#else
                    platformstl::basic_path<char> gdalDataVer(gdalDir);
                    gdalDataVer.push("gdal.version");
#endif
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
                if (value == nullptr || strcmp(value, "EPSG") != 0) {
                    // note there are producers out there that are supplying prj files without
                    // the AUTHORITY set such as "WGS_1984_UTM_Zone_56S", which really is
                    // AUTHORITY["EPSG","32756"] � it seems like ESRI is the product being used
                    // to generate the prj files.

                    // this could exist anywhere in the PROJCS list, we may need to revisit
                    value = srs->GetAttrValue("PROJCS", 0);
                    if (value != nullptr) {
                        return coordSysName2EPSG(value);
                    }
                    return -1;
                }

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

            int GdalLibrary::coordSysName2EPSG(const char* value) {
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_1N")) return (32601);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_2N")) return (32602);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_3N")) return (32603);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_4N")) return (32604);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_5N")) return (32605);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_6N")) return (32606);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_7N")) return (32607);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_8N")) return (32608);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_9N")) return (32609);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_10N")) return (32610);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_11N")) return (32611);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_12N")) return (32612);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_13N")) return (32613);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_14N")) return (32614);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_15N")) return (32615);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_16N")) return (32616);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_17N")) return (32617);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_18N")) return (32618);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_19N")) return (32619);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_20N")) return (32620);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_21N")) return (32621);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_22N")) return (32622);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_23N")) return (32623);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_24N")) return (32624);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_25N")) return (32625);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_26N")) return (32626);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_27N")) return (32627);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_28N")) return (32628);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_29N")) return (32629);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_30N")) return (32630);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_31N")) return (32631);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_32N")) return (32632);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_33N")) return (32633);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_34N")) return (32634);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_35N")) return (32635);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_36N")) return (32636);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_37N")) return (32637);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_38N")) return (32638);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_39N")) return (32639);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_40N")) return (32640);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_41N")) return (32641);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_42N")) return (32642);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_43N")) return (32643);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_44N")) return (32644);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_45N")) return (32645);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_46N")) return (32646);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_47N")) return (32647);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_48N")) return (32648);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_49N")) return (32649);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_50N")) return (32650);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_51N")) return (32651);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_52N")) return (32652);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_53N")) return (32653);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_54N")) return (32654);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_55N")) return (32655);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_56N")) return (32656);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_57N")) return (32657);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_58N")) return (32658);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_59N")) return (32659);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_60N")) return (32660);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_1S")) return (32701);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_2S")) return (32702);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_3S")) return (32703);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_4S")) return (32704);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_5S")) return (32705);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_6S")) return (32706);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_7S")) return (32707);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_8S")) return (32708);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_9S")) return (32709);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_10S")) return (32710);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_11S")) return (32711);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_12S")) return (32712);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_13S")) return (32713);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_14S")) return (32714);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_15S")) return (32715);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_16S")) return (32716);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_17S")) return (32717);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_18S")) return (32718);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_19S")) return (32719);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_20S")) return (32720);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_21S")) return (32721);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_22S")) return (32722);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_23S")) return (32723);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_24S")) return (32724);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_25S")) return (32725);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_26S")) return (32726);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_27S")) return (32727);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_28S")) return (32728);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_29S")) return (32729);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_30S")) return (32730);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_31S")) return (32731);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_32S")) return (32732);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_33S")) return (32733);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_34S")) return (32734);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_35S")) return (32735);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_36S")) return (32736);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_37S")) return (32737);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_38S")) return (32738);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_39S")) return (32739);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_40S")) return (32740);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_41S")) return (32741);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_42S")) return (32742);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_43S")) return (32743);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_44S")) return (32744);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_45S")) return (32745);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_46S")) return (32746);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_47S")) return (32747);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_48S")) return (32748);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_49S")) return (32749);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_50S")) return (32750);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_51S")) return (32751);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_52S")) return (32752);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_53S")) return (32753);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_54S")) return (32754);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_55S")) return (32755);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_56S")) return (32756);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_57S")) return (32757);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_58S")) return (32758);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_59S")) return (32759);
                if (0 == strcmp(value, "WGS_1984_UTM_Zone_60S")) return (32760);
                if (0 == strcmp(value, "Japanese Zone I")) return (32761);
                if (0 == strcmp(value, "Japanese Zone II")) return (32762);
                if (0 == strcmp(value, "Japanese Zone III")) return (32763);
                if (0 == strcmp(value, "Japanese Zone IV")) return (32764);
                if (0 == strcmp(value, "Japanese Zone V")) return (32765);
                if (0 == strcmp(value, "Japanese Zone VI")) return (32766);
                if (0 == strcmp(value, "Japanese Zone VII")) return (32767);
                if (0 == strcmp(value, "Japanese Zone VIII")) return (32768);
                if (0 == strcmp(value, "Japanese Zone IX")) return (32769);
                if (0 == strcmp(value, "Japanese Zone X")) return (32770);
                if (0 == strcmp(value, "Japanese Zone XI")) return (32771);
                if (0 == strcmp(value, "Japanese Zone XII")) return (32772);
                if (0 == strcmp(value, "Japanese Zone XIII")) return (32773);
                if (0 == strcmp(value, "Japanese Zone XIV")) return (32774);
                if (0 == strcmp(value, "Japanese Zone XV")) return (32775);
                if (0 == strcmp(value, "Japanese Zone XVI")) return (32776);
                if (0 == strcmp(value, "Japanese Zone XVII")) return (32777);
                if (0 == strcmp(value, "Japanese Zone XVIII")) return (32778);
                if (0 == strcmp(value, "Japanese Zone XIX")) return (32779);
                if (0 == strcmp(value, "Tokyo UTM 51")) return (32780);
                if (0 == strcmp(value, "TOKYO UTM 52")) return (32781);
                if (0 == strcmp(value, "TOKYO UTM 53")) return (32782);
                if (0 == strcmp(value, "TOKYO UTM 54")) return (32783);
                if (0 == strcmp(value, "TOKYO UTM 55")) return (32784);
                if (0 == strcmp(value, "Japanese 2000 Zone I")) return (32786);
                if (0 == strcmp(value, "Japanese 2000 Zone II")) return (32787);
                if (0 == strcmp(value, "Japanese 2000 Zone III")) return (32788);
                if (0 == strcmp(value, "Japanese 2000 Zone IV")) return (32789);
                if (0 == strcmp(value, "Japanese 2000 Zone V")) return (32790);
                if (0 == strcmp(value, "Japanese 2000 Zone VI")) return (32791);
                if (0 == strcmp(value, "Japanese 2000 Zone VII")) return (32792);
                if (0 == strcmp(value, "Japanese 2000 Zone VIII")) return (32793);
                if (0 == strcmp(value, "Japanese 2000 Zone IX")) return (32794);
                if (0 == strcmp(value, "Japanese 2000 Zone X")) return (32795);
                if (0 == strcmp(value, "Japanese 2000 Zone XI")) return (32796);
                if (0 == strcmp(value, "Japanese 2000 Zone XII")) return (32797);
                if (0 == strcmp(value, "Japanese 2000 Zone XIII")) return (32798);
                if (0 == strcmp(value, "Japanese 2000 Zone XIV")) return (32800);
                if (0 == strcmp(value, "Japanese 2000 Zone XV")) return (32801);
                if (0 == strcmp(value, "Japanese 2000 Zone XVI")) return (32802);
                if (0 == strcmp(value, "Japanese 2000 Zone XVII")) return (32803);
                if (0 == strcmp(value, "Japanese 2000 Zone XVIII")) return (32804);
                if (0 == strcmp(value, "Japanese 2000 Zone XIX")) return (32805);
                if (0 == strcmp(value, "Japanese UTM 51")) return (32806);
                if (0 == strcmp(value, "Japanese UTM 52")) return (32807);
                if (0 == strcmp(value, "Japanese UTM 53")) return (32808);
                if (0 == strcmp(value, "Japanese UTM 54")) return (32809);
                if (0 == strcmp(value, "Japanese UTM 55")) return (32810);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_1N")) return (32201);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_2N")) return (32202);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_3N")) return (32203);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_4N")) return (32204);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_5N")) return (32205);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_6N")) return (32206);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_7N")) return (32207);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_8N")) return (32208);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_9N")) return (32209);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_10N")) return (32210);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_11N")) return (32211);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_12N")) return (32212);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_13N")) return (32213);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_14N")) return (32214);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_15N")) return (32215);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_16N")) return (32216);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_17N")) return (32217);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_18N")) return (32218);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_19N")) return (32219);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_20N")) return (32220);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_21N")) return (32221);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_22N")) return (32222);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_23N")) return (32223);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_24N")) return (32224);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_25N")) return (32225);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_26N")) return (32226);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_27N")) return (32227);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_28N")) return (32228);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_29N")) return (32229);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_30N")) return (32230);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_31N")) return (32231);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_32N")) return (32232);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_33N")) return (32233);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_34N")) return (32234);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_35N")) return (32235);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_36N")) return (32236);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_37N")) return (32237);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_38N")) return (32238);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_39N")) return (32239);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_40N")) return (32240);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_41N")) return (32241);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_42N")) return (32242);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_43N")) return (32243);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_44N")) return (32244);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_45N")) return (32245);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_46N")) return (32246);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_47N")) return (32247);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_48N")) return (32248);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_49N")) return (32249);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_50N")) return (32250);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_51N")) return (32251);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_52N")) return (32252);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_53N")) return (32253);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_54N")) return (32254);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_55N")) return (32255);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_56N")) return (32256);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_57N")) return (32257);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_58N")) return (32258);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_59N")) return (32259);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_60N")) return (32260);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_1S")) return (32301);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_2S")) return (32302);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_3S")) return (32303);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_4S")) return (32304);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_5S")) return (32305);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_6S")) return (32306);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_7S")) return (32307);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_8S")) return (32308);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_9S")) return (32309);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_10S")) return (32310);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_11S")) return (32311);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_12S")) return (32312);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_13S")) return (32313);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_14S")) return (32314);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_15S")) return (32315);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_16S")) return (32316);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_17S")) return (32317);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_18S")) return (32318);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_19S")) return (32319);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_20S")) return (32320);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_21S")) return (32321);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_22S")) return (32322);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_23S")) return (32323);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_24S")) return (32324);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_25S")) return (32325);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_26S")) return (32326);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_27S")) return (32327);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_28S")) return (32328);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_29S")) return (32329);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_30S")) return (32330);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_31S")) return (32331);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_32S")) return (32332);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_33S")) return (32333);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_34S")) return (32334);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_35S")) return (32335);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_36S")) return (32336);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_37S")) return (32337);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_38S")) return (32338);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_39S")) return (32339);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_40S")) return (32340);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_41S")) return (32341);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_42S")) return (32342);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_43S")) return (32343);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_44S")) return (32344);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_45S")) return (32345);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_46S")) return (32346);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_47S")) return (32347);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_48S")) return (32348);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_49S")) return (32349);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_50S")) return (32350);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_51S")) return (32351);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_52S")) return (32352);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_53S")) return (32353);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_54S")) return (32354);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_55S")) return (32355);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_56S")) return (32356);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_57S")) return (32357);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_58S")) return (32358);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_59S")) return (32359);
                if (0 == strcmp(value, "WGS_1972_UTM_Zone_60S")) return (32360);
                if (0 == strcmp(value, "NAD_1927_BLM_Zone_14N")) return (32074);
                if (0 == strcmp(value, "NAD_1927_BLM_Zone_15N")) return (32075);
                if (0 == strcmp(value, "NAD_1927_BLM_Zone_16N")) return (32076);
                if (0 == strcmp(value, "NAD_1927_BLM_Zone_17N")) return (32077);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_3N")) return (26703);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_4N")) return (26704);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_5N")) return (26705);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_6N")) return (26706);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_7N")) return (26707);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_8N")) return (26708);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_9N")) return (26709);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_10N")) return (26710);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_11N")) return (26711);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_12N")) return (26712);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_13N")) return (26713);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_14N")) return (26714);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_15N")) return (26715);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_16N")) return (26716);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_17N")) return (26717);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_18N")) return (26718);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_19N")) return (26719);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_20N")) return (26720);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_21N")) return (26721);
                if (0 == strcmp(value, "NAD_1927_UTM_Zone_22N")) return (26722);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_3N")) return (26903);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_4N")) return (26904);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_5N")) return (26905);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_6N")) return (26906);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_7N")) return (26907);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_8N")) return (26908);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_9N")) return (26909);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_10N")) return (26910);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_11N")) return (26911);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_12N")) return (26912);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_13N")) return (26913);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_14N")) return (26914);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_15N")) return (26915);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_16N")) return (26916);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_17N")) return (26917);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_18N")) return (26918);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_19N")) return (26919);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_20N")) return (26920);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_21N")) return (26921);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_22N")) return (26922);
                if (0 == strcmp(value, "NAD_1983_UTM_Zone_23N")) return (26923);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_28N")) return (25828);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_29N")) return (25829);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_30N")) return (25830);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_31N")) return (25831);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_32N")) return (25832);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_33N")) return (25833);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_34N")) return (25834);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_35N")) return (25835);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_36N")) return (25836);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_37N")) return (25837);
                if (0 == strcmp(value, "ETRF_1989_UTM_Zone_38N")) return (25838);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_4")) return (28404);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_5")) return (28405);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_6")) return (28406);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_7")) return (28407);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_8")) return (28408);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_9")) return (28409);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_10")) return (28410);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_11")) return (28411);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_12")) return (28412);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_13")) return (28413);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_14")) return (28414);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_15")) return (28415);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_16")) return (28416);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_17")) return (28417);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_18")) return (28418);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_19")) return (28419);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_20")) return (28420);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_21")) return (28421);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_22")) return (28422);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_23")) return (28423);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_24")) return (28424);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_25")) return (28425);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_26")) return (28426);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_27")) return (28427);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_28")) return (28428);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_29")) return (28429);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_30")) return (28430);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_31")) return (28431);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_32")) return (28432);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_4N")) return (28464);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_5N")) return (28465);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_6N")) return (28466);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_7N")) return (28467);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_8N")) return (28468);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_9N")) return (28469);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_10N")) return (28470);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_11N")) return (28471);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_12N")) return (28472);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_13N")) return (28473);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_14N")) return (28474);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_15N")) return (28475);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_16N")) return (28476);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_17N")) return (28477);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_18N")) return (28478);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_19N")) return (28479);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_20N")) return (28480);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_21N")) return (28481);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_22N")) return (28482);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_23N")) return (28483);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_24N")) return (28484);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_25N")) return (28485);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_26N")) return (28486);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_27N")) return (28487);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_28N")) return (28488);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_29N")) return (28489);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_30N")) return (28490);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_31N")) return (28491);
                if (0 == strcmp(value, "Pulkovo_1942_GK_Zone_32N")) return (28492);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_4")) return (20004);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_5")) return (20005);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_6")) return (20006);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_7")) return (20007);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_8")) return (20008);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_9")) return (20009);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_10")) return (20010);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_11")) return (20011);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_12")) return (20012);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_13")) return (20013);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_14")) return (20014);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_15")) return (20015);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_16")) return (20016);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_17")) return (20017);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_18")) return (20018);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_19")) return (20019);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_20")) return (20020);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_21")) return (20021);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_22")) return (20022);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_23")) return (20023);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_24")) return (20024);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_25")) return (20025);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_26")) return (20026);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_27")) return (20027);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_28")) return (20028);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_29")) return (20029);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_30")) return (20030);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_31")) return (20031);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_32")) return (20032);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_4N")) return (20064);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_5N")) return (20065);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_6N")) return (20066);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_7N")) return (20067);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_8N")) return (20068);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_9N")) return (20069);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_10N")) return (20070);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_11N")) return (20071);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_12N")) return (20072);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_13N")) return (20073);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_14N")) return (20074);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_15N")) return (20075);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_16N")) return (20076);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_17N")) return (20077);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_18N")) return (20078);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_19N")) return (20079);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_20N")) return (20080);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_21N")) return (20081);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_22N")) return (20082);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_23N")) return (20083);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_24N")) return (20084);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_25N")) return (20085);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_26N")) return (20086);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_27N")) return (20087);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_28N")) return (20088);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_29N")) return (20089);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_30N")) return (20090);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_31N")) return (20091);
                if (0 == strcmp(value, "Pulkovo_1995_GK_Zone_32N")) return (20092);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_13")) return (21413);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_14")) return (21414);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_15")) return (21415);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_16")) return (21416);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_17")) return (21417);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_18")) return (21418);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_19")) return (21419);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_20")) return (21420);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_21")) return (21421);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_22")) return (21422);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_23")) return (21423);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_13N")) return (21473);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_14N")) return (21474);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_15N")) return (21475);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_16N")) return (21476);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_17N")) return (21477);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_18N")) return (21478);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_19N")) return (21479);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_20N")) return (21480);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_21N")) return (21481);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_22N")) return (21482);
                if (0 == strcmp(value, "Beijing_1954_GK_Zone_23N")) return (21483);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_28N")) return (23028);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_29N")) return (23029);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_30N")) return (23030);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_31N")) return (23031);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_32N")) return (23032);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_33N")) return (23033);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_34N")) return (23034);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_35N")) return (23035);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_36N")) return (23036);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_37N")) return (23037);
                if (0 == strcmp(value, "ED_1950_UTM_Zone_38N")) return (23038);
                if (0 == strcmp(value, "ATS_1977_UTM_Zone_19N")) return (2219);
                if (0 == strcmp(value, "ATS_1977_UTM_Zone_20N")) return (2220);
                if (0 == strcmp(value, "Finland_Zone_1")) return (2391);
                if (0 == strcmp(value, "Finland_Zone_2")) return (2392);
                if (0 == strcmp(value, "Finland_Zone_3")) return (2393);
                if (0 == strcmp(value, "Finland_Zone_4")) return (2394);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_18N")) return (29118);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_19N")) return (29119);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_20N")) return (29120);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_21N")) return (29121);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_22N")) return (29122);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_17S")) return (29177);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_18S")) return (29178);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_19S")) return (29179);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_20S")) return (29180);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_21S")) return (29181);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_22S")) return (29182);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_23S")) return (29183);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_24S")) return (29184);
                if (0 == strcmp(value, "SAD_1969_UTM_Zone_25S")) return (29185);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_48")) return (20248);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_49")) return (20249);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_50")) return (20250);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_51")) return (20251);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_52")) return (20252);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_53")) return (20253);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_54")) return (20254);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_55")) return (20255);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_56")) return (20256);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_57")) return (20257);
                if (0 == strcmp(value, "AGD_1966_AMG_Zone_58")) return (20258);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_48")) return (20348);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_49")) return (20349);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_50")) return (20350);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_51")) return (20351);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_52")) return (20352);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_53")) return (20353);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_54")) return (20354);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_55")) return (20355);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_56")) return (20356);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_57")) return (20357);
                if (0 == strcmp(value, "AGD_1984_AMG_Zone_58")) return (20358);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_48")) return (28348);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_49")) return (28349);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_50")) return (28350);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_51")) return (28351);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_52")) return (28352);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_53")) return (28353);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_54")) return (28354);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_55")) return (28355);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_56")) return (28356);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_57")) return (28357);
                if (0 == strcmp(value, "GDA_1994_MGA_Zone_58")) return (28358);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alabama_East_FIPS_0101")) return (26729);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alabama_West_FIPS_0102")) return (26730);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_1_FIPS_5001")) return (26731);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_2_FIPS_5002")) return (26732);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_3_FIPS_5003")) return (26733);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_4_FIPS_5004")) return (26734);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_5_FIPS_5005")) return (26735);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_6_FIPS_5006")) return (26736);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_7_FIPS_5007")) return (26737);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_8_FIPS_5008")) return (26738);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_9_FIPS_5009")) return (26739);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Alaska_10_FIPS_5010")) return (26740);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Arizona_East_FIPS_0201")) return (26748);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Arizona_Central_FIPS_0202")) return (26749);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Arizona_West_FIPS_0203")) return (26750);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Arkansas_North_FIPS_0301")) return (26751);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Arkansas_South_FIPS_0302")) return (26752);
                if (0 == strcmp(value, "NAD_1927_StatePlane_California_I_FIPS_0401")) return (26741);
                if (0 == strcmp(value, "NAD_1927_StatePlane_California_II_FIPS_0402")) return (26742);
                if (0 == strcmp(value, "NAD_1927_StatePlane_California_III_FIPS_0403")) return (26743);
                if (0 == strcmp(value, "NAD_1927_StatePlane_California_IV_FIPS_0404")) return (26744);
                if (0 == strcmp(value, "NAD_1927_StatePlane_California_V_FIPS_0405")) return (26745);
                if (0 == strcmp(value, "NAD_1927_StatePlane_California_VI_FIPS_0406")) return (26746);
                if (0 == strcmp(value, "NAD_1927_StatePlane_California_VII_FIPS_0407")) return (26747);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Colorado_North_FIPS_0501")) return (26753);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Colorado_Central_FIPS_0502")) return (26754);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Colorado_South_FIPS_0503")) return (26755);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Connecticut_FIPS_0600")) return (26756);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Delaware_FIPS_0700")) return (26757);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Florida_East_FIPS_0901")) return (26758);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Florida_West_FIPS_0902")) return (26759);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Florida_North_FIPS_0903")) return (26760);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Georgia_East_FIPS_1001")) return (26766);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Georgia_West_FIPS_1002")) return (26767);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Hawaii_1_FIPS_5101")) return (26761);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Hawaii_2_FIPS_5102")) return (26762);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Hawaii_3_FIPS_5103")) return (26763);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Hawaii_4_FIPS_5104")) return (26764);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Hawaii_5_FIPS_5105")) return (26765);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Idaho_East_FIPS_1101")) return (26768);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Idaho_Central_FIPS_1102")) return (26769);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Idaho_West_FIPS_1103")) return (26770);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Illinois_East_FIPS_1201")) return (26771);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Illinois_West_FIPS_1202")) return (26772);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Indiana_East_FIPS_1301")) return (26773);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Indiana_West_FIPS_1302")) return (26774);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Iowa_North_FIPS_1401")) return (26775);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Iowa_South_FIPS_1402")) return (26776);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Kansas_North_FIPS_1501")) return (26777);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Kansas_South_FIPS_1502")) return (26778);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Kentucky_North_FIPS_1601")) return (26779);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Kentucky_South_FIPS_1602")) return (26780);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Louisiana_North_FIPS_1701")) return (26781);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Louisiana_South_FIPS_1702")) return (26782);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Maine_East_FIPS_1801")) return (26783);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Maine_West_FIPS_1802")) return (26784);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Maryland_FIPS_1900")) return (26785);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Massachusetts_Mainland_FIPS_2001")) return (26786);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Massachusetts_Island_FIPS_2002")) return (26787);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Michigan_North_FIPS_2111")) return (26788);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Michigan_Central_FIPS_2112")) return (26789);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Michigan_South_FIPS_2113")) return (26790);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Minnesota_North_FIPS_2201")) return (26791);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Minnesota_Central_FIPS_2202")) return (26792);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Minnesota_South_FIPS_2203")) return (26793);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Mississippi_East_FIPS_2301")) return (26794);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Mississippi_West_FIPS_2302")) return (26795);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Missouri_East_FIPS_2401")) return (26796);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Missouri_Central_FIPS_2402")) return (26797);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Missouri_West_FIPS_2403")) return (26798);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Montana_North_FIPS_2501")) return (32001);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Montana_Central_FIPS_2502")) return (32002);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Montana_South_FIPS_2503")) return (32003);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Nebraska_North_FIPS_2601")) return (32005);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Nebraska_South_FIPS_2602")) return (32006);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Nevada_East_FIPS_2701")) return (32007);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Nevada_Central_FIPS_2702")) return (32008);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Nevada_West_FIPS_2703")) return (32009);
                if (0 == strcmp(value, "NAD_1927_StatePlane_New_Hampshire_FIPS_2800")) return (32010);
                if (0 == strcmp(value, "NAD_1927_StatePlane_New_Jersey_FIPS_2900")) return (32011);
                if (0 == strcmp(value, "NAD_1927_StatePlane_New_Mexico_East_FIPS_3001")) return (32012);
                if (0 == strcmp(value, "NAD_1927_StatePlane_New_Mexico_Central_FIPS_3002")) return (32013);
                if (0 == strcmp(value, "NAD_1927_StatePlane_New_Mexico_West_FIPS_3003")) return (32014);
                if (0 == strcmp(value, "NAD_1927_StatePlane_New_York_East_FIPS_3101")) return (32015);
                if (0 == strcmp(value, "NAD_1927_StatePlane_New_York_Central_FIPS_3102")) return (32016);
                if (0 == strcmp(value, "NAD_1927_StatePlane_New_York_West_FIPS_3103")) return (32017);
                if (0 == strcmp(value, "NAD_1927_StatePlane_New_York_Long_Island_FIPS_3104")) return (32018);
                if (0 == strcmp(value, "NAD_1927_StatePlane_North_Carolina_FIPS_3200")) return (32019);
                if (0 == strcmp(value, "NAD_1927_StatePlane_North_Dakota_North_FIPS_3301")) return (32020);
                if (0 == strcmp(value, "NAD_1927_StatePlane_North_Dakota_South_FIPS_3302")) return (32021);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Ohio_North_FIPS_3401")) return (32022);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Ohio_South_FIPS_3402")) return (32023);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Oklahoma_North_FIPS_3501")) return (32024);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Oklahoma_South_FIPS_3502")) return (32025);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Oregon_North_FIPS_3601")) return (32026);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Oregon_South_FIPS_3602")) return (32027);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Pennsylvania_North_FIPS_3701")) return (32028);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Pennsylvania_South_FIPS_3702")) return (32029);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Rhode_Island_FIPS_3800")) return (32030);
                if (0 == strcmp(value, "NAD_1927_StatePlane_South_Carolina_North_FIPS_3901")) return (32031);
                if (0 == strcmp(value, "NAD_1927_StatePlane_South_Carolina_South_FIPS_3902")) return (32033);
                if (0 == strcmp(value, "NAD_1927_StatePlane_South_Dakota_North_FIPS_4001")) return (32034);
                if (0 == strcmp(value, "NAD_1927_StatePlane_South_Dakota_South_FIPS_4002")) return (32035);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Tennessee_FIPS_4100")) return (32036);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Texas_North_FIPS_4201")) return (32037);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Texas_North_Central_FIPS_4202")) return (32038);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Texas_Central_FIPS_4203")) return (32039);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Texas_South_Central_FIPS_4204")) return (32040);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Texas_South_FIPS_4205")) return (32041);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Utah_North_FIPS_4301")) return (32042);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Utah_Central_FIPS_4302")) return (32043);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Utah_South_FIPS_4303")) return (32044);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Vermont_FIPS_3400")) return (32045);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Virginia_North_FIPS_4501")) return (32046);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Virginia_South_FIPS_4502")) return (32047);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Washington_North_FIPS_4601")) return (32048);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Washington_South_FIPS_4602")) return (32049);
                if (0 == strcmp(value, "NAD_1927_StatePlane_West_Virginia_North_FIPS_4701")) return (32050);
                if (0 == strcmp(value, "NAD_1927_StatePlane_West_Virginia_South_FIPS_4702")) return (32051);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Wisconsin_North_FIPS_4801")) return (32052);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Wisconsin_Central_FIPS_4802")) return (32053);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Wisconsin_South_FIPS_4803")) return (32054);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Wyoming_East_FIPS_4901")) return (32055);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Wyoming_East_Central_FIPS_4902")) return (32056);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Wyoming_West_Central_FIPS_4903")) return (32057);
                if (0 == strcmp(value, "NAD_1927_StatePlane_Wyoming_West_FIPS_4904")) return (32058);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alabama_East_FIPS_0101")) return (26929);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alabama_West_FIPS_0102")) return (26930);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_1_FIPS_5001")) return (26931);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_2_FIPS_5002")) return (26932);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_3_FIPS_5003")) return (26933);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_4_FIPS_5004")) return (26934);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_5_FIPS_5005")) return (26935);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_6_FIPS_5006")) return (26936);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_7_FIPS_5007")) return (26937);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_8_FIPS_5008")) return (26938);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_9_FIPS_5009")) return (26939);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Alaska_10_FIPS_5010")) return (26940);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Arizona_East_FIPS_0201")) return (26948);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Arizona_Central_FIPS_0202")) return (26949);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Arizona_West_FIPS_0203")) return (26950);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Arkansas_North_FIPS_0301")) return (26951);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Arkansas_South_FIPS_0302")) return (26952);
                if (0 == strcmp(value, "NAD_1983_StatePlane_California_I_FIPS_0401")) return (26941);
                if (0 == strcmp(value, "NAD_1983_StatePlane_California_II_FIPS_0402")) return (26942);
                if (0 == strcmp(value, "NAD_1983_StatePlane_California_III_FIPS_0403")) return (26943);
                if (0 == strcmp(value, "NAD_1983_StatePlane_California_IV_FIPS_0404")) return (26944);
                if (0 == strcmp(value, "NAD_1983_StatePlane_California_V_FIPS_0405")) return (26945);
                if (0 == strcmp(value, "NAD_1983_StatePlane_California_VI_FIPS_0406")) return (26946);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Colorado_North_FIPS_0501")) return (26953);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Colorado_Central_FIPS_0502")) return (26954);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Colorado_South_FIPS_0503")) return (26955);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Connecticut_FIPS_0600")) return (26956);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Delaware_FIPS_0700")) return (26957);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Florida_East_FIPS_0901")) return (26958);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Florida_West_FIPS_0902")) return (26959);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Florida_North_FIPS_0903")) return (26960);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Georgia_East_FIPS_1001")) return (26966);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Georgia_West_FIPS_1002")) return (26967);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Hawaii_1_FIPS_5101")) return (26961);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Hawaii_2_FIPS_5102")) return (26962);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Hawaii_3_FIPS_5103")) return (26963);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Hawaii_4_FIPS_5104")) return (26964);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Hawaii_5_FIPS_5105")) return (26965);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Idaho_East_FIPS_1101")) return (26968);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Idaho_Central_FIPS_1102")) return (26969);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Idaho_West_FIPS_1103")) return (26970);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Illinois_East_FIPS_1201")) return (26971);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Illinois_West_FIPS_1202")) return (26972);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Indiana_East_FIPS_1301")) return (26973);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Indiana_West_FIPS_1302")) return (26974);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Iowa_North_FIPS_1401")) return (26975);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Iowa_South_FIPS_1402")) return (26976);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Kansas_North_FIPS_1501")) return (26977);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Kansas_South_FIPS_1502")) return (26978);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Kentucky_North_FIPS_1601")) return (26979);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Kentucky_South_FIPS_1602")) return (26980);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Louisiana_North_FIPS_1701")) return (26981);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Louisiana_South_FIPS_1702")) return (26982);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Maine_East_FIPS_1801")) return (26983);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Maine_West_FIPS_1802")) return (26984);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Maryland_FIPS_1900")) return (26985);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Massachusetts_Mainland_FIPS_2001")) return (26986);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Massachusetts_Island_FIPS_2002")) return (26987);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Michigan_North_FIPS_2111")) return (26988);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Michigan_Central_FIPS_2202")) return (26989);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Michigan_South_FIPS_2113")) return (26990);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Minnesota_North_FIPS_2201")) return (26991);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Minnesota_Central_FIPS_2202")) return (26992);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Minnesota_South_FIPS_2203")) return (26993);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Mississippi_East_FIPS_2301")) return (26994);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Mississippi_West_FIPS_2302")) return (26995);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Missouri_East_FIPS_2401")) return (26996);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Missouri_Central_FIPS_2402")) return (26997);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Missouri_West_FIPS_2403")) return (26998);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Montana_FIPS_2500")) return (32100);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Nebraska_FIPS_2600")) return (32104);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Nevada_East_FIPS_2701")) return (32107);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Nevada_Central_FIPS_2702")) return (32108);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Nevada_West_FIPS_2703")) return (32109);
                if (0 == strcmp(value, "NAD_1983_StatePlane_New_Hampshire_FIPS_2800")) return (32110);
                if (0 == strcmp(value, "NAD_1983_StatePlane_New_Jersey_FIPS_2900")) return (32111);
                if (0 == strcmp(value, "NAD_1983_StatePlane_New_Mexico_East_FIPS_3001")) return (32112);
                if (0 == strcmp(value, "NAD_1983_StatePlane_New_Mexico_Central_FIPS_3002")) return (32113);
                if (0 == strcmp(value, "NAD_1983_StatePlane_New_Mexico_West_FIPS_3003")) return (32114);
                if (0 == strcmp(value, "NAD_1983_StatePlane_New_York_East_FIPS_3101")) return (32115);
                if (0 == strcmp(value, "NAD_1983_StatePlane_New_York_Central_FIPS_3102")) return (32116);
                if (0 == strcmp(value, "NAD_1983_StatePlane_New_York_West_FIPS_3103")) return (32117);
                if (0 == strcmp(value, "NAD_1983_StatePlane_New_York_Long_Island_FIPS_3104")) return (32118);
                if (0 == strcmp(value, "NAD_1983_StatePlane_North_Carolina_FIPS_3200")) return (32119);
                if (0 == strcmp(value, "NAD_1983_StatePlane_North_Dakota_North_FIPS_3301")) return (32120);
                if (0 == strcmp(value, "NAD_1983_StatePlane_North_Dakota_South_FIPS_3302")) return (32121);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Ohio_North_FIPS_3401")) return (32122);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Ohio_South_FIPS_3402")) return (32123);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Oklahoma_North_FIPS_3501")) return (32124);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Oklahoma_South_FIPS_3502")) return (32125);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Oregon_North_FIPS_3601")) return (32126);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Oregon_South_FIPS_3602")) return (32127);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Pennsylvania_North_FIPS_3701")) return (32128);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Pennsylvania_South_FIPS_3702")) return (32129);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Rhode_Island_FIPS_3800")) return (32130);
                if (0 == strcmp(value, "NAD_1983_StatePlane_South_Carolina_FIPS_3900")) return (32133);
                if (0 == strcmp(value, "NAD_1983_StatePlane_South_Dakota_North_FIPS_4001")) return (32134);
                if (0 == strcmp(value, "NAD_1983_StatePlane_South_Dakota_South_FIPS_4002")) return (32135);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Tennessee_FIPS_4100")) return (32136);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Texas_North_FIPS_4201")) return (32137);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Texas_North_Central_FIPS_4202")) return (32138);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Texas_Central_FIPS_4203")) return (32139);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Texas_South_Central_FIPS_4204")) return (32140);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Texas_South_FIPS_4205")) return (32141);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Utah_North_FIPS_4301")) return (32142);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Utah_Central_FIPS_4302")) return (32143);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Utah_South_FIPS_4303")) return (32144);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Vermont_FIPS_4400")) return (32145);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Virginia_North_FIPS_4501")) return (32146);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Virginia_South_FIPS_4502")) return (32147);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Washington_North_FIPS_4601")) return (32148);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Washington_South_FIPS_4602")) return (32149);
                if (0 == strcmp(value, "NAD_1983_StatePlane_West_Virginia_North_FIPS_4701")) return (32150);
                if (0 == strcmp(value, "NAD_1983_StatePlane_West_Virginia_South_FIPS_4702")) return (32151);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Wisconsin_North_FIPS_4801")) return (32152);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Wisconsin_Central_FIPS_4802")) return (32153);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Wisconsin_South_FIPS_4803")) return (32154);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Wyoming_East_FIPS_4901")) return (32155);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Wyoming_East_Central_FIPS_4902")) return (32156);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Wyoming_West_Central_FIPS_4903")) return (32157);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Wyoming_West_FIPS_4904")) return (32158);
                if (0 == strcmp(value, "NAD_1983_StatePlane_Puerto_Rico_Virgin_Islands_FIPS_5200")) return (32161);
                if (0 == strcmp(value, "Adindan_UTM_Zone_37N")) return (20137);
                if (0 == strcmp(value, "Adindan_UTM_Zone_38N")) return (20138);
                if (0 == strcmp(value, "Afgooye_UTM_Zone_38N")) return (20538);
                if (0 == strcmp(value, "Afgooye_UTM_Zone_39N")) return (20539);
                if (0 == strcmp(value, "Ain_el_Abd_UTM_Zone_37N")) return (20437);
                if (0 == strcmp(value, "Ain_el_Abd_UTM_Zone_38N")) return (20438);
                if (0 == strcmp(value, "Ain_el_Abd_UTM_Zone_39N")) return (20439);
                if (0 == strcmp(value, "Aratu_UTM_Zone_22S")) return (20822);
                if (0 == strcmp(value, "Aratu_UTM_Zone_23S")) return (20823);
                if (0 == strcmp(value, "Aratu_UTM_Zone_24S")) return (20824);
                if (0 == strcmp(value, "Batavia_UTM_Zone_48S")) return (21148);
                if (0 == strcmp(value, "Batavia_UTM_Zone_49S")) return (21149);
                if (0 == strcmp(value, "Batavia_UTM_Zone_50S")) return (21150);
                if (0 == strcmp(value, "Bogota_UTM_Zone_17N")) return (21817);
                if (0 == strcmp(value, "Bogota_UTM_Zone_18N")) return (21818);
                if (0 == strcmp(value, "Camacupa_UTM_Zone_32S")) return (22032);
                if (0 == strcmp(value, "Camacupa_UTM_Zone_33S")) return (22033);
                if (0 == strcmp(value, "Carthage_UTM_Zone_32N")) return (22332);
                if (0 == strcmp(value, "Corrego_Alegre_UTM_Zone_23S")) return (22523);
                if (0 == strcmp(value, "Corrego_Alegre_UTM_Zone_24S")) return (22524);
                if (0 == strcmp(value, "Datum_73_UTM_Zone_29N")) return (27429);
                if (0 == strcmp(value, "Douala_UTM_Zone_32N")) return (22832);
                if (0 == strcmp(value, "Fahud_UTM_Zone_39N")) return (23239);
                if (0 == strcmp(value, "Fahud_UTM_Zone_40N")) return (23240);
                if (0 == strcmp(value, "Garoua_UTM_Zone_33N")) return (23433);
                if (0 == strcmp(value, "Greek_Grid")) return (2100);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_46N")) return (23846);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_47N")) return (23847);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_48N")) return (23848);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_49N")) return (23849);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_50N")) return (23850);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_51N")) return (23851);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_52N")) return (23852);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_53N")) return (23853);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_46S")) return (23886);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_47S")) return (23887);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_48S")) return (23888);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_49S")) return (23889);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_50S")) return (23890);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_51S")) return (23891);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_52S")) return (23892);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_53S")) return (23893);
                if (0 == strcmp(value, "Indonesian_1974_UTM_Zone_54S")) return (23894);
                if (0 == strcmp(value, "Indian_1954_UTM_Zone_47N")) return (23947);
                if (0 == strcmp(value, "Indian_1954_UTM_Zone_48N")) return (23948);
                if (0 == strcmp(value, "Indian_1975_UTM_Zone_47N")) return (24047);
                if (0 == strcmp(value, "Indian_1975_UTM_Zone_48N")) return (24048);
                if (0 == strcmp(value, "Kertau_UTM_Zone_47N")) return (24547);
                if (0 == strcmp(value, "Kertau_UTM_Zone_48N")) return (24548);
                if (0 == strcmp(value, "La_Canoa_UTM_Zone_20N")) return (24720);
                if (0 == strcmp(value, "Lome_UTM_Zone_31N")) return (25231);
                if (0 == strcmp(value, "Mporaloko_UTM_Zone_32N")) return (26632);
                if (0 == strcmp(value, "Mporaloko_UTM_Zone_32S")) return (26692);
                if (0 == strcmp(value, "Malongo_1987_UTM_Zone_32S")) return (25932);
                if (0 == strcmp(value, "Massawa_UTM_Zone_37N")) return (26237);
                if (0 == strcmp(value, "Mhast_UTM_Zone_32S")) return (26432);
                if (0 == strcmp(value, "Minna_UTM_Zone_31N")) return (26331);
                if (0 == strcmp(value, "Minna_UTM_Zone_32N")) return (26332);
                if (0 == strcmp(value, "Nahrwan_1967_UTM_Zone_38N")) return (27038);
                if (0 == strcmp(value, "Nahrwan_1967_UTM_Zone_39N")) return (27039);
                if (0 == strcmp(value, "Nahrwan_1967_UTM_Zone_40N")) return (27040);
                if (0 == strcmp(value, "NGN_UTM_Zone_38N")) return (31838);
                if (0 == strcmp(value, "NGN_UTM_Zone_39N")) return (31839);
                if (0 == strcmp(value, "Nord_Sahara_1959_UTM_Zone_29N")) return (30729);
                if (0 == strcmp(value, "Nord_Sahara_1959_UTM_Zone_30N")) return (30730);
                if (0 == strcmp(value, "Nord_Sahara_1959_UTM_Zone_31N")) return (30731);
                if (0 == strcmp(value, "Nord_Sahara_1959_UTM_Zone_32N")) return (30732);
                if (0 == strcmp(value, "Naparima_1972_UTM_Zone_20N")) return (27120);
                if (0 == strcmp(value, "Pointe_Noire_UTM_Zone_32S")) return (28232);
                if (0 == strcmp(value, "PSAD_1956_UTM_Zone_18N")) return (24818);
                if (0 == strcmp(value, "PSAD_1956_UTM_Zone_19N")) return (24819);
                if (0 == strcmp(value, "PSAD_1956_UTM_Zone_20N")) return (24820);
                if (0 == strcmp(value, "PSAD_1956_UTM_Zone_21N")) return (24821);
                if (0 == strcmp(value, "PSAD_1956_UTM_Zone_17S")) return (24877);
                if (0 == strcmp(value, "PSAD_1956_UTM_Zone_18S")) return (24878);
                if (0 == strcmp(value, "PSAD_1956_UTM_Zone_19S")) return (24879);
                if (0 == strcmp(value, "PSAD_1956_UTM_Zone_20S")) return (24880);
                if (0 == strcmp(value, "Sapper_Hill_1943_UTM_Zone_20S")) return (29220);
                if (0 == strcmp(value, "Sapper_Hill_1943_UTM_Zone_21S")) return (29221);
                if (0 == strcmp(value, "Schwarzeck_UTM_Zone_33S")) return (29333);
                if (0 == strcmp(value, "Sudan_UTM_Zone_35N")) return (29635);
                if (0 == strcmp(value, "Sudan_UTM_Zone_36N")) return (29636);
                if (0 == strcmp(value, "Tananarive_1925_UTM_Zone_38S")) return (29738);
                if (0 == strcmp(value, "Tananarive_1925_UTM_Zone_39S")) return (29739);
                if (0 == strcmp(value, "TC_1948_UTM_Zone_39N")) return (30339);
                if (0 == strcmp(value, "TC_1948_UTM_Zone_40N")) return (30340);
                if (0 == strcmp(value, "Timbalai_1948_UTM_Zone_49N")) return (29849);
                if (0 == strcmp(value, "Timbalai_1948_UTM_Zone_50N")) return (29850);
                if (0 == strcmp(value, "Yoff_1972_UTM_Zone_28N")) return (31028);
                if (0 == strcmp(value, "Zanderij_1972_UTM_Zone_21N")) return (31121);
                if (0 == strcmp(value, "KUDAMS_KTM")) return (31900);
                if (0 == strcmp(value, "Philippines_Zone_I")) return (25391);
                if (0 == strcmp(value, "Philippines_Zone_II")) return (25392);
                if (0 == strcmp(value, "Philippines_Zone_III")) return (25393);
                if (0 == strcmp(value, "Philippines_Zone_IV")) return (25394);
                if (0 == strcmp(value, "Philippines_Zone_V")) return (25395);
                if (0 == strcmp(value, "Austria_West_Zone")) return (31291);
                if (0 == strcmp(value, "Austria_Central_Zone")) return (31292);
                if (0 == strcmp(value, "Austria_East_Zone")) return (31293);
                if (0 == strcmp(value, "Monte_Mario_Rome_Italy_1")) return (26591);
                if (0 == strcmp(value, "Monte_Mario_Rome_Italy_2")) return (26592);
                if (0 == strcmp(value, "Argentina_Zone_1")) return (22191);
                if (0 == strcmp(value, "Argentina_Zone_2")) return (22192);
                if (0 == strcmp(value, "Argentina_Zone_3")) return (22193);
                if (0 == strcmp(value, "Argentina_Zone_4")) return (22194);
                if (0 == strcmp(value, "Argentina_Zone_5")) return (22195);
                if (0 == strcmp(value, "Argentina_Zone_6")) return (22196);
                if (0 == strcmp(value, "Argentina_Zone_7")) return (22197);
                if (0 == strcmp(value, "Bahrain_State_Grid")) return (20499);
                if (0 == strcmp(value, "Colombia_West_Zone")) return (21891);
                if (0 == strcmp(value, "Colombia_Bogota_Zone")) return (21892);
                if (0 == strcmp(value, "Colombia_East_Central_Zone")) return (21893);
                if (0 == strcmp(value, "Colombia_East_Zone")) return (21894);
                if (0 == strcmp(value, "Egypt_Red_Belt")) return (22992);
                if (0 == strcmp(value, "Egypt_Purple_Belt")) return (22993);
                if (0 == strcmp(value, "Egypt_Extended_Purple_Belt")) return (22994);
                if (0 == strcmp(value, "Ghana_Metre_Grid")) return (25000);
                if (0 == strcmp(value, "Irish_National_Grid")) return (29900);
                if (0 == strcmp(value, "New_Zealand_North_Island")) return (27291);
                if (0 == strcmp(value, "New_Zealand_South_Island")) return (27292);
                if (0 == strcmp(value, "Nigeria_West_Belt")) return (26391);
                if (0 == strcmp(value, "Nigeria_Mid_Belt")) return (26392);
                if (0 == strcmp(value, "Nigeria_East_Belt")) return (26393);
                if (0 == strcmp(value, "Peru_West_Zone")) return (24891);
                if (0 == strcmp(value, "Peru_Central_Zone")) return (24892);
                if (0 == strcmp(value, "Peru_East_Zone")) return (24893);
                if (0 == strcmp(value, "Lisbon (Lisbon)/Portuguese National Grid")) return (20700);
                if (0 == strcmp(value, "Lisbon (Lisbon)/Portuguese Grid")) return (20791);
                if (0 == strcmp(value, "Datum 73 Hayford Gauss IGeoE")) return (27492);
                if (0 == strcmp(value, "Datum 73 Hayford Gauss IPCC")) return (27493);
                if (0 == strcmp(value, "Qatar_National_Grid")) return (28600);
                if (0 == strcmp(value, "British_National_Grid")) return (27700);
                if (0 == strcmp(value, "Swedish_National_Grid")) return (30800);
                if (0 == strcmp(value, "Nord_Algerie_Ancienne")) return (30491);
                if (0 == strcmp(value, "Sud_Algerie_Ancienne")) return (30492);
                if (0 == strcmp(value, "Nord_Algerie")) return (30591);
                if (0 == strcmp(value, "Sud_Algerie")) return (30592);
                if (0 == strcmp(value, "Nord_de_Guerre")) return (27500);
                if (0 == strcmp(value, "France_I")) return (27581);
                if (0 == strcmp(value, "France_II")) return (27582);
                if (0 == strcmp(value, "France_III")) return (27583);
                if (0 == strcmp(value, "France_IV")) return (27584);
                if (0 == strcmp(value, "Nord_France")) return (27591);
                if (0 == strcmp(value, "Centre_France")) return (27592);
                if (0 == strcmp(value, "Sud_France")) return (27593);
                if (0 == strcmp(value, "Corse")) return (27594);
                if (0 == strcmp(value, "India_Zone_0")) return (24370);
                if (0 == strcmp(value, "India_Zone_I")) return (24371);
                if (0 == strcmp(value, "India_Zone_IIa")) return (24372);
                if (0 == strcmp(value, "India_Zone_IIb")) return (24382);
                if (0 == strcmp(value, "Jamaica_1875_Old_Grid")) return (24100);
                if (0 == strcmp(value, "Jamaica_Grid")) return (24200);
                if (0 == strcmp(value, "Nord_Maroc")) return (26191);
                if (0 == strcmp(value, "Sud_Maroc")) return (26192);
                if (0 == strcmp(value, "Sahara")) return (26193);
                if (0 == strcmp(value, "Nord_Tunisie")) return (22391);
                if (0 == strcmp(value, "Sud_Tunisie")) return (22392);
                if (0 == strcmp(value, "KOC_Lambert")) return (24600);
                if (0 == strcmp(value, "Belge_Lambert_1950")) return (21500);
                if (0 == strcmp(value, "Stereo_33")) return (31600);
                if (0 == strcmp(value, "Stereo_70")) return (31700);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_25")) return (2401);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_26")) return (2402);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_27")) return (2403);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_28")) return (2404);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_29")) return (2405);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_30")) return (2406);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_31")) return (2407);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_32")) return (2408);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_33")) return (2409);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_34")) return (2410);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_35")) return (2411);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_36")) return (2412);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_37")) return (2413);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_38")) return (2414);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_39")) return (2415);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_40")) return (2416);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_41")) return (2417);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_42")) return (2418);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_43")) return (2419);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_44")) return (2420);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_45")) return (2421);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_25N")) return (2422);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_26N")) return (2423);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_27N")) return (2424);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_28N")) return (2425);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_29N")) return (2426);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_30N")) return (2427);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_31N")) return (2428);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_32N")) return (2429);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_33N")) return (2430);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_34N")) return (2431);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_35N")) return (2432);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_36N")) return (2433);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_37N")) return (2434);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_38N")) return (2435);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_39N")) return (2436);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_40N")) return (2437);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_41N")) return (2438);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_42N")) return (2439);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_43N")) return (2440);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_44N")) return (2441);
                if (0 == strcmp(value, "Beijing_1954_3_DEGREE_GK_Zone_45N")) return (2442);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_13")) return (2327);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_14")) return (2328);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_15")) return (2329);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_16")) return (2330);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_17")) return (2331);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_18")) return (2332);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_19")) return (2333);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_20")) return (2334);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_21")) return (2335);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_22")) return (2336);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_23")) return (2337);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_13N")) return (2338);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_14N")) return (2339);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_15N")) return (2340);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_16N")) return (2341);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_17N")) return (2342);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_18N")) return (2343);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_19N")) return (2344);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_20N")) return (2345);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_21N")) return (2346);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_22N")) return (2347);
                if (0 == strcmp(value, "Xian_1980_GK_Zone_23N")) return (2348);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_25")) return (2349);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_26")) return (2350);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_27")) return (2351);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_28")) return (2352);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_29")) return (2353);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_30")) return (2354);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_31")) return (2355);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_32")) return (2356);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_33")) return (2357);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_34")) return (2358);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_35")) return (2359);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_36")) return (2360);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_37")) return (2361);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_38")) return (2362);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_39")) return (2363);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_40")) return (2364);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_41")) return (2365);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_42")) return (2366);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_43")) return (2367);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_44")) return (2368);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_45")) return (2369);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_25N")) return (2370);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_26N")) return (2371);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_27N")) return (2372);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_28N")) return (2373);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_29N")) return (2374);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_30N")) return (2375);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_31N")) return (2376);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_32N")) return (2377);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_33N")) return (2378);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_34N")) return (2379);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_35N")) return (2380);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_36N")) return (2381);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_37N")) return (2382);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_38N")) return (2383);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_39N")) return (2384);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_40N")) return (2385);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_41N")) return (2386);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_42N")) return (2387);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_43N")) return (2388);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_44N")) return (2389);
                if (0 == strcmp(value, "Xian_1980_3_DEGREE_GK_Zone_45N")) return (2390);
                if (0 == strcmp(value, "PCS_ETRS89_PORTUGAL_TM06")) return (3763);
                if (0 == strcmp(value, "PCS_AZORES_OCCIDENTAL_1939_UTM_ZONE_25N")) return (2188);
                if (0 == strcmp(value, "PCS_AZORES_CENTRAL_1948_UTM_ZONE_26N")) return (2189);
                if (0 == strcmp(value, "PCS_AZORES_ORIENTAL_1940_UTM_ZONE_26N")) return (2190);
                if (0 == strcmp(value, "PCS_MADEIRA_1936_UTM_ZONE_28N")) return (2191);
                if (0 == strcmp(value, "PCS_WGS_1984_WORLD_MERCATOR")) return (3857);

                return -1;
            }
        }
    }
}
