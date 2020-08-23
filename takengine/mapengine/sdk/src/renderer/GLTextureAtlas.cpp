#include "GLTextureAtlas.h"

#include <cmath>

namespace
{

struct ScopeLocalBitmap
{
    ScopeLocalBitmap() :
        release(false)
    {
        bitmap.data = nullptr;
        bitmap.getScaledInstance = nullptr;
        bitmap.releaseData = nullptr;
    }
    ~ScopeLocalBitmap()
    {
        if(release && bitmap.data)
            bitmap.releaseData(bitmap);
    }

    atakmap::renderer::Bitmap bitmap;
    bool release;
};

}

namespace atakmap {
    namespace renderer {

        Rect::Rect(int x, int y, int width, int height, bool splitFreeHorizontal) : x(x), y(y), width(width), height(height), splitFreeHorizontal(splitFreeHorizontal)
        {
            static int nextInstance = 0;
            instance = nextInstance++;
        }


        void GLTextureAtlas::commonInit(int ts, int is, bool sh)
        {
            iconSize = is;
            texSize = 1 << (int)ceil(log(ts) / log(2));

            fixedIconSize = (iconSize > 0);

            freeIndex = 0;
            currentTexId = 0;

            splitFreeHorizontal = sh;
        }

        GLTextureAtlas::GLTextureAtlas(int texSize)
        {
            commonInit(texSize, 0, false);
        }

        GLTextureAtlas::GLTextureAtlas(int texSize, bool splitHorizontal)
        {
            commonInit(texSize, 0, splitHorizontal);
        }

        GLTextureAtlas::GLTextureAtlas(int texSize, int iconSize)
        {
            commonInit(texSize, iconSize, false);
        }

        GLTextureAtlas::~GLTextureAtlas()
        {
        }

        void GLTextureAtlas::release()
        {
            std::set<int> texIds;
            std::map<std::string, int64_t>::iterator iter;
            for (iter = uriToKey.begin(); iter != uriToKey.end(); ++iter)
                texIds.insert(getTexId(iter->second));

            uriToKey.clear();
            keyToIconRect.clear();

            std::set<int>::iterator siter;
            for (siter = texIds.begin(); siter != texIds.end(); ++siter)
                releaseTexture(*siter);
        }

        void GLTextureAtlas::releaseTexture(int textureId)
        {
            int64_t key;
            std::map<std::string, int64_t>::iterator iter;
            for (iter = uriToKey.begin(); iter != uriToKey.end(); ) {
                key = iter->second;
                if (getTexId(key) == textureId) {
                    auto tmp = iter;
                    tmp++;
                    uriToKey.erase(iter);
                    iter = tmp;
                    keyToIconRect.erase(key);
                } else {
                    iter++;
                }
            }

            if (currentTexId == textureId) {
                currentTexId = 0;
                freeList.clear();
            }

            GLuint tid = textureId;
            glDeleteTextures(1, &tid);
        }

        int64_t GLTextureAtlas::getTextureKey(std::string uri)
        {
            int64_t retval = 0L;
            std::map<std::string, int64_t>::iterator entry;
            entry = uriToKey.find(uri);
            if (entry != uriToKey.end())
                retval = entry->second;
            return retval;
        }

        bool GLTextureAtlas::isFixedImageSize()
        {
            return fixedIconSize;
        }

        math::Rectangle<float> GLTextureAtlas::getImageRect(int64_t key)
        {
            math::Rectangle<float> rect(0, 0, 0, 0);
            getImageRect(key, false, &rect);
            return rect;
        }

        math::Rectangle<float> GLTextureAtlas::getImageRect(int64_t key, bool normalized)
        {
            math::Rectangle<float> rect(0, 0, 0, 0);
            getImageRect(key, normalized, &rect);
            return rect;
        }

        math::Rectangle<float> *GLTextureAtlas::getImageRect(int64_t key, bool normalized, math::Rectangle<float> *rect)
        {
            if (rect == nullptr)
                rect = new math::Rectangle<float>(0, 0, 0, 0);

            if (fixedIconSize) {
                int index = getIndex(key);
                int numIconCols = (texSize / iconSize);
                rect->y = (float)((index / numIconCols) * iconSize);
                rect->x = (float)((index % numIconCols) * iconSize);
                rect->height = (float)iconSize;
                rect->width = (float)iconSize;
            } else {
                try {
                    Rect r = keyToIconRect.at(key);
                    rect->x = (float)r.x;
                    rect->y = (float)r.y;
                    rect->width = (float)r.width;
                    rect->height = (float)r.height;
                } catch (std::out_of_range) {
                    delete rect;
                    return nullptr;
                }
            }

            if (normalized) {
                rect->x /= (float) texSize;
                rect->y /= (float) texSize;
                rect->width /= (float) texSize;
                rect->height /= (float) texSize;
            }

            return rect;
        }


        int GLTextureAtlas::getImageWidth(int64_t key)
        {
            if (fixedIconSize) {
                return iconSize;
            } else {
                try {
                    Rect r = keyToIconRect.at(key);
                    return r.width;
                } catch (std::out_of_range) {
                    return 0;
                }
            }
        }

        int GLTextureAtlas::getImageHeight(int64_t key)
        {
            if (fixedIconSize) {
                return iconSize;
            } else {
                try {
                    Rect r = keyToIconRect.at(key);
                    return r.height;
                } catch (std::out_of_range) {
                    return 0;
                }
            }
        }

        int GLTextureAtlas::getTextureSize()
        {
            return texSize;
        }

        int GLTextureAtlas::getTexId(int64_t key)
        {
            return (int)((key >> 32L) & 0xFFFFFFFFL);
        }

