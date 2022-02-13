#ifndef ATAKMAP_RENDERER_GLTEXTURECACHE_H_INCLUDED
#define ATAKMAP_RENDERER_GLTEXTURECACHE_H_INCLUDED

#include "GLTexture.h"
#include "renderer/GL.h"
#include "Bitmap.h"
#include <map>
#include <string>
#include <stdexcept>

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
                disposeEntryFunc disposer_;
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

                void commonInit(disposeEntryFunc dispose_func, GLTexture *gl_texture,
                                float *vertex_coordinates, int num_vertices,
                                short *indices_array, int num_indices,
                                float *texture_coordinates,
                                int hint_flags, void *opaque_ptr);

                void invokeDisposer();

            public:
                bool hasHint(int flags);
            };


        public:
            GLTextureCache(std::size_t maxSize);
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
            static std::size_t sizeOf(GLTexture *texture);

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
            

            std::map<std::string, BidirectionalNode *> node_map_;
            BidirectionalNode *head_;
            BidirectionalNode *tail_;
            std::size_t max_size_;
            std::size_t size_;
            int count_;
            
            
        };
    }
}

#endif
