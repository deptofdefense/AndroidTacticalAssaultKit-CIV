#ifndef ATAKMAP_RASTER_MOBAC_MOBACTILECLIENT_H_INCLUDED
#define ATAKMAP_RASTER_MOBAC_MOBACTILECLIENT_H_INCLUDED

#include <cstdint>
#include <set>

#include "thread/Mutex.h"

namespace atakmap {
    namespace db {
        class Database;
        class Statement;
    }
    namespace renderer {
        struct Bitmap;
    }

    namespace raster {
        namespace mobac {
            class MobacMapSource;
            struct MobacMapTile;

            class MobacTileClient
            {
            private :
                struct TileRecord
                {
                    TileRecord();
                    ~TileRecord();

                    int64_t expiration;
                    uint8_t *data;
                    size_t dataLength;
                };
            public :
                class DownloadErrorCallback;
            private :
                //static MobacTileClient();
            public :
                MobacTileClient(MobacMapSource *mapSource, const char *offlineCachePath);
                ~MobacTileClient();
            public :
                void setOfflineMode(bool offlineOnly);
                bool isOfflineMode();
                void close();
                bool loadTile(atakmap::renderer::Bitmap *tile, int zoom, int x, int y/*, BitmapFactory.Options opts*/, MobacTileClient::DownloadErrorCallback *callback);
                bool cacheTile(int zoom, int x, int y, MobacTileClient::DownloadErrorCallback *callback);
            private :
                /** library allocates record->data, caller must free */
                bool checkTile(int zoom, int x, int y, TileRecord *record);
                void updateCache(int64_t index, MobacMapTile *tile, bool update);
            public :
#if 0
                static const char *TAG = "MobacTileClient";
                static int64_t ONE_WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L;
#endif
            private :
#if 0
                static BitmapFactory.Options ^ const CACHE_OPTS = gcnew BitmapFactory.Options();
#endif
            private :
                MobacMapSource *mapSource;
                atakmap::db::Database *offlineCache;
#if 0
                System::Threading::ThreadLocal<atakmap::db::Statement ^> updateAccessStmt;
                System::Threading::ThreadLocal<atakmap::db::Statement ^> updateCatalogStmt;
                System::Threading::ThreadLocal<atakmap::db::Statement ^> insertCatalogStmt;
                System::Threading::ThreadLocal<atakmap::db::Statement ^> insertTileStmt;
                System::Threading::ThreadLocal<atakmap::db::Statement ^> updateTileStmt;
                System::Threading::ThreadLocal<atakmap::db::Statement ^> queryTileStmt;
#endif
                bool offlineMode;
                int64_t defaultExpiration;
                int64_t cacheLimit;
                std::set<int64_t> pendingCacheUpdates;

                TAK::Engine::Thread::Mutex mutex;
            };

            class MobacTileClient::DownloadErrorCallback
            {
            public:
                virtual void tileDownloadError(int zoom, int x, int y, const char *msg) = 0;
            };
        }
    }
}

#endif // ATAKMAP_RASTER_MOBAC_MOBACTILECLIENT_H_INCLUDED