        int GLTextureAtlas::getIndex(int64_t key)
        {
            return (int)(key & 0xFFFFFFFFL);
        }

        int GLTextureAtlas::getImageTextureOffsetX(int64_t key)
        {
            if (fixedIconSize) {
                int index = getIndex(key);
                int numIconCols = (texSize / iconSize);
                return (index % numIconCols) * iconSize;
            } else {
                try {
                    Rect r = keyToIconRect.at(key);
                    return r.x;
                } catch (std::out_of_range) {
                    return 0;
                }
            }
        }

        int GLTextureAtlas::getImageTextureOffsetY(int64_t key)
        {
            if (fixedIconSize) {
                int index = getIndex(key);
                int numIconCols = (texSize / iconSize);
                return (index / numIconCols) * iconSize;
            } else {
                try {
                    Rect r = keyToIconRect.at(key);
                    return r.y;
                } catch (std::out_of_range) {
                    return 0;
                }
            }
        }

        int64_t GLTextureAtlas::addImage(std::string uri, Bitmap bitmap) throw (std::invalid_argument)
        {
            ScopeLocalBitmap iconDestructor;
            Bitmap icon = bitmap;            

            if (fixedIconSize
                && (bitmap.width != iconSize || bitmap.height != iconSize)) {
                icon = bitmap.getScaledInstance(bitmap, iconSize, iconSize);

                iconDestructor.bitmap = icon;        
                iconDestructor.release = true;
            } else if (bitmap.width > texSize || bitmap.height > texSize) {
                throw std::invalid_argument("image too large compared to texture atlas");
            }

            // allocate a new texture if the current is filled
            if (fixedIconSize) {
                int numIcons = (texSize / iconSize);
                if (freeIndex == (numIcons * numIcons))
                    currentTexId = 0;
            } else {
                Rect bound(0, 0, icon.width, icon.height, splitFreeHorizontal);
                auto iter = freeList.lower_bound(bound);
                bool haveFree = false;
                while (iter != freeList.end()) {
                    Rect r = *iter;
                    if (r.width >= icon.width && r.height >= icon.height) {
                        haveFree = true;
                        break;
                    }
                    iter++;
                }
                if (!haveFree)
                    currentTexId = 0;
            }

            if (currentTexId == 0) {
                GLuint id;

                glGenTextures(1, &id);
                if (id == 0)
                    throw std::invalid_argument("Failed to generate new texture id");
                currentTexId = id;

                glBindTexture(GL_TEXTURE_2D, currentTexId);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
                                             texSize, texSize, 0,
                                             GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

                freeIndex = 0;
                if (!fixedIconSize)
                    freeList.insert(Rect(0, 0, texSize, texSize, splitFreeHorizontal));
            } else {
                glBindTexture(GL_TEXTURE_2D, currentTexId);
            }

            int64_t retval = ((int64_t) currentTexId << 32L)
                | ((int64_t) freeIndex & 0xFFFFFFFFL);
            if (fixedIconSize) {
                int numIconCols = (texSize / iconSize);
                int x = (freeIndex % numIconCols) * iconSize;
                int y = (freeIndex / numIconCols) * iconSize;

                glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, icon.width, icon.height,
                                icon.format, icon.dataType, icon.data);
            } else {
                Rect iconR(0, 0, icon.width, icon.height, splitFreeHorizontal);
                auto iter = freeList.lower_bound(iconR);
                Rect free(0, 0, 0, 0, false);
                bool gotit = false;
                while (iter != freeList.end()) {
                    free = *iter;
                    auto freeIter = iter;
                    iter++;
                    if (free.width >= icon.width && free.height >= icon.height) {
                        freeList.erase(freeIter);
                        gotit = true;
                        break;
                    }
                }
                // the icon is smaller than the texture and we have already made
                // sure that there is enough room to accommodate it
                if (!gotit)
                    throw std::invalid_argument("Unexpected error in texture subdivision");
                // update to reflect the position of the free region
                iconR.x = free.x;
                iconR.y = free.y;
                // subdivide the free region, favoring a vertical or horizontal
                // split
                if (splitFreeHorizontal) {
                    if (free.width > icon.width)
                        freeList.insert(Rect(free.x + icon.width, free.y, 
                                              free.width - icon.width,
                                              free.height,
                                              splitFreeHorizontal));
                    if (free.height > icon.height)
                        freeList.insert(Rect(free.x, free.y + icon.height, 
                                               icon.width,
                                               free.height - icon.height,
                                               splitFreeHorizontal));
                } else {
                    if (free.height > icon.height)
                        freeList.insert(Rect(free.x, free.y + icon.height,
                                              free.width, 
                                              free.height - icon.height, 
                                              splitFreeHorizontal));
                    if (free.width > icon.width)
                        freeList.insert(Rect(free.x + icon.width, free.y,
                                         free.width - icon.width,
                                         icon.height, splitFreeHorizontal));
                }

                glTexSubImage2D(GL_TEXTURE_2D, 0, iconR.x, iconR.y, icon.width,
                                icon.height, icon.format, icon.dataType, icon.data);
                keyToIconRect.insert(std::pair<int64_t, Rect>(retval, iconR));
            }

            uriToKey[uri] = retval;

            freeIndex++;

            glBindTexture(GL_TEXTURE_2D, 0);

            return retval;
        }


        bool GLTextureAtlasRectComp::operator() (const Rect &x, const Rect &y) const
        {
            int retval;
            if (x.splitFreeHorizontal) {
                retval = x.height - y.height;
            } else {
                retval = x.width - y.width;
            }
            if (retval == 0)
                retval = (x.instance - y.instance);
            return retval < 0;
        }

    }
}



