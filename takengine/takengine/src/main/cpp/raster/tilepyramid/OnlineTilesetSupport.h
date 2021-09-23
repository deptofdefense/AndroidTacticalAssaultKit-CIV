
#ifndef ATAKMAP_RASTER_TILEPYRAMID_ONLINETILESETSUPPORT_H_INCLUDED
#define ATAKMAP_RASTER_TILEPYRAMID_ONLINETILESETSUPPORT_H_INCLUDED

namespace atakmap {
    namespace raster {
        namespace tilepyramid {
            
            class OnlineTilesetSupport
            {
            public:
                virtual ~OnlineTilesetSupport();
                virtual void setOfflineMode(bool offlineOnly) = 0;
                virtual bool isOfflineMode() = 0;
            };
        }
    }
}

#endif