#ifndef TAK_ENGINE_RENDERER_GLTEXTURECACHE2_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLTEXTURECACHE2_H_INCLUDED

#include <map>
#include <string>

#include "renderer/GL.h"

#include "port/Platform.h"
#include "renderer/GLTexture2.h"
#include "renderer/Bitmap2.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {

            class ENGINE_API GLTextureCache2
            {
            public:
                struct ENGINE_API Entry;
                typedef std::unique_ptr<Entry, void(*)(const Entry *)> EntryPtr;
            private :
                struct BidirectionalNode;
            public:
                GLTextureCache2(const std::size_t maxSize) NOTHROWS;
            public :
                ~GLTextureCache2() NOTHROWS;
            public :
                Util::TAKErr get(const Entry **value, const char *key) const NOTHROWS;
                Util::TAKErr remove(EntryPtr &value, const char *key) NOTHROWS;
                Util::TAKErr put(const char *key, EntryPtr &&value) NOTHROWS;
                Util::TAKErr clear() NOTHROWS;
                Util::TAKErr deleteEntry(const char *key) NOTHROWS;
            public :
                static Util::TAKErr sizeOf(std::size_t *value, const GLTexture2 &texture) NOTHROWS;
            private:
                Util::TAKErr trimToSize() NOTHROWS;
            private :
                std::map<std::string, BidirectionalNode *> nodeMap;
                BidirectionalNode *head;
                BidirectionalNode *tail;
                std::size_t maxSize;
                std::size_t size;
                std::size_t count;
            };

            struct GLTextureCache2::BidirectionalNode
            {
                BidirectionalNode *prev;
                BidirectionalNode *next;
                std::string key;
                GLTextureCache2::EntryPtr value;

                BidirectionalNode(BidirectionalNode *prev, std::string key, EntryPtr &&value) NOTHROWS;
            };

            struct ENGINE_API GLTextureCache2::Entry
            {
            public:
                typedef std::unique_ptr<void, void(*)(const void *)> OpaquePtr;
            public:
                GLTexture2Ptr texture;
                std::unique_ptr<float, void(*)(const float *)> textureCoordinates;
                std::unique_ptr<float, void(*)(const float *)> vertexCoordinates;
                std::unique_ptr<uint16_t, void(*)(const uint16_t *)> indices;
                std::size_t numVertices;
                std::size_t numIndices;
                std::size_t hints;
                OpaquePtr opaque;
                std::size_t opaqueSize;
            public:
                Entry() NOTHROWS;
                Entry(GLTexture2Ptr &&texture) NOTHROWS;
                Entry(GLTexture2Ptr &&texture,
                      std::unique_ptr<float, void(*)(const float *)> &&textureCoordinates,
                      std::unique_ptr<float, void(*)(const float *)> &&vertexCoordinates,
                      const std::size_t numVertices) NOTHROWS;
                Entry(GLTexture2Ptr &&texture,
                      std::unique_ptr<float, void(*)(const float *)> &&textureCoordinates,
                      std::unique_ptr<float, void(*)(const float *)> &&vertexCoordinates,
                      const std::size_t numVertices,
                      const std::size_t hints,
                      OpaquePtr opaque) NOTHROWS;
                Entry(GLTexture2Ptr &&texture,
                      std::unique_ptr<float, void(*)(const float *)> &&textureCoordinates,
                      std::unique_ptr<float, void(*)(const float *)> &&vertexCoordinates,
                      std::unique_ptr<uint16_t, void(*)(const uint16_t *)> &&indices,
                      const std::size_t numVertices,
                      const std::size_t numIndices,
                      const std::size_t hints,
                      OpaquePtr opaque) NOTHROWS;                
            private:
                Entry(Entry &&other);
            public:
                bool hasHint(int flags) const;
            private:
                friend class GLTextureCache2;
            };
        }
    }
}
#endif
