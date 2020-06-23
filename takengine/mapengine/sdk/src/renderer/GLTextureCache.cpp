#include "GLTextureCache.h"

#include <cmath>

namespace atakmap
{
    namespace renderer
    {

        GLTextureCache::Entry::Entry()
        {
            commonInit(NULL, NULL, NULL, 0, NULL, 0, NULL, 0, NULL);
        }

       GLTextureCache::Entry::Entry(disposeEntryFunc disposeFunc, GLTexture *texture,
                     float *vertexCoordinates, int numVertices,
                     float *textureCoordinates,
                     int hints, void *opaque)
       {
           commonInit(disposeFunc, texture, vertexCoordinates, numVertices, NULL, 0, textureCoordinates, hints, opaque);
       }

       GLTextureCache::Entry::Entry(disposeEntryFunc disposeFunc, GLTexture *texture,
                     float *vertexCoordinates, int numVertices,
                     short *indices, int numIndices,
                     float *textureCoordinates,
                     int hints, void *opaque)
       {
           commonInit(disposeFunc, texture, vertexCoordinates, numVertices, indices, numIndices, textureCoordinates, hints, opaque);
       }

       void GLTextureCache::Entry::commonInit(disposeEntryFunc disposeFunc, GLTexture *texture,
                                              float *vertexCoordinates, int numVertices,
                                              short *indices, int numIndices,
                                              float *textureCoordinates,
                                              int hints, void *opaque)
       {
           this->texture = texture;
           this->numVertices = numVertices;
           this->textureCoordinates = textureCoordinates;
           this->vertexCoordinates = vertexCoordinates;
           this->numIndices = numIndices;
           this->indices = indices;
           this->hints = hints;
           this->opaque = opaque;
           disposer = disposeFunc;
       }

       void GLTextureCache::Entry::invokeDisposer()
       {
           if (disposer)
            disposer(texture, vertexCoordinates, indices, textureCoordinates, opaque);
       }

       bool GLTextureCache::Entry::hasHint(int flags)
       {
           return ((hints & flags) == flags);
       }



       GLTextureCache::BidirectionalNode::BidirectionalNode(BidirectionalNode *prev, std::string key, Entry value) :
           prev(prev), next(NULL), key(key), value(value)
       {
           if (prev != NULL)
               prev->next = this;
       }




       GLTextureCache::GLTextureCache(int maxSize) : nodeMap(), head(NULL), 
           tail(NULL), maxSize(maxSize), size(0), count(0)
       {
       }

       GLTextureCache::~GLTextureCache()
       {
           clear();
       }

       bool GLTextureCache::get(std::string key, GLTextureCache::Entry *val) throw (std::out_of_range)
       {
           std::map<std::string, BidirectionalNode *>::iterator entry = nodeMap.find(key);
           if (entry == nodeMap.end())
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
           std::map<std::string, BidirectionalNode *>::iterator entry = nodeMap.find(key);
           if (entry == nodeMap.end())
               return false;

           BidirectionalNode *node = entry->second;
           nodeMap.erase(entry);
           Entry retval = node->value;

           if (node->prev != NULL) {
               node->prev->next = node->next;
           } else if (node == head) {
               head = node->next;
               if (head != NULL)
                   head->prev = NULL;
           }

           if (node->next != NULL) {
               node->next->prev = node->prev;
           } else if (node == tail) {
               tail = node->prev;
               if (tail != NULL)
                   tail->next = NULL;
           }

           delete node;

           count--;

           size -= sizeOf(retval.texture);
           *val = retval;
           return true;
       }

       void GLTextureCache::put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture)
       {
           put(key, disposeFunc, texture, NULL, 0, NULL, 0, NULL);
       }

       void GLTextureCache::put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, int hints, void *opaque)
       {
           put(key, disposeFunc, texture, NULL, 0, NULL, hints, opaque);
       }

       void GLTextureCache::put(std::string key, disposeEntryFunc disposeFunc, GLTexture *texture, float *vertexCoords,
                                int numVertices, float *texCoords)
       {
           put(key, disposeFunc, texture, vertexCoords, numVertices, texCoords, 0, NULL);
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
           nodeMap.clear();
           while (head != NULL) {
               BidirectionalNode *n = head;

               Entry e = head->value;
               e.texture->release();
               e.invokeDisposer();

               head = head->next;
               delete n;
               count--;
           }
           tail = NULL;
           size = 0;
       }
       
       void GLTextureCache::putImpl(std::string key, Entry entry)
       {
           try {
               deleteEntry(key);
           } catch (std::out_of_range) {
               // no existing entry, just move along
           }
           BidirectionalNode *node = new BidirectionalNode(tail, key, entry);
           if (head == NULL)
               head = node;
           tail = node;
           nodeMap.insert(std::pair<std::string, BidirectionalNode *>(key, node));
           count++;
           size += sizeOf(entry.texture);

           trimToSize();
       }

       void GLTextureCache::trimToSize()
       {
           int releasedSize;
           while (size > maxSize && nodeMap.size() > 1) {
               BidirectionalNode *n = head;
               Entry e = head->value;
               releasedSize = sizeOf(e.texture);
               nodeMap.erase(head->key);
               head = head->next;
               head->prev = NULL;
               count--;
               e.texture->release();
               e.invokeDisposer();
               delete n;
               size -= releasedSize;
           }
       }

       int GLTextureCache::sizeOf(GLTexture *texture)
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
