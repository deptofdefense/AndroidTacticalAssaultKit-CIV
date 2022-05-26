
#include <stdexcept>
#include <list>

#include "gdal_priv.h"
#include "GdalLayerInfo.h"
#include "raster/ImageDatasetDescriptor.h"
#include "raster/pfps/PfpsMapTypeFrame.h"
#include "GdalDatasetProjection.h"
#include "GdalTileReader.h"
#include "raster/ImageryFileType.h"
#include "util/IO.h"
#include "util/IO2.h"
#include "util/Memory.h"
#include "raster/mosaic/ATAKMosaicDatabase.h"
#include "raster/MosaicDatasetDescriptor.h"

namespace {
    
    struct Frame {
    Frame() :
        typeIsDatasetType(false),
        resolution(NAN),
        numLevels(0),
        width(0),
        height(0),
        srid(4326)
        {}
        
        std::string name;
        std::string path;
        std::string gdalSubDataset;
        std::string type;
        bool typeIsDatasetType;
        atakmap::core::GeoPoint upperLeft;
        atakmap::core::GeoPoint upperRight;
        atakmap::core::GeoPoint lowerRight;
        atakmap::core::GeoPoint lowerLeft;
        double resolution;
        int numLevels;
        int width;
        int height;
        int srid;
    };
    
    class ProjectiveMappingProjection : public atakmap::core::Projection {
    private:
        atakmap::math::Matrix img2proj;
        atakmap::math::Matrix proj2img;
        
    public:
        ProjectiveMappingProjection(const atakmap::math::Matrix &simg2proj) throw (atakmap::math::NonInvertibleTransformException) :
        img2proj(simg2proj)
        {
            img2proj.createInverse(&proj2img);
        }
        
        void forward(const atakmap::core::GeoPoint *geo, atakmap::math::Point<double> *proj) override {
            atakmap::math::PointD p(geo->longitude, geo->latitude);
            return proj2img.transform(&p, proj);
        }
        
        void inverse(const atakmap::math::Point<double> *proj, atakmap::core::GeoPoint *geo) override {
            atakmap::math::PointD tmppt;
            img2proj.transform(proj, &tmppt);
            geo->set(tmppt.y, tmppt.x);
        }
        
        double getMinLatitude() override {
            throw std::logic_error("This method should never be invoked.");
        }
        
        double getMinLongitude() override {
            throw std::logic_error("This method should never be invoked.");
        }
        
        double getMaxLatitude() override {
            throw std::logic_error("This method should never be invoked.");
        }
        
        double getMaxLongitude() override {
            throw std::logic_error("This method should never be invoked.");
        }
        
        int getSpatialReferenceID() override {
            throw std::logic_error("This method should never be invoked.");
        }
        
        bool is3D() override {
            throw std::logic_error("This method should never be invoked");
        }
    };
    
    bool isDisplayable(GDALDatasetH dataset, bool checkForProjection) {
        
        auto *datasetObj = (GDALDataset *)dataset;
        
        if (strcmp(datasetObj->GetDriver()->GetDescription(), "NITF") == 0) {
            const char *irep = datasetObj->GetMetadataItem("NITF_IREP", "");
            if (irep && strcmp(irep, "NODISPLY") == 0) {
                return false;
            }
        }
        
        return !checkForProjection || (atakmap::raster::gdal::GdalDatasetProjection::getInstance(datasetObj) != nullptr);
    }

    bool isTableOfContents(const char *path);
    
    void createRecursive(const char *file, std::list<Frame> &retval, int *count, atakmap::raster::DatasetDescriptor::CreationCallback *callback);
    
    void createImpl(const char *file, bool mosaic, std::list<Frame> &frames);
    void createLayer(const char *file, int type, bool forMosaic, std::list<Frame> &frames); /*throws IOException*/;

    void createGdalLayer(const char *baseFile, const char *name, const char *uri, bool forMosaic, std::list<Frame> &frames) /*throws IOException*/;
    bool createGdalLayer(const char *derivedFrom, const char *name, const char *path, GDALDatasetH dataset, Frame &frame);
    bool createPfiLayer(const char *baseFile, const char *name, const char *uri, Frame &frame) /*throws IOException*/;
    bool createRpfLayer(const char *baseFile, const char *name, const char *uri, Frame &frame) /*throws IOException*/;
    bool createPriLayer(const char *file);
}

