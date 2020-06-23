#ifndef ATAKMAP_RENDERER_GLTEXTUREATLAS_H_INCLUDED
#define ATAKMAP_RENDERER_GLTEXTUREATLAS_H_INCLUDED

#include <map>
#include <set>
#include <string>
#include <inttypes.h>
#include "renderer/Bitmap.h"
#include "port/Platform.h"
#include "math/Rectangle.h"

namespace atakmap {
    namespace renderer {

        struct ENGINE_API Rect {
            int x;
            int y;
            int width;
            int height;
            bool splitFreeHorizontal;

            Rect(int x, int y, int width, int height, bool splitFreeHorizontal);

        private:
            int instance;

            friend struct GLTextureAtlasRectComp;
        };

        struct ENGINE_API GLTextureAtlasRectComp
        {
            bool operator() (const Rect &x, const Rect &y) const;
        };


        class ENGINE_API GLTextureAtlas {
        public:
            GLTextureAtlas(int texSize);
            GLTextureAtlas(int texSize, bool splitHorizontal);
            GLTextureAtlas(int texSize, int iconSize);
            ~GLTextureAtlas();  

            void release();
            void releaseTexture(int textureId);

            int64_t getTextureKey(std::string uri);
            bool isFixedImageSize();
            math::Rectangle<float> getImageRect(int64_t key);

            math::Rectangle<float> getImageRect(int64_t key, bool normalized);

            /*
             * Return == rect if non-nullptr.  If nullptr, return is new
             * RectF allocated with new.
             */
            math::Rectangle<float> *getImageRect(int64_t key, bool normalized, math::Rectangle<float> *rect);
            int getImageWidth(int64_t key);
            int getImageHeight(int64_t key);
            int getTextureSize();
            int getTexId(int64_t key);
            int getIndex(int64_t key);
            int getImageTextureOffsetX(int64_t key);
            int getImageTextureOffsetY(int64_t key);
            int64_t addImage(std::string uri, Bitmap bitmap) throw (std::invalid_argument);

        private:
            void commonInit(int texSize, int iconSize, bool splitHorizontal);

            std::map<std::string, int64_t> uriToKey;
            int texSize;
            int freeIndex;
            int currentTexId;

            std::map<int64_t, Rect> keyToIconRect;
            bool splitFreeHorizontal;
            std::set<Rect, GLTextureAtlasRectComp> freeList;

            bool fixedIconSize;
            int iconSize;

        };
    }
}

#endif


