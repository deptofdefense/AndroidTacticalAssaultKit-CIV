#include "renderer/GLTextureAtlas2.h"

#include <cmath>

#include "renderer/GLTexture2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Util;

#define RECT_SHEET_SIZE 256

GLTextureAtlas2::Rect::Rect() :
    x(0),
    y(0),
    width(0),
    height(0),
    splitFreeHorizontal(false)
{}

GLTextureAtlas2::Rect::Rect(const int x, const int y, const size_t width, const size_t height, const bool splitFreeHorizontal) :
    x(x),
    y(y),
    width(width),
    height(height),
    splitFreeHorizontal(splitFreeHorizontal)
{
    static int nextInstance = 0;
    instance = nextInstance++;
}

GLTextureAtlas2::SheetRects::SheetRects(const int tex_id) NOTHROWS :
    texid(tex_id),
    limit(RECT_SHEET_SIZE),
    rectPtr(new GLTextureAtlas2::Rect[limit]),
    rect(rectPtr.get()),
    numRects(0)
{}

const GLTextureAtlas2::SheetRects *GLTextureAtlas2::SheetRects::find(const int tex_id) const NOTHROWS
{
    const SheetRects *iter = this;
    while (iter)
    {
        if (iter->texid == tex_id)
        {
            return iter;
        }

        iter = iter->next.get();
    }
    return nullptr;
}

GLTextureAtlas2::GLTextureAtlas2(const std::size_t texSize_) NOTHROWS :
    iconSize(0),
    texSize(((std::size_t)1) << (int)ceil(log(texSize_) / log(2))),
    fixedIconSize(false),
    freeIndex(0),
    currentTexId(0),
    splitFreeHorizontal(false),
    currentSheetRects(nullptr)
{}

GLTextureAtlas2::GLTextureAtlas2(const std::size_t texSize_, const bool splitHorizontal_) NOTHROWS :
    iconSize(0),
    texSize(((std::size_t)1) << (int)ceil(log(texSize_) / log(2))),
    fixedIconSize(false),
    freeIndex(0),
    currentTexId(0),
    splitFreeHorizontal(splitHorizontal_),
    currentSheetRects(nullptr)
{}

GLTextureAtlas2::GLTextureAtlas2(const std::size_t texSize_, const std::size_t iconSize_) NOTHROWS :
    iconSize(iconSize_),
    texSize(((std::size_t)1) << (int)ceil(log(texSize_) / log(2))),
    fixedIconSize(iconSize_ > 0),
    freeIndex(0),
    currentTexId(0),
    splitFreeHorizontal(false),
    currentSheetRects(nullptr)
{}

GLTextureAtlas2::~GLTextureAtlas2() NOTHROWS
{}

TAKErr GLTextureAtlas2::release() NOTHROWS
{
    std::set<int> texIds;
    std::map<std::string, int64_t>::iterator iter;
    for (iter = uriToKey.begin(); iter != uriToKey.end(); ++iter) {
        int texId;
        getTexId(&texId, iter->second);
        texIds.insert(texId);
    }

    uriToKey.clear();
    keyToIconRectOverflow.clear();
    sheetRects.reset();
    currentSheetRects = nullptr;

    std::set<int>::iterator siter;
    for (siter = texIds.begin(); siter != texIds.end(); ++siter)
        releaseTexture(*siter);

    return TE_Ok;
}

TAKErr GLTextureAtlas2::releaseTexture(const int textureId) NOTHROWS
{
    int64_t key;
    std::map<std::string, int64_t>::iterator iter;
    for (iter = uriToKey.begin(); iter != uriToKey.end();) {
        key = iter->second;
        int keyTexId;
        getTexId(&keyTexId, key);
        if (keyTexId == textureId) {
            iter = uriToKey.erase(iter);
            keyToIconRectOverflow.erase(key);
        } else {
            iter++;
        }
    }

    std::unique_ptr<SheetRects> *sheetRectIter = &this->sheetRects;
    while (sheetRectIter->get())
    {
        if ((*sheetRectIter)->texid == textureId)
        {
            // XXX - 
            sheetRectIter->reset((*sheetRectIter)->next.release());
            break;
        }

        sheetRectIter = &(*sheetRectIter)->next;
    }

    if (currentTexId == textureId) {
        currentTexId = 0;
        freeList.clear();
    }

    GLuint tid = textureId;
    glDeleteTextures(1, &tid);

    return TE_Ok;
}