namespace atakmap {
    namespace raster {
        namespace gdal {
            const char *GdalLayerInfo::PROVIDER_NAME = "gdal";

            GdalLayerInfo::GdalLayerInfo() : DatasetDescriptor::Factory(PROVIDER_NAME, 2)
            {

            }

            GdalLayerInfo::~GdalLayerInfo()
                NOTHROWS
            {

            }


            unsigned short
                GdalLayerInfo::getVersion()
                const
                NOTHROWS
            {
                return 2;
            }
            
            bool GdalLayerInfo::probeFile (const char* file, DatasetDescriptor::CreationCallback &callback) const {
                int cnt = 0;
                return this->test(file, &cnt, callback);
            }
            
            GdalLayerInfo::DescriptorSet *
                GdalLayerInfo::createImpl(const char* file,       // Never NULL.
                const char* workingDir,     // May be NULL.
                CreationCallback *callback)          // May be NULL.
                const
            {
                if (atakmap::util::isFile(file)) {
                    
                    std::unique_ptr<GdalLayerInfo::DescriptorSet> retval(new GdalLayerInfo::DescriptorSet());
                    std::list<Frame> frames;
                    ::createImpl(file, false, frames);
                    
                    int num = 0;
                    for (auto it = frames.begin(); it != frames.end(); ++it) {
                        std::map<TAK::Engine::Port::String, TAK::Engine::Port::String, TAK::Engine::Port::StringLess> extraData;
                        std::stringstream descWorkDir;
                        
                        descWorkDir << workingDir << num;
                        num++;
                        
                        std::string descWorkDirStr = descWorkDir.str();
                        atakmap::util::createDir(descWorkDirStr.c_str());
                        
                        TAK::Engine::Port::String tilecacheDatabaseFile;
                        TAK::Engine::Util::IO_createTempFile(tilecacheDatabaseFile, "tilecache", ".sqlite", descWorkDirStr.c_str());
                        
                        extraData["tilecache"] = atakmap::util::getFileAsAbsolute(tilecacheDatabaseFile.get()).c_str();
                            if (it->gdalSubDataset.length() > 0)
                                extraData["gdalSubdataset"] = it->gdalSubDataset.c_str();

                        Frame *frame = &(*it);
                        retval->insert(new ImageDatasetDescriptor(frame->name.c_str(),
                                                           frame->path.c_str(),
                                                           PROVIDER_NAME,
                                                           frame->typeIsDatasetType ? frame->type.c_str() : "native",
                                                           frame->type.c_str(),
                                                           frame->width,
                                                           frame->height,
                                                           frame->numLevels,
                                                           frame->upperLeft,
                                                           frame->upperRight,
                                                           frame->lowerRight,
                                                           frame->lowerLeft,
                                                           frame->srid,
                                                           false,
                                                           descWorkDirStr.c_str(),
                                                           extraData));
                    }

                    if (retval->size() == 0)
                        return nullptr;
                    else
                        return retval.release();

                } else {
                    
                    std::list<Frame> layers;
                    std::stringstream mosaicDatabaseFileSS;
                    //XXX--
                    mosaicDatabaseFileSS << workingDir << "/" << "mosaicdb.sqlite";
                    std::string mosaicDatabaseFile = mosaicDatabaseFileSS.str();
                    
                    {
                        atakmap::raster::mosaic::ATAKMosaicDatabase db;
                        db.create(mosaicDatabaseFile.c_str());
                        db.beginTransaction();
                        int cnt = 0;
                        createRecursive(file, layers, &cnt, callback);
                        if (callback && callback->isCanceled()) {
                            return nullptr;
                        }
                        
                        if (callback)
                            callback->setProgress(-400);
                        
#if 0
                        MosaicDatabaseBuilder::CoverageCreationCallback ^cb = nullptr;
                        if (callback)
                            cb = gcnew MosaicDbBuilderCallbackAdapter(callback);
                        mosaicdb->finalizeCoverages(cb);
#endif
                        
                        db.setTransactionSuccessful();
                        db.endTransaction();
                        db.close();
                    }
                    
                    {
                        atakmap::raster::mosaic::ATAKMosaicDatabase db;
                        db.open(mosaicDatabaseFile.c_str());

                        std::map<TAK::Engine::Port::String, TAK::Engine::Port::String, TAK::Engine::Port::StringLess> extraData;
                        extraData["relativePaths"] = "false";
                    
                        std::stringstream ss;
                        ss << workingDir << "/" << "tilecache";
                        atakmap::util::deletePath(ss.str().c_str());
                        if (atakmap::util::createDir(ss.str().c_str())) {
                            extraData["tilecacheDir"] = atakmap::util::getFileAsAbsolute(ss.str().c_str()).c_str();
                        }
                        
                        MosaicDatasetDescriptor::CoverageMap coverages;
                        MosaicDatasetDescriptor::ResolutionMap resolutions;
                        MosaicDatasetDescriptor::StringVector imageryTypes;

                        std::map<std::string, mosaic::MosaicDatabase::Coverage *> dbCoverages = db.getCoverages();
                        for (auto it = coverages.begin(); it != coverages.end(); ++it) {
                            mosaic::MosaicDatabase::Coverage *coverage = db.getCoverage(std::string(it->first));
                            resolutions[it->first] = std::make_pair(coverage->minGSD, coverage->maxGSD);
                            coverages[it->first] = coverage->geometry;
                        }
                        
                        std::unique_ptr<GdalLayerInfo::DescriptorSet> retval(new GdalLayerInfo::DescriptorSet());
                        retval->insert(new atakmap::raster::MosaicDatasetDescriptor(atakmap::util::getFileName(file).c_str(),
                                                                     file,
                                                                     this->getStrategy(),//this->getType(),
                                                                     "native-mosaic",
                                                                     mosaicDatabaseFile.c_str(),
                                                                     db.getType(),
                                                                     imageryTypes,
                                                                     resolutions,
                                                                     coverages,
                                                                     4326,
                                                                     false,
                                                                     workingDir,
                                                                     extraData));
                        
                        db.close();
                        
                        return retval.release();
                    }
                }
            }
            
