#include "GLTextureCache.h"

#include <cmath>

namespace atakmap
{
    namespace renderer
    {

        GLTextureCache::Entry::Entry()
        {
            commonInit(nullptr, nullptr, nullptr, 0, nullptr, 0, nullptr, 0, nullptr);
        }

       GLTextureCache::Entry::Entry(disposeEntryFunc disposeFunc, GLTexture *texture,
                     float *vertexCoordinates, int numVertices,
                     float *textureCoordinates,
                     int hints, void *opaque)
       {
           commonInit(disposeFunc, texture, vertexCoordinates, numVertices, nullptr, 0, textureCoordinates, hints, opaque);
       }

       GLTextureCache::Entry::Entry(disposeEntryFunc disposeFunc, GLTexture *texture,
                     float *vertexCoordinates, int numVertices,
                     short *indices, int numIndices,
                     float *textureCoordinates,
                     int hints, void *opaque)
       {
           commonInit(disposeFunc, texture, vertexCoordinates, numVertices, indices, numIndices, textureCoordinates, hints, opaque);
       }

       void GLTextureCache::Entry::commonInit(disposeEntryFunc dispose_func, GLTexture *gl_texture,
                                              float *vertex_coordinates, int num_vertices,
                                              short *indices_array, int num_indices,
                                              float *texture_coordinates,
                                              int hint_flags, void *opaque_ptr)
       {
           this->texture = gl_texture;
           this->numVertices = num_vertices;
           this->textureCoordinates = texture_coordinates;
           this->vertexCoordinates = vertex_coordinates;
           this->numIndices = num_indices;
           this->indices = indices_array;
           this->hints = hint_flags;
           this->opaque = opaque_ptr;
           disposer_ = dispose_func;
       }

       void GLTextureCache::Entry::invokeDisposer()
       {
           if (disposer_)
            disposer_(texture, vertexCoordinates, indices, textureCoordinates, opaque);
       }

       bool GLTextureCache::Entry::hasHint(int flags)
       {
           return ((hints & flags) == flags);
       }



       GLTextureCache::BidirectionalNode::BidirectionalNode(BidirectionalNode *prev, std::string key, Entry value) :
           prev(prev), next(nullptr), key(key), value(value)
       {
           if (prev != nullptr)
               prev->next = this;
       }




       GLTextureCache::GLTextureCache(std::size_t maxSize) : node_map_(), head_(nullptr), 
           tail_(nullptr), max_size_(maxSize), size_(0), count_(0)
       {
       }

       GLTextureCache::~GLTextureCache()
       {
           clear();
       }

       bool GLTextureCache::get(std::string key, GLTextureCache::Entry *val) throw (std::out_of_range)
       {
           auto entry = node_map_.find(key);
           if (entry == node_map_.end())
               return false;

           BidirectionalNode *node = entry->second;
           *val = node->value;
           return true;
       }

       void GLTextureCache::deleteEntry(std::string key) throw (std::out_of_range)
       {
           Entry e;
           if (remove(key, &e))
               e.texture->release();
           e.invokeDisposer();
       }

       bool GLTextureCache::remove(std::string key, GLTextureCache::Entry *val) throw (std::out_of_range)
       {
           auto entry = node_map_.find(key);
           if (entry == node_map_.end())
               return false;

           BidirectionalNode *node = entry->second;
           node_map_.erase(entry);
           Entry retval = node->value;

           if (node->prev != nullptr) {
               node->prev->next = node->next;
           } else if (node == head_) {
               head_ = node->next;
               if (head_ != nullptr)
                   head_->prev = nullptr;
           }

           if (node->next != nullptr) {
               node->next->prev = node->prev;
           } else if (node == tail_) {
               tail_ = node->prev;
               if (tail_ != nullptr)
                   tail_->next = nullptr;
           }

           delete node;

           count_--;

           size_ -= sizeOf(retval.texture);
           *val = retval;
           return true;
       }

       void GLTextureCache::put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture)
       {
           put(key, disposeFunc, texture, nullptr, 0, nullptr, 0, nullptr);
       }

       void GLTextureCache::put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, int hints, void *opaque)
       {
           put(key, disposeFunc, texture, nullptr, 0, nullptr, hints, opaque);
       }

       void GLTextureCache::put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, float *vertexCoords,
                                int numVertices, float *texCoords)
       {
           put(key, disposeFunc, texture, vertexCoords, numVertices, texCoords, 0, nullptr);
       }

       void GLTextureCache::put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, float *vertexCoords,
                                int numVertices, float *texCoords, int hints, void *opaque)
       {
           putImpl(key, Entry(disposeFunc, texture, vertexCoords, numVertices, texCoords, hints, opaque));
       }

       void GLTextureCache::put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, float *vertexCoords,
                                int numVertices, short *indices, int numIndices,
                                float *textureCoordinates,
                                int hints, void *opaque)
       {
           putImpl(key, Entry(disposeFunc, texture, vertexCoords, numVertices, indices, numIndices, textureCoordinates, hints, opaque));
       }

       void GLTextureCache::clear()
       {
           node_map_.clear();
           while (head_ != nullptr) {
               BidirectionalNode *n = head_;

               Entry e = head_->value;
               e.texture->release();
               e.invokeDisposer();

               head_ = head_->next;
               delete n;
               count_--;
           }
           tail_ = nullptr;
           size_ = 0;
       }
       
       void GLTextureCache::putImpl(std::string key, Entry entry)
       {
           try {
               deleteEntry(key);
           } catch (std::out_of_range) {
               // no existing entry, just move along
           }
           BidirectionalNode *node = new BidirectionalNode(tail_, key, entry);
           if (head_ == nullptr)
               head_ = node;
           tail_ = node;
           node_map_.insert(std::pair<std::string, BidirectionalNode *>(key, node));
           count_++;
           size_ += sizeOf(entry.texture);

           trimToSize();
       }

       void GLTextureCache::trimToSize()
       {
           size_t releasedSize;
           while (size_ > max_size_ && node_map_.size() > 1) {
               BidirectionalNode *n = head_;
               Entry e = head_->value;
               releasedSize = sizeOf(e.texture);
               node_map_.erase(head_->key);
               head_ = head_->next;
               head_->prev = nullptr;
               count_--;
               e.texture->release();
               e.invokeDisposer();
               delete n;
               size_ -= releasedSize;
           }
       }

       std::size_t GLTextureCache::sizeOf(GLTexture *texture)
       {
           int bytesPerPixel;
           switch (texture->getType()) {
           case GL_UNSIGNED_BYTE:
               switch (texture->getFormat()) {
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
                   throw std::invalid_argument("Invalid texture format");
               }
               break;
           case GL_UNSIGNED_SHORT_5_5_5_1:
           case GL_UNSIGNED_SHORT_5_6_5:
               bytesPerPixel = 2;
               break;
           default:
               throw std::invalid_argument("Invalid texture type");
           }
           return bytesPerPixel * texture->getTexWidth() * texture->getTexHeight();
       }
    }
}