TAKErr GLTextureAtlas2::getTextureKey(int64_t *value, const char *uri) const NOTHROWS
{
    std::map<std::string, int64_t>::const_iterator entry;
    entry = uriToKey.find(uri);
    if (entry == uriToKey.end())
        return TE_InvalidArg;
    *value = entry->second;
    return TE_Ok;
}

bool GLTextureAtlas2::isFixedImageSize() const NOTHROWS
{
    return fixedIconSize;
}

TAKErr GLTextureAtlas2::getImageRect(atakmap::math::Rectangle<float> *value, const int64_t key) const NOTHROWS
{
    return getImageRect(value, key, false);
}

TAKErr GLTextureAtlas2::getImageRect(atakmap::math::Rectangle<float> *value, const int64_t key, const bool normalized) const NOTHROWS
{
    TAKErr code;

    code = TE_Ok;
    if (fixedIconSize) {
        int index;
        code = getIndex(&index, key);
        TE_CHECKRETURN_CODE(code);

        size_t numIconCols = (texSize / iconSize);
        value->y = (float)((index / numIconCols) * iconSize);
        value->x = (float)((index % numIconCols) * iconSize);
        value->height = (float)iconSize;
        value->width = (float)iconSize;
    } else {
        int index;
        code = getIndex(&index, key);
        TE_CHECKRETURN_CODE(code);
        int texid;
        code = getTexId(&texid, key);
        TE_CHECKRETURN_CODE(code);

        // find the sheet
        const SheetRects *rects = nullptr;
        if (currentSheetRects && currentSheetRects->texid == texid) {
            // check if it's the current sheet
            rects = currentSheetRects;
        } else {
            // do the sheet lookup
            rects = this->sheetRects->find(texid);
        }

        if (rects && index < rects->numRects) {
            // if the rect is on a sheet, use it
            const Rect &r = rects->rect[index];
            value->x = (float)r.x;
            value->y = (float)r.y;
            value->width = (float)r.width;
            value->height = (float)r.height;
        } else {
            // check overflow
            std::map<int64_t, Rect, GLTextureAtlasRectComp>::const_iterator entry;
            entry = keyToIconRectOverflow.find(key);
            if (entry == keyToIconRectOverflow.end())
                return TE_InvalidArg;

            const Rect &r = entry->second;
            value->x = (float)r.x;
            value->y = (float)r.y;
            value->width = (float)r.width;
            value->height = (float)r.height;
        }
    }

    if (normalized) {
        value->x /= (float)texSize;
        value->y /= (float)texSize;
        value->width /= (float)texSize;
        value->height /= (float)texSize;
    }

    return code;
}


TAKErr GLTextureAtlas2::getImageWidth(std::size_t *value, const int64_t key) const NOTHROWS
{
    if (fixedIconSize) {
        *value = iconSize;
        return TE_Ok;
    } else {
        TAKErr code(TE_Ok);
        atakmap::math::Rectangle<float> r;
        code = getImageRect(&r, key, false);
        TE_CHECKRETURN_CODE(code);
        *value = (std::size_t)r.width;
        return code;
    }
}

TAKErr GLTextureAtlas2::getImageHeight(std::size_t *value, const int64_t key) const NOTHROWS
{
    if (fixedIconSize) {
        *value = iconSize;
        return TE_Ok;
    } else {
        TAKErr code(TE_Ok);
        atakmap::math::Rectangle<float> r;
        code = getImageRect(&r, key, false);
        TE_CHECKRETURN_CODE(code);
        *value = (std::size_t)r.height;
        return code;
    }
}

std::size_t GLTextureAtlas2::getTextureSize() const NOTHROWS
{
    return texSize;
}

TAKErr GLTextureAtlas2::getTexId(int *value, const int64_t key) const NOTHROWS
{
    *value = (int)((key >> 32L) & 0xFFFFFFFFL);
    return TE_Ok;
}