            bool GdalLayerInfo::test(const char *file, int *count, DatasetDescriptor::CreationCallback &callback) const {
                // Recursively check the file, and any children if it is a directory,
                // for files that can be opened by GDAL. If one is found, this
                // is probably a directory that could make a GDAL layer.
                
                if (atakmap::util::isDirectory(file)){
                    // skip PFPS bcpdata directory
                    std::string name = atakmap::util::getFileName(file);
                    
                    //XXX--fixme
#if 0
                    if (ImplConstants::IGNORE_DIRS.Contains(name->ToLower()))
                        return false;
#endif
                    
                    std::vector<std::string> children = atakmap::util::getDirContents(file);
                    for (size_t i = 0; i < children.size(); ++i) {
                        if (*count++ > static_cast<int>(callback.getProbeLimit())) {
                            return false;
                        }
                        if (test(children[i].c_str(), count, callback)) {
                            return true;
                        }
                    }
                    
                    return false;
                }
                else if (!isTableOfContents(file)) {
                    // XXX - ignore DTED
#if 0
                    std::string s = atakmap::util::toLowerCase(file);
                    s.resize(s.size() - 1);
                    System::String ^s = file->ToLower()->Substring(0, file->Length - 1);
                    if (s->EndsWith(".dt"))
                        return false;
#endif
                    
                    GDALDatasetH dataset = GDALOpen(atakmap::util::getFileAsAbsolute(file).c_str(), GA_ReadOnly);
                    if (dataset == nullptr) {
                        return false;
                    }
                    
                    GDALClose(dataset);
                    return true;
                }
                
                return false;
            }

            void GdalLayerInfo::isSupported(const char *filePath, CreationCallback *cb) const
            {
                cb->setError("Not yet implemented");
                cb->setProbeResult(false);
            }


            core::Projection *GdalLayerInfo::createDatasetProjection(const math::Matrix &m) throw (math::NonInvertibleTransformException)
            {
                return new ProjectiveMappingProjection(m);
            }

