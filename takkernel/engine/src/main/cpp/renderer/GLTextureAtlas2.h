#ifndef TAK_ENGINE_RENDERER_GLTEXTUREATLAS2_H_INCLUDED
#define TAK_ENGINE_RENDERER_GLTEXTUREATLAS2_H_INCLUDED

#include <map>
#include <set>
#include <string>
#include <cstdint>

#include "math/Rectangle.h"
#include "renderer/Bitmap2.h"
#include "port/Platform.h"
#include "util/Error.h"
#include "util/Memory.h"
#include "util/NonCopyable.h"

namespace TAK {
    namespace Engine {
        namespace Renderer {

            class ENGINE_API GLTextureAtlas2 : TAK::Engine::Util::NonCopyable
            {
            private :
                struct Rect;
                struct SheetRects;
            private :
                struct GLTextureAtlasRectComp
                {
                    bool operator() (const Rect &x, const Rect &y) const;
                };
            public:
                GLTextureAtlas2(const std::size_t texSize) NOTHROWS;
                GLTextureAtlas2(const std::size_t texSize, const bool splitHorizontal) NOTHROWS;
                GLTextureAtlas2(const std::size_t texSize, const std::size_t iconSize) NOTHROWS;
            public :
                ~GLTextureAtlas2() NOTHROWS;
            public :
                Util::TAKErr release() NOTHROWS;
                Util::TAKErr releaseTexture(const int textureId) NOTHROWS;

                Util::TAKErr getTextureKey(int64_t *value, const char *uri) const NOTHROWS;
                bool isFixedImageSize() const NOTHROWS;
                Util::TAKErr getImageRect(atakmap::math::Rectangle<float> *value, const int64_t key) const NOTHROWS;
                Util::TAKErr getImageRect(atakmap::math::Rectangle<float> *value, const int64_t key, const bool normalized) const NOTHROWS;

                Util::TAKErr getImageWidth(std::size_t *value, const int64_t key) const NOTHROWS;
                Util::TAKErr getImageHeight(std::size_t *value, const int64_t key) const NOTHROWS;
                std::size_t getTextureSize() const NOTHROWS;
                Util::TAKErr getTexId(int *value, const int64_t key) const NOTHROWS;
                Util::TAKErr getIndex(int * value, const int64_t key) const NOTHROWS;
                Util::TAKErr getImageTextureOffsetX(std::size_t *value, const int64_t key) const NOTHROWS;
                Util::TAKErr getImageTextureOffsetY(std::size_t *value, const int64_t key) const NOTHROWS;
                Util::TAKErr addImage(int64_t *value, const char *uri, const Bitmap2 &bitmap) NOTHROWS;
            private:
                std::map<std::string, int64_t> uriToKey;
                const std::size_t texSize;
                int freeIndex;
                int currentTexId;

                std::map<int64_t, Rect> keyToIconRectOverflow;
                const bool splitFreeHorizontal;
                std::set<Rect, GLTextureAtlasRectComp> freeList;

                const bool fixedIconSize;
                const std::size_t iconSize;

                std::unique_ptr<SheetRects> sheetRects;
                SheetRects *currentSheetRects;
            };

            struct GLTextureAtlas2::Rect
            {
                int x;
                int y;
                size_t width;
                size_t height;
                bool splitFreeHorizontal;
            private :
                Rect();
            public :
                Rect(const int x, const int y, const size_t width, const size_t height, const bool splitFreeHorizontal);
            private:
                int instance;

                friend struct GLTextureAtlasRectComp;
                friend struct SheetRects;
            };

            struct GLTextureAtlas2::SheetRects : TAK::Engine::Util::NonCopyable
            {
            public :
                SheetRects(const int texid) NOTHROWS;
            public :
                const SheetRects *find(const int tex_id) const NOTHROWS;
            public :
                const int texid;
                const int limit;
                Util::array_ptr<GLTextureAtlas2::Rect> rectPtr;
                GLTextureAtlas2::Rect * const rect;
                int numRects;
                std::unique_ptr<GLTextureAtlas2::SheetRects> next;

                
            };
        }
    }
}

#endif