TAKErr GLTextureAtlas2::getIndex(int *value, const int64_t key) const NOTHROWS
{
    *value = (int)(key & 0xFFFFFFFFL);
    return TE_Ok;
}

TAKErr GLTextureAtlas2::getImageTextureOffsetX(std::size_t *value, const int64_t key) const NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    if (fixedIconSize) {
        int index;
        code = getIndex(&index, key);
        TE_CHECKRETURN_CODE(code);

        std::size_t numIconCols = (texSize / iconSize);
        *value = (index % numIconCols) * iconSize;
        return code;
    } else {
        atakmap::math::Rectangle<float> r;
        code = getImageRect(&r, key, false);
        TE_CHECKRETURN_CODE(code);
        *value = (std::size_t)r.x;
        return code;
    }
}

TAKErr GLTextureAtlas2::getImageTextureOffsetY(std::size_t *value, const int64_t key) const NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    if (fixedIconSize) {
        int index;
        code = getIndex(&index, key);
        TE_CHECKRETURN_CODE(code);

        std::size_t numIconCols = (texSize / iconSize);
        *value = (index / numIconCols) * iconSize;
        return code;
    } else {
        atakmap::math::Rectangle<float> r;
        code = getImageRect(&r, key, false);
        TE_CHECKRETURN_CODE(code);
        *value = (std::size_t)r.y;
        return code;
    }
}

