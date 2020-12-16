#include "raster/ImageryFileType.h"

#include <algorithm>
#include <set>
#include <cctype>

#include "raster/PrecisionImageryFactory.h"
#include "raster/pfps/PfpsMapTypeFrame.h"
#include "thread/Lock.h"
#include "thread/Mutex.h"
#include "util/Memory.h"

using namespace atakmap::raster;

using namespace TAK::Engine::Raster;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::raster::pfps;

namespace
{
    std::set<const ImageryFileType *> types;

    class RPFImageryFileType : public ImageryFileType
    {
    public:
        RPFImageryFileType();
    private:
        bool test(const char *path) const override;
    };

    class PRIImageryFileType : public ImageryFileType
    {
    public:
        PRIImageryFileType();
    private:
        bool test(const char *path) const override;;
    };

    class PFIImageryFileType : public ImageryFileType
    {
    public:
        PFIImageryFileType();
    private:
        bool test(const char *path) const override;
    };

#define ARRAY(...) { __VA_ARGS__ }
#define EXT_IMG_FT_DECL(var, desc, exts, cnt) \
    const char *var##_exts[cnt] = exts; \
    ExtensionImageryFileType var(desc, var##_exts, cnt);

    EXT_IMG_FT_DECL(geotiff, "Geotiff", ARRAY("tif", "tiff", "gtif"), 3);
    EXT_IMG_FT_DECL(gpkg, "Geopackage", ARRAY("gpkg"), 1);
    EXT_IMG_FT_DECL(mbtiles, "mbtiles", ARRAY("mbtiles"), 1);
    EXT_IMG_FT_DECL(mrsid, "MrSID", ARRAY("sid", "mg2", "mg3", "mg4"), 4);
    EXT_IMG_FT_DECL(jp2, "JP2", ARRAY("jp2", "j2k", "j2c"), 3);
    EXT_IMG_FT_DECL(dted, "DTED", ARRAY("dt0", "dt1", "dt2", "dt3"), 4);
    EXT_IMG_FT_DECL(ntf, "NITF", ARRAY("ntf", "nitf", "nsf", "nisf"), 4);
    EXT_IMG_FT_DECL(ecw, "ECW", ARRAY("ecw"), 1);

#undef ARRAY
#undef EXT_IMG_FT_DECL

    RPFImageryFileType rpf;
    PFIImageryFileType pfi;
    PRIImageryFileType pri;

    bool registerTypes();
}


Mutex ImageryFileType::mutex(TEMT_Recursive);

ImageryFileType::ImageryFileType(const char *desc_)
{
    char *str = new char[strlen(desc_)+1];
    memcpy(str, desc_, strlen(desc_) + 1);
    this->desc = str;
}

ImageryFileType::~ImageryFileType()
{
    delete[] desc;
}

const char *ImageryFileType::getDescription() const
{
    return this->desc;
}

const char *ImageryFileType::getFileType(const char *file)
{
    Lock lock(ImageryFileType::mutex);
    static bool registered = registerTypes();

    std::set<const ImageryFileType *>::iterator it;
    for (it = types.begin(); it != types.end(); it++)
        if ((*it)->test(file))
            return (*it)->getDescription();
    return nullptr;
}

void ImageryFileType::registerType(const ImageryFileType *type)
{
    Lock lock(ImageryFileType::mutex);
    types.insert(type);
}

void ImageryFileType::unregisterType(const ImageryFileType *type)
{
    Lock lock(ImageryFileType::mutex);
    types.erase(type);
}

ExtensionImageryFileType::ExtensionImageryFileType(const char *desc, const char **ext, const size_t numExt) :
    ImageryFileType(desc)
{
    for (size_t i = 0; i < numExt; i++)
        extensions.push_back(ext[i]);
}

bool ExtensionImageryFileType::test(const char *path) const
{
    int len = 0;
    int extIdx = -1;

    while (path[len]) {
        if (path[len] == '.')
            extIdx = len + 1;
        len++;
    }
    if (extIdx < 0 || extIdx == len)
        return false;

    const size_t extLen = (len - (unsigned)extIdx);
    TAK::Engine::Util::array_ptr<char> extLower(new char[extLen+1]);
    for (size_t i = 0; i < extLen; i++)
        extLower.get()[i] = std::tolower(path[extIdx+i]);
    extLower.get()[extLen] = '\0';

    return (std::find(extensions.begin(), extensions.end(), extLower.get()) != extensions.end());
}

namespace
{
    RPFImageryFileType::RPFImageryFileType() :
        ImageryFileType("RPF")
    {}

    bool RPFImageryFileType::test(const char *path) const
    {
        atakmap::core::GeoPoint ignored;
        return PfpsMapTypeFrame::coverageFromFilepath(path,
                                                      ignored,
                                                      ignored,
                                                      ignored,
                                                      ignored);
    }

    PRIImageryFileType::PRIImageryFileType() :
        ImageryFileType("PRI")
    {}

    bool PRIImageryFileType::test(const char *path) const
    {
#if 0
        return PRI::isPRI(path);
#else
        return false;
#endif
    }
    
    PFIImageryFileType::PFIImageryFileType() :
        ImageryFileType("PFI")
    {}

    bool PFIImageryFileType::test(const char *path) const
    {
        return PrecisionImageryFactory_isSupported(path, "PFI") == TE_Ok;
    }

    bool registerTypes()
    {
        ImageryFileType::registerType(&geotiff);
        ImageryFileType::registerType(&gpkg);
        ImageryFileType::registerType(&mbtiles);
        ImageryFileType::registerType(&mrsid);
        ImageryFileType::registerType(&jp2);
        ImageryFileType::registerType(&dted);
        ImageryFileType::registerType(&ntf);
        ImageryFileType::registerType(&ecw);
        ImageryFileType::registerType(&rpf);
        ImageryFileType::registerType(&pfi);
        ImageryFileType::registerType(&pri);

        return true;
    }
}