            std::string GdalLayerInfo::getURI(const char *file)
            {
                std::string ret;
                const char *proto = "file://";
#if 0
                if (file instanceof ZipVirtualFile)
                    proto = "zip://";
#endif
                ret = util::getFileAsAbsolute(file);
                for (std::size_t i = 0; i < ret.length(); ++i) {
                    if (ret[i] == ' ') {
                        ret[i] = '%';
                        ret.insert(i + 1, "20");
                        i += 2;
                    } else if (ret[i] == '#') {
                        ret[i] = '%';
                        ret.insert(i + 1, "23");
                        i += 2;
                    }
                }

                ret.insert(0, proto);
                return ret;
            }

        }
    }
}

namespace {
    enum {
        GDAL = 0,
        PFI,
        PRI,
        RPF
    };
    
    void createImpl(const char *file, bool mosaic, std::list<Frame> &frames) {
        
        using namespace atakmap::raster::pfps;
        
        int type = GDAL;
        
        // check for special types
        if (!atakmap::util::isDirectory(file)) {
#if 0
            if (!(file instanceof ZipVirtualFile)) {
#else
                if (true) {
#endif
                    bool subtyped = false;
                    std::string strout;
                    
                    std::string name = atakmap::util::getFileName(file);
                    if(name.length() == 12 && PfpsMapTypeFrame::getRpfPrettyName(&strout, name.c_str())) {
                        type = RPF;
                        subtyped = true;
                    }
                    
#if 0
                    try {
                        msclr::interop::marshal_context ctx;
                        if (!subtyped && PRIJNI::isPRI(ctx.marshal_as<const char *>(Path::GetFullPath(file)))) {
                            type = PRI;
                            subtyped = true;
                        }
                    } catch (System::Exception ^ignored) {
                        // process as normal GDAL file
                        Log::d(GdalLayerInfo::TAG, "Unexpected general error during PRI check for " + FileSystemUtils::getName(file)
                               + "; processing as normal GDAL file.");
                    }
#endif
                    
#if 0
                    try {
                        msclr::interop::marshal_context ctx;
                        if (!subtyped && PFIUtils::isPFI(ctx.marshal_as<const char *>(file))) {
                            type = PFI;
                            subtyped = true;
                        }
                    } catch (System::Exception ^ignored) {
                        // process as normal GDAL file
                        Log::d(GdalLayerInfo::TAG, "Unexpected general error during PRI check for " + file
                               + "; processing as normal GDAL file.");
                    }
#endif
                }
#if 0
                else{
                    InputStream zipInputStream = nullptr;
                    try {
                        zipInputStream = ((ZipVirtualFile)file).openStream();
                        long isPRI = PRIJNI.isPRI(zipInputStream);
                        if (isPRI > 0) {
                            type = PRI;
                            priPointerCache.put(file, isPRI);
                        }
                    } catch (Exception ignored) {
                        // process as normal GDAL file
                        Log::d(TAG, "Unexpected general error during PRI check for " + file.getName()
                               + "; processing as normal GDAL file.");
                    }finally {
                        try{
                            if(zipInputStream != nullptr){
                                zipInputStream.close();
                            }
                        }catch(Exception ^ignored){
                            
                        }
                    }
                }
#endif
            }
            
            createLayer(file, type, mosaic, frames);
    }
        
    void createLayer(const char *file, int type, bool forMosaic, std::list<Frame> &retval) {
        do {
            switch (type) {
                case PFI: {
                    std::string uri = atakmap::util::getFileAsAbsolute(file);
#if 0
                    if (file instanceof ZipVirtualFile)
                        uri = "/vsizip" + uri;
#endif
#if 0
                    try {
                        Frame ^tsInfo = createPfiLayer(file, FileSystemUtils::getName(file),
                                                       uri);
                        if (tsInfo != nullptr) {
                            retval->Add(tsInfo);
                            return;
                        }
                        else {
                            Log::w(GdalLayerInfo::TAG, "Failed to create PFI layer for " + FileSystemUtils::getName(file) + " treat as NITF.");
                        }
                    } catch(System::Exception ^e) {
                        Log::e(GdalLayerInfo::TAG, "Unexpected general error during PFI layer creation.", e);
                    }
#endif
                    type = GDAL;
                    continue;
                }
                case RPF: {
                    std::string uri = atakmap::util::getFileAsAbsolute(file);
#if 0
                    if (file instanceof ZipVirtualFile)
                        uri = "/vsizip" + uri;
#endif
#if 1
                    try {
                        Frame tsInfo;
                        
                        std::string fileName = atakmap::util::getFileName(file);
                        if (createRpfLayer(file, fileName.c_str(), uri.c_str(), tsInfo)) {
                            retval.push_back(tsInfo);
                            return;
                        }
                        else {
                            atakmap::util::Logger::log(atakmap::util::Logger::Warning, "Failed to create RPF layer for %s treat as NITF.", fileName.c_str());
                        }
                    }
                    catch (std::exception &e) {
                        atakmap::util::Logger::log(atakmap::util::Logger::Error, "Unexpected general error during RPF layer creation '%s'.", e.what());
                    }
#endif
                    type = GDAL;
                    continue;
                }
                case GDAL: {
                    std::string uri = atakmap::util::getFileAsAbsolute(file);
#if 0
                    if (file instanceof ZipVirtualFile)
                        uri = "/vsizip" + uri;
#endif
                    std::string fileName = atakmap::util::getFileName(file);
                    createGdalLayer(file, fileName.c_str(), uri.c_str(), forMosaic, retval);
                    return;
                }
                case PRI: {
#if 0
                    try {
                        Frame ^tsInfo = createPriLayer(file);
                        if (tsInfo != nullptr) {
                            retval->Add(tsInfo);
                            return;
                        }
                        else {
                            Log::w(GdalLayerInfo::TAG, "Failed to create PRI layer for " + FileSystemUtils::getName(file) + " treat as NITF.");
                        }
                    } catch (System::Exception ^e) {
                        Log::e(GdalLayerInfo::TAG, "Unexpected general error during PRI layer creation.", e);
                    }
#endif
                    type = GDAL;
                    continue;
                }
                default:
                    throw std::runtime_error("Illegal State");
            }
            break;
        } while (true);
        
        throw std::runtime_error("Illegal State");
    }
        
    bool isTableOfContents(const char *path) {
        try {
            std::string name = atakmap::util::getFileName(path);
            if (!name.length())
                return false;
            
            name = atakmap::util::toLowerCase(name);
            
            return name == "toc.xml" ||
                   name == "a.toc";
        } catch (...) {
            return false;
        }
    }
        
    void createGdalLayer(const char *baseFile, const char *name, const char *uri, bool forMosaic, std::list<Frame> &retval) {
#if 0
        if (TOC_FILE_FILTER.accept(baseFile)){
            return Collections.<DatasetDescriptor> emptySet();
        }
#else
        if (isTableOfContents(baseFile)) {
            return;
        }
#endif
#if 1
        // we'll try to short circuit RPF frame files by doing name analysis. if
        // that fails, we'll process normally
        try {
            if (strlen(name) == 12) {
                std::string rpfType;
                if (atakmap::raster::pfps::PfpsMapTypeFrame::getRpfPrettyName(&rpfType, name)) {
                    atakmap::core::GeoPoint ul;
                    atakmap::core::GeoPoint ur;
                    atakmap::core::GeoPoint lr;
                    atakmap::core::GeoPoint ll;
                    if (atakmap::raster::pfps::PfpsMapTypeFrame::coverageFromFilename(name, ul, ur, lr, ll)) {
                        Frame fbacking;
                        Frame *frame = &fbacking;
                        frame->name = name;
                        frame->path = uri;
                        frame->type = rpfType;
                        frame->width = 1536;
                        frame->height = 1536;
                        frame->resolution = atakmap::raster::DatasetDescriptor::computeGSD(1536, 1536, ul, ur, lr, ll);
                        frame->numLevels = 2;
                        frame->upperLeft.latitude = ul.latitude;
                        frame->upperLeft.longitude = ul.longitude;
                        frame->upperRight.latitude = ur.latitude;
                        frame->upperRight.longitude = ur.longitude;
                        frame->lowerRight.latitude = lr.latitude;
                        frame->lowerRight.longitude = lr.longitude;
                        frame->lowerLeft.latitude = ll.latitude;
                        frame->lowerLeft.longitude = ll.longitude;
                        frame->srid = 4326;
                        
                        retval.push_back(*frame);
                        return;
                    }
                }
            }
        }
        catch (...) {
        }
        
#endif
        GDALDatasetH dataset = nullptr;
        try {
            dataset = GDALOpen(uri, GDALAccess::GA_ReadOnly);
            if (!dataset) {
                atakmap::util::Logger::log(atakmap::util::Logger::Error, "failed to open dataset %s", uri);
                return;
            }
                                           
            // look for subdatasets
#if 0
            // XXX - insufficient documentation for the C# API to see how this is done
            IDictionary<System::String ^, System::String ^> ^subdatasets = (IDictionary<System::String ^, System::String ^>) dataset
            .GetMetadata_Dict("SUBDATASETS");
#else
            std::map<std::string, std::string> subdatasets;
#endif
            if (subdatasets.size() > 0) {
#if 0
                // build the list of subdatasets
                ISet<System::String ^> ^uris = gcnew HashSet<System::String ^>();
                IEnumerator<KeyValuePair<System::String ^, System::String ^>> ^subdatasetIter = subdatasets->GetEnumerator();
                KeyValuePair<System::String ^, System::String ^> entry;
                while (subdatasetIter->MoveNext()) {
                    entry = subdatasetIter->Current;
                    if (Regex::IsMatch(entry.Key, "SUBDATASET\\_\\d+\\_NAME")) {
                        uris->Add(entry.Value);
                    }
                }
                
                // close parent
                delete dataset;
                dataset = nullptr;
                
                // XXX - pretty names for subdatasets
                int subdatasetNum = 1;
                IEnumerator<System::String ^> ^uriIter = uris->GetEnumerator();
                System::String ^subdatasetUri;
                IDictionary<System::String ^, System::String ^> ^subdatasetExtraData;
                while (uriIter->MoveNext()) {
                    subdatasetUri = uriIter->Current;
                    try {
                        createGdalLayer(baseFile, name + "["
                                        + (subdatasetNum++) + "]", subdatasetUri, forMosaic, retval);
                    }
                    catch (System::Exception ^e) {
                        Log::e(GdalLayerInfo::TAG, "error: ", e);
                    }
                }
#endif
            }
            else {
                Frame info;
                if (!createGdalLayer(baseFile, name, baseFile, dataset, info)) {
                    goto cleanup;
                }
                info.gdalSubDataset = uri;
                retval.push_back(info);
            }
        } catch (...) { }
        
    cleanup:
        GDALClose(dataset);
    }
        
    bool createGdalLayer(const char *derivedFrom, const char *name, const char *path, GDALDatasetH datasetH, Frame &retval) {
        
        if (!isDisplayable(datasetH, true))
            return false;
        
        auto *dataset = (GDALDataset *)datasetH;
        const int width = dataset->GetRasterXSize();
        const int height = dataset->GetRasterYSize();
        
        std::unique_ptr<atakmap::raster::gdal::GdalDatasetProjection> proj(atakmap::raster::gdal::GdalDatasetProjection::getInstance(dataset));
        
        
        atakmap::core::GeoPoint ul;
        atakmap::math::PointD zero(0, 0);
        proj->inverse(&zero, &ul);
        
        atakmap::core::GeoPoint ur;
        atakmap::math::PointD wp(width - 1, 0);
        proj->inverse(&wp, &ur);
        
        atakmap::core::GeoPoint lr;
        atakmap::math::PointD whp(width - 1, height - 1);
        proj->inverse(&whp, &lr);
        
        atakmap::core::GeoPoint ll;
        atakmap::math::PointD hp(0, height - 1);
        proj->inverse(&hp, &ll);
        
        const int spatialReferenceID = proj->getNativeSpatialReferenceID();
        
        //            Log::d(TAG, "Create GdalLayer " + name + " " + width + "x" + height);
        //            Log::d(TAG, "ul=" + ul);
        //            Log::d(TAG, "ur=" + ur);
        //            Log::d(TAG, "lr=" + lr);
        //            Log::d(TAG, "ll=" + ll);
        
        const double gsd = atakmap::raster::DatasetDescriptor::computeGSD(width, height, ul, ur, lr, ll);
#if 0
        const int numLevels = Math::Min(GdalTileReader::getNumResolutionLevels(width, height, 512, 512), 6);
#else
        const int numLevels = std::min(atakmap::raster::gdal::GdalTileReader::getNumResolutionLevels(width, height, 512, 512),
                                       std::max(dataset->GetRasterBand(1)->GetOverviewCount(), 2) + 2);
#endif
        
        std::string type = "Unknown";
        bool appendResolution = false;
        const char *t = atakmap::raster::ImageryFileType::getFileType(derivedFrom);
        if (t) {
            if (strcmp(t, "RPF") == 0) {
                atakmap::raster::pfps::PfpsMapTypeFrame::getRpfPrettyName(&type, name);
                appendResolution = false;
            }
            else {
                appendResolution = true;
            }
        }
        else {
            if (strlen(name) == 12) {
                std::string rpfType;
                if (atakmap::raster::pfps::PfpsMapTypeFrame::getRpfPrettyName(&rpfType, name)) {
                    type = rpfType;
                }
            }
        }
        
        if (appendResolution) {
            TAK::Engine::Util::array_ptr<const char> cresStr(atakmap::raster::DatasetDescriptor::formatResolution(gsd));
            type += " ";
            type += cresStr.get();
        }
        
        Frame *frame = &retval;
        frame->name = name;
        frame->path = path;
        frame->type = type;
        frame->width = width;
        frame->height = height;
        frame->resolution = gsd;
        frame->numLevels = numLevels;
        frame->upperLeft.latitude =  ul.latitude;
        frame->upperLeft.longitude = ul.longitude;
        frame->upperRight.latitude =  ur.latitude;
        frame->upperRight.longitude = ur.longitude;
        frame->lowerRight.latitude =  lr.latitude;
        frame->lowerRight.longitude = lr.longitude;
        frame->lowerLeft.latitude =  ll.latitude;
        frame->lowerLeft.longitude = ll.longitude;
        frame->srid = spatialReferenceID;
        
        return true;
    }
        
    void createRecursive(const char *file, std::list<Frame> &retval, int *count, atakmap::raster::DatasetDescriptor::CreationCallback *callback) {
        (*count)++;
        int tmpCount = *count;
        if (callback != nullptr) {
            if (callback->isCanceled())
                return;
            
            if((tmpCount % 10) == 0)
                callback->setProgress(tmpCount);
        }
        
#if 0
        if (file.isFile() && file.getAbsolutePath().toUpperCase(Locale.US).endsWith(".ZIP")) {
            try {
                file = gcnew ZipVirtualFile(file);
            } catch (IOException e) {
                Log::w(TAG, "Failed to open zip: " + file.getAbsolutePath());
                return;
            }
            createRecursive(file, retval, count, callback);
        } else
#endif
            if (atakmap::util::isFile(file)) {
                // XXX - ignoring DTED due to observed crash that could not be
                //       reproduced on development machine/debugger
                std::string fileStr = atakmap::util::toLowerCase(file);
                if (fileStr.length() > 4 &&
                    fileStr[fileStr.length() - 4] == '.' &&
                    fileStr[fileStr.length() - 3] == 'd' &&
                    fileStr[fileStr.length() - 2] == 't' &&
                    (fileStr[fileStr.length() - 1] == '0' || fileStr[fileStr.length() - 1] == '1' || fileStr[fileStr.length() - 1] == '2')) {
                    return;
                }
                createImpl(file, true, retval);
            }
            else /*TODO--if(!ImplConstants::IGNORE_DIRS.Contains(FileSystemUtils::getName(file)->ToLower()))*/ {
                std::vector<std::string> children = atakmap::util::getDirContents(file);
                for (size_t i = 0; i < children.size(); i++) {
                    createRecursive(children[i].c_str(), retval, count, callback);
                    if (callback && callback->isCanceled())
                        return;
                }
            }
        
    }
        
    bool createPfiLayer(const char *baseFile, const char *name, const char *uri, Frame &frame) {
        return false;
    }
    
    bool createRpfLayer(const char *baseFile, const char *name, const char *uri, Frame &frame) {
        return false;
    }
    
    bool createPriLayer(const char *file) {
        return false;
    }
}

