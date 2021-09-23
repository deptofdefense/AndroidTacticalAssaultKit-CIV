#include "renderer/GLTextureCache2.h"

#include <cmath>

using namespace TAK::Engine::Renderer;

using namespace TAK::Engine::Util;

GLTextureCache2::GLTextureCache2(const std::size_t maxSize_) NOTHROWS :
    head(nullptr),
    tail(nullptr),
    maxSize(maxSize_),
    size(0u),
    count(0u)
{}

GLTextureCache2::~GLTextureCache2()
{
    // clear the map and clean up allocations, but do NOT release GL resources
    nodeMap.clear();
    while (head != nullptr) {
        BidirectionalNode *n = head;

        head = head->next;
        delete n;
        count--;
    }
    tail = nullptr;
    size = 0u;
}

TAKErr GLTextureCache2::get(const GLTextureCache2::Entry **value, const char *key) const NOTHROWS
{
    auto entry = nodeMap.find(key);
    if (entry == nodeMap.end())
        return TE_InvalidArg;

    BidirectionalNode *node = entry->second;
    *value = node->value.get();
    return TE_Ok;
}

TAKErr GLTextureCache2::deleteEntry(const char *key) NOTHROWS
{
    TAKErr code;
    GLTextureCache2::EntryPtr entry(nullptr, nullptr);
    code = this->remove(entry, key);
    TE_CHECKRETURN_CODE(code);
    if (entry->texture.get())
        entry->texture->release();
    return code;
}

TAKErr GLTextureCache2::remove(GLTextureCache2::EntryPtr &val, const char *key) NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    auto entry = nodeMap.find(key);
    if (entry == nodeMap.end())
        return TE_InvalidArg;

    BidirectionalNode *node = entry->second;
    nodeMap.erase(entry);
    val = std::move(node->value);

    if (node->prev != nullptr) {
        node->prev->next = node->next;
    } else if (node == head) {
        head = node->next;
        if (head != nullptr)
            head->prev = nullptr;
    }

    if (node->next != nullptr) {
        node->next->prev = node->prev;
    } else if (node == tail) {
        tail = node->prev;
        if (tail != nullptr)
            tail->next = nullptr;
    }

    delete node;

    count--;
    
    if (val->texture.get()) {
        std::size_t texSize;
        code = sizeOf(&texSize, *val->texture);
        TE_CHECKRETURN_CODE(code);
        size -= texSize + val->opaqueSize;
    }

    return code;
}

