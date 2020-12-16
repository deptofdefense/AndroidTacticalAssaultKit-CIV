
#ifndef ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEREADER_GLQUADTILENODE_H_INCLUDED
#define ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEREADER_GLQUADTILENODE_H_INCLUDED

#include "renderer/map/layer/raster/tilereader/GLTileNode.h"

namespace atakmap {
    namespace renderer {
        namespace map {
            namespace layer {
                namespace raster {
                    namespace tilereader {
                        class GLQuadTileNode : public GLTileNode {
                            
                        private:
                            static const bool TEXTURE_BORROW_ENABLED = true;
                            
                        private:
                            static const bool TEXTURE_COPY_ENABLED = true;
                            static bool offscreenFboFailed;
                            
                            /*************************************************************************/
                            
                            /**
                             * This node's children, in the order: upper-left, upper-right, lower-left, lower-right.
                             */
                        protected:
                            static const size_t childrenLength = 4;
                            GLQuadTileNode *children[childrenLength];
                            
                            int halfTileWidth;
                            int halfTileHeight;
                            
                            GLQuadTileNode *parent;
                            GLQuadTileNode *root;
                            
                            GLQuadTileNode *borrowingFrom;
                            
                            typedef std::vector<GLQuadTileNode *> BorrowingSet;
                            BorrowingSet borrowing;
                            
                            /* OWNED BY ROOT */
                            
                            /** local GSD for dataset */
                            double gsd;
                            
                            /* corner coordinates of dataset NOT tile */
                            
                            /** upper-left corner for dataset */
                            atakmap::core::GeoPoint upperLeft;
                            /** upper-right corner for dataset */
                            atakmap::core::GeoPoint upperRight;
                            /** lower-right corner for dataset */
                            atakmap::core::GeoPoint lowerRight;
                            /** lower-left corner for dataset */
                            atakmap::core::GeoPoint lowerLeft;
                            
                            uint32_t frameBufferHandle;
                            uint32_t depthBufferHandle;
                            
                            bool loadingTextureEnabled;
                            
                            bool verticesInvalid;
                            
                            int loadingTexCoordsVertCount;
                            
                            int minFilter;
                            int magFilter;
                            
                            bool derivedUnresolvableData;
                            
                            /**************************************************************************/
                            
                        public:
                            /**
                             * Creates a new root node.
                             *
                             * @param type The dataset type
                             * @param reader The reader
                             * @param proj The projection for the dataset
                             */
                            GLQuadTileNode(const char *type,
                                           atakmap::raster::tilereader::TileReader *reader,
                                           atakmap::raster::DatasetProjection *proj,
                                           bool ownsResources);
                            
                            GLQuadTileNode(const char *type,
                                           atakmap::raster::tilereader::TileReader *reader,
                                           atakmap::raster::DatasetProjection *proj,
                                           bool ownsResources,
                                           const Options *opts);
                            
                            virtual ~GLQuadTileNode();
                            
                        protected:
                            GLQuadTileNode(const char *type,
                                           atakmap::raster::tilereader::TileReader *reader,
                                           atakmap::raster::DatasetProjection *proj,
                                           bool ownsResources, VertexResolver *vertexResolver, const Options *opts);
                            
                            /**
                             * Creates a new child node.
                             *
                             * @param parent The parent node
                             * @param idx The index; <code>0</code> for upper-left, <code>1</code> for upper-right,
                             *            <code>2</code> for lower-left and <code>3</code> for lower-right.
                             */
                            GLQuadTileNode(GLQuadTileNode *parent, int idx);
                            
                        private:
                            GLQuadTileNode(GLQuadTileNode *parent, int idx,
                                           const char *type,
                                           atakmap::raster::tilereader::TileReader *reader,
                                           atakmap::raster::DatasetProjection *proj,
                                           bool ownsResources, VertexResolver *vertexResolver,
                                           const Options *opts);
                            
                            void quadInitImpl();
                            
                        public:
                            void set(int64_t tileColumn, int64_t tileRow, int level);
                            
                            void setTextureCache(GLTextureCache *cache);
                            
                            void setLoadingTextureEnabled(bool enabled);
                            
                            void release();
                            
                        protected:
                            /**
                             * Allows for borrowing a portion of this node's texture.
                             *
                             * @param ref The node performing the borrowing. A reference to this node is stored to track
                             *            whether or not the borrow is still in effect.
                             * @param srcX The x-coordinate of the source region (unscaled) to be borrowed
                             * @param srcY The y-coordinate of the source region (unscaled) to be borrowed
                             * @param srcW The width of the source region (unscaled) to be borrowed
                             * @param srcH The height of the source region (unscaled) to be borrowed
                             * @param texCoords Returns the texture coordinates for the requested region of the texture.
                             * @return This node's texture
                             */
                            GLTexture *borrowTexture(GLQuadTileNode *ref, int64_t srcX, int64_t srcY, int64_t srcW,
                                                     int64_t srcH, float *texCoords, int texGridWidth, int texGridHeight);
                            
                            /**
                             * Notifies this node that the specified node is no longer borrowing the texture.
                             *
                             * @param ref A node that was previously borrowing this node's texture.
                             */
                            void unborrowTexture(GLQuadTileNode *ref);
                            
                            virtual GLTexture *getLoadingTexture(float *texCoords, int texGridWidth, int texGridHeight);
                            
                        public:
                            virtual void draw(const GLMapView *view);
                            
                        protected:
                            virtual void resolveTexture();
                            
                        private:
                            /**
                             * @param view The view
                             * @param level The resolution level. The scale factor is equivalent to
                             *            <code>1.0d / (double)(1<<level)</code>
                             * @param srcX The x-coordinate of the source region to be rendered (unscaled)
                             * @param srcY The y-coordinate of the source region to be rendered (unscaled)
                             * @param srcW The width of the source region to be rendered (unscaled)
                             * @param srcH The height of the source region to be rendered (unscaled)
                             */
                            void draw(const GLMapView *view, int level, int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH);
                            
                        protected:
                            virtual void drawTexture(GLTexture *tex, float *texCoords);
                            
                            /**
                             * Invokes {@link com.atakmap.map.layer.raster.gdal.opengl.GLGdalTileNode#draw}.
                             *
                             * @param view The view
                             */
                            void drawImpl(const GLMapView *view);
                            
                            GLQuadTileNode *createChild(int idx);
                            
                        private:
                            /**
                             * Adopts the specified child
                             *
                             * @param idx The {@link #children} index to adopt the child at
                             * @param child The child to adopt
                             */
                            void adopt(int idx, GLQuadTileNode *child);
                            
                            /**
                             * Orphans the specified children.
                             *
                             * @param upperLeft <code>true</code> to orphan the upper-left child
                             * @param upperRight <code>true</code> to orphan the upper-right child
                             * @param lowerLeft <code>true</code> to orphan the lower-left child
                             * @param lowerRight <code>true</code> to orphan the lower-right child
                             * @return The orphaned children
                             */
                            int orphan(bool upperLeft, bool upperRight,
                                        bool lowerLeft, bool lowerRight, GLQuadTileNode *outOrphans[]);
                            
                            /**
                             * Abandons all of the children.
                             */
                            void abandon();
                            
                            /**************************************************************************/
                            
                            // XXX - implementation for getState
                            
                        public:
                            virtual State getState();
                            
                            virtual void suspend();
                            
                            virtual void resume();
                        };
                    }
                }
            }
        }
    }
}

#endif