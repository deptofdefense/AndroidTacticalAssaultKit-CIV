#ifndef ATAKMAP_RASTER_TILEPYRAMID_TILESETINFO_H_INCLUDED
#define ATAKMAP_RASTER_TILEPYRAMID_TILESETINFO_H_INCLUDED

#include <map>

#include "string/String.hh"
#include "base/RefCount.hh"

#include "util/IO.h"

#include "raster/ImageDatasetDescriptor.h"

namespace atakmap {
    namespace core {
        class GeoPoint;
    }
    
    namespace raster {
        class DatasetDescriptor;
        class ImageDatasetDescriptor;
        
        namespace tilepyramid {
            
            class TilesetInfo
            {
            public :
                class Builder;
                
                static const char * const NETT_WARRIOR_TILES_COLUMN_NAMES[];
                
            public:
                
            private :
                /*TODO(bergeronj)--static System::Collections::Generic::ISet<PGSC::String> * const NETT_WARRIOR_TILES_TABLE_COLUMN_NAMES = gcnew System::Collections::Generic::HashSet<PGSC::String >();
                static TilesetInfo();*/
                
            private:
                const atakmap::raster::ImageDatasetDescriptor *layerInfo;
                void (*layerInfoDeleter)(const atakmap::raster::DatasetDescriptor *);
            public:
                TilesetInfo(const atakmap::raster::ImageDatasetDescriptor *layerInfo);
                TilesetInfo(const atakmap::raster::ImageDatasetDescriptor *info, void (*layerInfoDeleter)(const atakmap::raster::DatasetDescriptor *));
                ~TilesetInfo();
                
            public:
                const atakmap::raster::ImageDatasetDescriptor *getInfo() const;
            public:
                static atakmap::raster::DatasetDescriptor *parse(const char *file);
            private:
                static atakmap::raster::DatasetDescriptor *parseZip(const char *file);
                static atakmap::raster::DatasetDescriptor *parseSQLiteDb(const char *file);
            public:
                bool operator==(const TilesetInfo &other) const;
                bool operator!=(const TilesetInfo &other) const;
            private:
                /*TODO(bergeronj)--static atakmap::raster::DatasetDescriptor *_parseXml(atakmap::util::DataInput &in, PGSC::String uri);
                static void _parseCoverage(System::Xml::XmlNode *node, array<atakmap::core::GeoPoint *> *_fourCorners);
                static bool _sanityCheckCoverage(array<atakmap::core::GeoPoint *> *_fourCorners, double _north, double _east, double _south, double _west);
                static System::Xml::XmlNode *_fetchNode(System::Xml::XmlDocument *doc, PGSC::String name);
                static double _fetchDoubleNode(System::Xml::XmlDocument *doc, PGSC::String name, double fallback);
                static int _fetchIntNode(System::Xml::XmlDocument *doc, PGSC::String name, int fallback);
                static PGSC::String _fetchStringNode(System::Xml::XmlDocument *doc, PGSC::String name, PGSC::String fallback);
                static bool _fetchBooleanNode(System::Xml::XmlDocument *doc, PGSC::String name, bool fallback);
                static array<double> *_fetchBounds(System::Xml::XmlDocument *doc, PGSC::String name, double s, double w, double n, double e);*/
                public :
                int getTilePixelWidth() const;
                int getTilePixelHeight() const;
                double getZeroWidth() const;
                double getZeroHeight() const;
                double getGridOriginLat() const;
                double getGridOriginLng() const;
                int getGridOffsetX() const;
                int getGridOffsetY() const;
                int getGridWidth() const;
                int getGridHeight() const;
                const char *getImageExt() const;
                bool isOverview() const;
                int getLevelCount() const;
                Builder buildUpon();
                bool isArchive() const;
            private:
                bool _isArchiveUri;
                public :
                static bool isZipArchive(const char *path);
                static bool isSQLiteDb(const char *file);
            };
            
            class TilesetInfo::Builder
            {
            public:
                Builder(const char *provider, const char *datasetType);
                
            public:
                Builder &setName(const char *name);
                
                Builder &setLevelCount(int levelCount);
                Builder &setZeroWidth(double zeroWidth);
                Builder &setZeroHeight(double zeroHeight);
                Builder &setOverview(bool isOverview);
                Builder &setUri(const char *uri);
                Builder &setTilePixelWidth(int tilePixelWidth);
                Builder &setTilePixelHeight(int tilePixelHeight);
                
                Builder &setImageExt(const char *imageExt);
                
                Builder &setPathStructure(const char *pathStructure);
                
                Builder &setFourCorners(const atakmap::core::GeoPoint &sw,
                                        const atakmap::core::GeoPoint &nw,
                                        const atakmap::core::GeoPoint &ne,
                                        const atakmap::core::GeoPoint &se);
                
                Builder &setSpatialReferenceID(int srid);
                
                Builder &setLevelOffset(int l);
                
                Builder &setGridOriginLat(double off);
                
                Builder &setGridOriginLng(double off);
                Builder &setGridOffsetX(int off);
                Builder &setGridOffsetY(int off);
                Builder &setGridWidth(int w);
                Builder &setGridHeight(int h);
                Builder &setSubpath(const char *p);
                Builder &setExtra(const char *key, const char *value);
                Builder &setSupportSpi(const char *spiClass);
                Builder &setIsOnline(bool isOnline);
                Builder &setWorkingDir(const char *workingDir);
                Builder &setImageryType(const char *imageryType);
                atakmap::raster::DatasetDescriptor *build();
            private:
                const PGSC::String provider;
                const PGSC::String datasetType;
                PGSC::String imageryType;
                PGSC::String name;
                atakmap::core::GeoPoint sw;
                atakmap::core::GeoPoint nw;
                atakmap::core::GeoPoint ne;
                atakmap::core::GeoPoint se;
                int _levelCount;
                double _zeroWidth;
                double _zeroHeight;
                int _tilePixelWidth;
                int _tilePixelHeight;
                bool _isOverview;
                PGSC::String _uri;
                PGSC::String _imageExt;
                PGSC::String _pathStructure;
                int srid;
                int levelOffset = 0;
                double gridOriginLat = -90;
                double gridOriginLng = -180;
                int gridOffsetX = -1;
                int gridOffsetY = -1;
                int gridWidth = -1;
                int gridHeight = -1;
                PGSC::String subpath;
                PGSC::String supportSpi;
                bool isOnline = false;
                PGSC::String workingDir;
                
                std::map<PGSC::String, PGSC::String, PGSC::StringLess> extra;
            };
            
            class TilesetInfoDatasetDescriptorFactory : public raster::DatasetDescriptor::Factory {
            public:
                TilesetInfoDatasetDescriptorFactory();
                virtual ~TilesetInfoDatasetDescriptorFactory() throw ();
                
                //
                // Returns the parse version associated with this Factory.  The parse
                // version should be incremented every time the Factory's implementation is
                // modified to produce different results.
                //
                // The version number 0 is a reserved value that should never be returned.
                //
                virtual unsigned short getVersion() const throw();
                
            private:
                
                typedef DatasetDescriptor::CreationCallback CreationCallback;
                typedef DatasetDescriptor::DescriptorSet    DescriptorSet;
                
                DescriptorSet*
                createImpl (const char* filePath,       // Never NULL.
                            const char* workingDir,     // May be NULL.
                            CreationCallback*)          // May be NULL.
                const;
                
                virtual
                bool
                probeFile (const char* file,
                           CreationCallback&)
                const;
            };
        }
    }
}


#endif