TAKErr GLTextureCache2::put(const char *key, EntryPtr &&value) NOTHROWS
{
    TAKErr code;

    // if there is already an entry we will replace it
    if (this->nodeMap.find(key) != this->nodeMap.end()) {
        code = deleteEntry(key);
        if (code == TE_InvalidArg)
            code = TE_Ok;
        TE_CHECKRETURN_CODE(code);
    }

    std::size_t texSize = 0u;
    if (value->texture.get()) {
        code = sizeOf(&texSize, *value->texture);
        TE_CHECKRETURN_CODE(code);
    }

    std::unique_ptr<BidirectionalNode> nodePtr(new BidirectionalNode(tail, key, std::move(value)));
    BidirectionalNode *node = nodePtr.get();
    nodeMap.insert(std::pair<std::string, BidirectionalNode *>(key, nodePtr.release()));
    if (head == nullptr)
        head = node;
    tail = node;

    count++;
    size += texSize + tail->value->opaqueSize;

    code = trimToSize();
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLTextureCache2::clear() NOTHROWS
{
    nodeMap.clear();
    while (head != nullptr) {
        BidirectionalNode *n = head;

        head = head->next;
        if (n->value->texture.get())
            n->value->texture->release();
        delete n;
        count--;
    }
    tail = nullptr;
    size = 0u;

    return TE_Ok;
}

TAKErr GLTextureCache2::trimToSize() NOTHROWS
{
    TAKErr code;

    code = TE_Ok;

    std::size_t releasedSize;
    while (size > maxSize && nodeMap.size() > 1) {
        BidirectionalNode *n = head;
        if (n->value->texture.get()) {
            code = sizeOf(&releasedSize, *n->value->texture);
            TE_CHECKBREAK_CODE(code);
            releasedSize += n->value->opaqueSize;
        } else {
            releasedSize = n->value->opaqueSize;
        }
        nodeMap.erase(head->key);
        head = head->next;
        head->prev = nullptr;
        count--;
        if (n->value->texture.get())
            n->value->texture->release();
        delete n;
        size -= releasedSize;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

TAKErr GLTextureCache2::sizeOf(std::size_t *value, const GLTexture2 &texture) NOTHROWS
{
    int bytesPerPixel;
    switch (texture.getType()) {
    case GL_UNSIGNED_BYTE:
        switch (texture.getFormat()) {
        case GL_LUMINANCE:
            bytesPerPixel = 1;
            break;
        case GL_LUMINANCE_ALPHA:
            bytesPerPixel = 2;
            break;
        case GL_RGB:
            bytesPerPixel = 3;
            break;
        case GL_RGBA:
            bytesPerPixel = 4;
            break;
        default:
            return TE_InvalidArg;
        }
        break;
    case GL_UNSIGNED_SHORT_5_5_5_1:
    case GL_UNSIGNED_SHORT_5_6_5:
        bytesPerPixel = 2;
        break;
    default:
        return TE_InvalidArg;
    }
    *value = bytesPerPixel * texture.getTexWidth() * texture.getTexHeight();
    return TE_Ok;
}


GLTextureCache2::BidirectionalNode::BidirectionalNode(BidirectionalNode *prev_, std::string key_, EntryPtr &&value_) NOTHROWS :
    prev(prev_),
    next(nullptr),
    key(key_),
    value(std::move(value_))
{
    if (prev != nullptr)
        prev->next = this;
}

GLTextureCache2::Entry::Entry() NOTHROWS :
    texture(nullptr, nullptr),
    textureCoordinates(nullptr, nullptr),
    vertexCoordinates(nullptr, nullptr),
    indices(nullptr, nullptr),
    numVertices(0u),
    numIndices(0u),
    hints(0u),
    opaque(nullptr, nullptr),
    opaqueSize(0u)
{}

GLTextureCache2::Entry::Entry(GLTexture2Ptr &&texture_) NOTHROWS :
    texture(std::move(texture_)),
    textureCoordinates(nullptr, nullptr),
    vertexCoordinates(nullptr, nullptr),
    indices(nullptr, nullptr),
    numVertices(0u),
    numIndices(0u),
    hints(0u),
    opaque(nullptr, nullptr),
    opaqueSize(0u)
{}

GLTextureCache2::Entry::Entry(GLTexture2Ptr &&texture_,
                              std::unique_ptr<float, void(*)(const float *)> &&textureCoordinates_,
                              std::unique_ptr<float, void(*)(const float *)> &&vertexCoordinates_,
                              const std::size_t numVertices_) NOTHROWS :
    texture(std::move(texture_)),
    textureCoordinates(std::move(textureCoordinates_)),
    vertexCoordinates(std::move(vertexCoordinates_)),
    indices(nullptr, nullptr),
    numVertices(numVertices_),
    numIndices(0u),
    hints(0u),
    opaque(nullptr, nullptr),
    opaqueSize(0u)
{}

GLTextureCache2::Entry::Entry(GLTexture2Ptr &&texture_,
                std::unique_ptr<float, void(*)(const float *)> &&textureCoordinates_,
                std::unique_ptr<float, void(*)(const float *)> &&vertexCoordinates_,
                const std::size_t numVertices_,
                const std::size_t hints_,
                OpaquePtr opaque_) NOTHROWS:
    texture(std::move(texture_)),
    textureCoordinates(std::move(textureCoordinates_)),
    vertexCoordinates(std::move(vertexCoordinates_)),
    indices(nullptr, nullptr),
    numVertices(numVertices_),
    numIndices(0u),
    hints(hints_),
    opaque(std::move(opaque_)),
    opaqueSize(0u)
{}

GLTextureCache2::Entry::Entry(GLTexture2Ptr &&texture_,
                              std::unique_ptr<float, void(*)(const float *)> &&textureCoordinates_,
                              std::unique_ptr<float, void(*)(const float *)> &&vertexCoordinates_,
                              std::unique_ptr<uint16_t, void(*)(const uint16_t *)> &&indices_,
                              const std::size_t numVertices_,
                              const std::size_t numIndices_,
                              const std::size_t hints_,
                              OpaquePtr opaque_) NOTHROWS :
    texture(std::move(texture_)),
    textureCoordinates(std::move(textureCoordinates_)),
    vertexCoordinates(std::move(vertexCoordinates_)),
    indices(std::move(indices_)),
    numVertices(numVertices_),
    numIndices(numIndices_),
    hints(hints_),
    opaque(std::move(opaque_)),
    opaqueSize(0u)
{}

bool GLTextureCache2::Entry::hasHint(int flags) const
{
    return ((this->hints & flags) == flags);
}