#ifndef ATAKMAP_RENDERER_GLTEXTURECACHE_H_INCLUDED
#define ATAKMAP_RENDERER_GLTEXTURECACHE_H_INCLUDED

#include "GLTexture.h"
#include "renderer/GL.h"
#include "Bitmap.h"
#include <map>
#include <string>

namespace atakmap
{
    namespace renderer
    {

        class GLTextureCache {
        public:
            // Any arg can be NULL. Non-NULL needs to be disposed completely by impl
            typedef void(*disposeEntryFunc)(GLTexture *, float *vertexCoords, short *indices, float *textureCoords, void *opaque);

            class Entry {
            private:
                friend class GLTextureCache;
            public :
                GLTexture *texture;
                float *textureCoordinates;
                float *vertexCoordinates;
                short *indices;
                int numVertices;
                int numIndices;
                int hints;
                void *opaque;
            private :
                disposeEntryFunc disposer;
            public :
                Entry();
            private :
                Entry(disposeEntryFunc disposeFunc, GLTexture *texture, 
                      float *vertexCoordinates, int numVertices,
                      float *textureCoordinates,
                      int hints, void *opaque);

                Entry(disposeEntryFunc disposeFunc, GLTexture *texture,
                      float *vertexCoordinates, int numVertices, 
                      short *indices, int numIndices,
                      float *textureCoordinates,
                      int hints, void *opaque);

                void commonInit(disposeEntryFunc disposeFunc, GLTexture *texture,
                                float *vertexCoordinates, int numVertices,
                                short *indices, int numIndices,
                                float *textureCoordinates,
                                int hints, void *opaque);

                void invokeDisposer();

            public:
                bool hasHint(int flags);
            };


        public:
            GLTextureCache(int maxSize);
            ~GLTextureCache();

            bool get(std::string key, Entry *val) throw (std::out_of_range);
            bool remove(std::string key, Entry *val) throw (std::out_of_range);

            /**
             * Cache Insert Functions
             *
             * When the client inserts data in the cache, he immediately
             * relinquishes all memory management and ownership to the cache.
             * A 'disposeEntryFunc' must be specified to allow the cache the
             * means to autonomously dispose of any client side allocated
             * memory that is associated with the cache entry.
             */
            void put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture);
            void put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, int hints, void *opaque);
            void put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, float *vertexCoords,
                     int numVertices, float *texCoords);
            void put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, float *vertexCoords,
                     int numVertices, float *texCoords, int hints, void *opaque);
            void put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, float *vertexCoords,
                     int numVertices, short *indices, int numIndices,
                     float *textureCoordinates,
                     int hints, void *opaque);
            void clear();
            void deleteEntry(std::string key) throw (std::out_of_range);
            static int sizeOf(GLTexture *texture);

        private:
            class BidirectionalNode {
            public:
                BidirectionalNode *prev;
                BidirectionalNode *next;
                std::string key;
                Entry value;

                BidirectionalNode(BidirectionalNode *prev, std::string key, Entry value);
            };


            void putImpl(std::string key, Entry entry);
            void trimToSize();
            

            std::map<std::string, BidirectionalNode *> nodeMap;
            BidirectionalNode *head;
            BidirectionalNode *tail;
            int maxSize;
            int size;
            int count;
            
            
        };
    }
}

#endif