TAKErr GLTextureAtlas2::addImage(int64_t *value, const char *uri, const Bitmap2 &legacy) NOTHROWS
{
    int bitmapGLFormat = GL_RGBA;
    int bitmapGLType = GL_UNSIGNED_BYTE;

    BitmapPtr_const bitmap(&legacy, Memory_leaker_const<Bitmap2>);

    if (fixedIconSize
        && (bitmap->getWidth() != iconSize || bitmap->getHeight() != iconSize)) {
        
        bitmap = BitmapPtr_const(new Bitmap2(*bitmap, iconSize, iconSize), Memory_deleter_const<Bitmap2>);
    } else if (bitmap->getWidth() > texSize || bitmap->getHeight() > texSize) {
        atakmap::util::Logger::log(atakmap::util::Logger::Error, "GLTextureAtlas2: image too large compared to texture atlas: %s", uri);
        return TE_InvalidArg;
    }

    if (bitmap->getFormat() != Bitmap2::RGBA32)
        bitmap = BitmapPtr_const(new Bitmap2(*bitmap, Bitmap2::RGBA32), Memory_deleter_const<Bitmap2>);

    // allocate a new texture if the current is filled
    if (fixedIconSize) {
        size_t numIcons = (texSize / iconSize);
        if (freeIndex == (numIcons * numIcons))
            currentTexId = 0;
    } else {
        Rect bound(0, 0, bitmap->getWidth(), bitmap->getHeight(), splitFreeHorizontal);
        auto iter = freeList.lower_bound(bound);
        bool haveFree = false;
        while (iter != freeList.end()) {
            Rect r = *iter;
            if (r.width >= bitmap->getWidth() && r.height >= bitmap->getHeight()) {
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
        if (id == 0) {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "GLTextureAtlas2: failed to generate new texture id: %s", uri);
            return TE_Err;
        }
        currentTexId = id;

        glBindTexture(GL_TEXTURE_2D, currentTexId);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA,
            static_cast<GLsizei>(texSize), static_cast<GLsizei>(texSize), 0,
            GL_RGBA, GL_UNSIGNED_BYTE, nullptr);

        freeIndex = 0;
        if (!fixedIconSize) {
            freeList.clear();
            freeList.insert(Rect(0, 0, texSize, texSize, splitFreeHorizontal));
        }

        if (!currentSheetRects) {
            sheetRects.reset(new SheetRects(currentTexId));
            currentSheetRects = sheetRects.get();
        } else {
            currentSheetRects->next.reset(new SheetRects(currentTexId));
            currentSheetRects = currentSheetRects->next.get();
        }
    } else {
        glBindTexture(GL_TEXTURE_2D, currentTexId);
    }

    int64_t retval = ((int64_t)currentTexId << 32L)
        | ((int64_t)freeIndex & 0xFFFFFFFFL);
    if (fixedIconSize) {
        size_t numIconCols = (texSize / iconSize);
        int x = static_cast<int>((freeIndex % numIconCols) * iconSize);
        int y = static_cast<int>((freeIndex / numIconCols) * iconSize);

        glTexSubImage2D(GL_TEXTURE_2D, 0, x, y, static_cast<GLsizei>(bitmap->getWidth()), static_cast<GLsizei>(bitmap->getHeight()),
            bitmapGLFormat, bitmapGLType, bitmap->getData());
    }
    else {
        Rect iconR(0, 0, bitmap->getWidth(), bitmap->getHeight(), splitFreeHorizontal);
        auto iter = freeList.lower_bound(iconR);
        Rect free(0, 0, 0, 0, false);
        bool gotit = false;
        while (iter != freeList.end()) {
            free = *iter;
            auto freeIter = iter;
            iter++;
            if (free.width >= bitmap->getWidth() && free.height >= bitmap->getHeight()) {
                freeList.erase(freeIter);
                gotit = true;
                break;
            }
        }
        // the icon is smaller than the texture and we have already made
        // sure that there is enough room to accommodate it
        if (!gotit) {
            atakmap::util::Logger::log(atakmap::util::Logger::Error, "GLTextureAtlas2: Unexpected error in texture subdivision: %s", uri);
            return TE_Err;
        }
        // update to reflect the position of the free region
        iconR.x = free.x;
        iconR.y = free.y;
        // subdivide the free region, favoring a vertical or horizontal
        // split
        if (splitFreeHorizontal) {
            if (free.width > bitmap->getWidth())
                freeList.insert(Rect(free.x + static_cast<int>(bitmap->getWidth()), free.y,
                free.width - bitmap->getWidth(),
                free.height,
                splitFreeHorizontal));
            if (free.height > bitmap->getHeight())
                freeList.insert(Rect(free.x, free.y + static_cast<int>(bitmap->getHeight()),
                bitmap->getWidth(),
                free.height - bitmap->getHeight(),
                splitFreeHorizontal));
        }
        else {
            if (free.height > bitmap->getHeight())
                freeList.insert(Rect(free.x, free.y + static_cast<int>(bitmap->getHeight()),
                free.width,
                free.height - bitmap->getHeight(),
                splitFreeHorizontal));
            if (free.width > bitmap->getWidth())
                freeList.insert(Rect(free.x + static_cast<int>(bitmap->getWidth()), free.y,
                free.width - bitmap->getWidth(),
                bitmap->getHeight(), splitFreeHorizontal));
        }

        glTexSubImage2D(GL_TEXTURE_2D, 0, iconR.x, iconR.y, static_cast<GLsizei>(bitmap->getWidth()),
            static_cast<GLsizei>(bitmap->getHeight()), bitmapGLFormat, bitmapGLType, bitmap->getData());

        if (currentSheetRects->numRects < currentSheetRects->limit && freeIndex == currentSheetRects->numRects)
        {
            // numRects and freeIndex must always be equal !!!
            currentSheetRects->rect[currentSheetRects->numRects++] = iconR;
        } else {
            // rect sheet is filled, put in the overflow
            keyToIconRectOverflow.insert(std::pair<int64_t, Rect>(retval, iconR));
        }
    }

    uriToKey[uri] = retval;

    freeIndex++;

    glBindTexture(GL_TEXTURE_2D, 0);

    *value = retval;
    return TE_Ok;
}


bool GLTextureAtlas2::GLTextureAtlasRectComp::operator() (const GLTextureAtlas2::Rect &x, const GLTextureAtlas2::Rect &y) const
{
    const std::size_t xa = (x.width*x.height);
    const std::size_t ya = (y.width*y.height);
    if(xa < ya)
        return true;
    else if(xa > ya)
        return false;
    else if(x.splitFreeHorizontal && x.width < y.width)
        return true;
    else if(x.splitFreeHorizontal && x.width > y.width)
        return false;
    else if(x.height < y.height)
        return true;
    else if(x.height > y.height)
        return false;
    else
        return x.instance > y.instance;
}
