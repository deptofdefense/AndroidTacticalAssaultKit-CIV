
#ifndef ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEREADER_GLTILENODE_H_INCLUDED
#define ATAKMAP_RENDERER_MAP_LAYER_RASTER_TILEREADER_GLTILENODE_H_INCLUDED

#include <vector>

#include "renderer/map/GLResolvableMapRenderable.h"
#include "raster/tilereader/TileReader.h"

#define DEBUG_GL_TILE_NODE_RELEASE 1

namespace atakmap {
    
    namespace raster {
        class DatasetProjection;
    }
    
    namespace renderer {
        
        class GLTexture;
        class GLTextureCache;
        
        namespace map {
            namespace layer {
                namespace raster {
                    namespace tilereader {
                        
                        class GLTileNode : public GLResolvableMapRenderable,
                        public atakmap::raster::tilereader::TileReader::AsynchronousReadRequestListener/*, MapData*/ {
                            
                        public:
                            struct Options {
                                
                                bool textureCopyEnabled;
                                bool childTextureCopyResolvesParent;
                                bool textureCacheEnabled;
                                bool forceLoResLoadOnError;
                                bool progressiveLoad;
                                double levelTransitionAdjustment;
                                
                                Options() {
                                    this->textureCopyEnabled = true;
                                    this->childTextureCopyResolvesParent = true;
                                    this->textureCacheEnabled = true;
                                    this->hardwareTransforms = false;
                                    this->forceLoResLoadOnError = false;
                                    this->progressiveLoad = false;
                                    this->levelTransitionAdjustment = 0;
                                }
                                
                            private:
                                bool hardwareTransforms;
                            };
                            
                        protected:
                            class VertexResolver {
                            public:
                                virtual ~VertexResolver();
                                
                                virtual void begin(GLTileNode *node) = 0;
                                
                                virtual void end(GLTileNode *node) = 0;
                                
                                virtual void project(const map::GLMapView *view, int64_t imgSrcX, int64_t imgSrcY, core::GeoPoint *geo) = 0;
                            };
                            
                            class DefaultVertexResolver : public VertexResolver {
                            private:
                                math::PointD scratchImg;
                                
                            public:
                                DefaultVertexResolver(GLTileNode *outerInst)
                                : outerInst(outerInst) { }
                                virtual ~DefaultVertexResolver() { }
                                
                                virtual void begin(GLTileNode *node) { }
                                
                                virtual void end(GLTileNode *node) { }
                                
                                virtual void project(const GLMapView *view, int64_t imgSrcX, int64_t imgSrcY, core::GeoPoint *geo);
                            private:
                                GLTileNode *outerInst;
                            };
                            
                        private:
                            
                            /*                        static {
                             GdaltileReader->setPaletteRgbaFormat(GdaltileReader->Format.RGBA);
                             }*/
                            
                        protected:
                            static const int TEXTURE_CACHE_HINT_RESOLVED = 0x00000001;
                            
                            static const bool HARDWARE_TRANSFORMS = false;
                            
                            atakmap::raster::tilereader::TileReader *tileReader;
                            
                            /**
                             * The read request currently being serviced. Valid access to the request is only guaranteed on
                             * the render thread.
                             */
                            int currentRequest;
                            
                            /**
                             * Projects between the image coordinate space and WGS84.
                             */
                            atakmap::raster::DatasetProjection *proj;
                            
                            GLTexture *texture;
                            
                            const GLMapView *view;
                            
                            int64_t tileRow;
                            int64_t tileColumn;
                            int level;
                            
                            State state;
                            
                            // source coordinate space (unscaled)
                            int64_t tileSrcX;
                            int64_t tileSrcY;
                            int64_t tileSrcWidth;
                            int64_t tileSrcHeight;
                            
                            int tileWidth;
                            int tileHeight;
                            
                        private:
                            int drawVersion;
                            
                        protected:
                            bool drawInitialized;
                            
                            /**
                             * The texture coordinate for the texture in the order lower-left,
                             * lower-right, upper-right, upper-left. Using this order relieves us of the
                             * need to do a vertical flip of the raster data.
                             */
                            std::vector<float> textureCoordinates;
                            
                            /**
                             * The texture coordinate for the texture in the order upper-left,
                             * upper-right, lower-right, lower-left. Using this order relieves us of the
                             * need to do a vertical flip of the raster data.
                             */
                            std::vector<float> vertexCoordinates;
                            
                            std::vector<short> glTexCoordIndices;
                            
                        private:
                            std::vector<float> loadingTextureCoordinates;
                            
#if DEBUG_GL_TILE_NODE_RELEASE
                            bool debugHasBeenReleased;
#endif
                            
                            
                        public:
                            bool deleteAfterRequestAction;
                            
                        protected:
                            bool textureCoordsValid;
                            bool receivedUpdate;

                            PGSC::String type;
                            
                            PGSC::String uri;
                            
                            int glTexType;
                            int glTexFormat;
                            
                            GLTextureCache *textureCache;
                            
                            bool ownsResources;
                            
                            int glTexGridWidth;
                            int glTexGridHeight;
                            int glTexGridVertCount;
                            int glTexGridIdxCount;
                            
                            VertexResolver *vertexResolver;
                            
                            int vertexCoordSrid;
                            bool vertexCoordsValid;
                            
                            Options options;
                            
                            bool touched;
                            
                        private:
                            std::unique_ptr<VertexResolver> ownedVertexResolver;
                            
                        public:
                            GLTileNode(const char *type, atakmap::raster::tilereader::TileReader *reader,
                                       atakmap::raster::DatasetProjection *datasetProjection, bool ownsResources, const Options *opts)
                            : GLTileNode(type, reader, datasetProjection, ownsResources, NULL, opts)
                            { }
                            
                            GLTileNode(const char *type, atakmap::raster::tilereader::TileReader *reader, atakmap::raster::DatasetProjection *datasetProjection,
                                       bool ownsResources, class VertexResolver *vertexResolver, const Options *opts);
                            
                            virtual ~GLTileNode();
                            
                        protected:
                            std::unique_ptr<VertexResolver> createDefaultVertexResolver() {
                                return std::unique_ptr<VertexResolver>(new DefaultVertexResolver(this));
                            }
                            
                        public:
                            atakmap::raster::DatasetProjection *getDatasetProjection() {
                                return this->proj;
                            }
                            
                            atakmap::raster::tilereader::TileReader *getReader() {
                                return this->tileReader;
                            }
                            
                            int64_t getTileColumn() {
                                return this->tileColumn;
                            }
                            
                            int64_t getTileRow() {
                                return this->tileRow;
                            }
                            
                            int getLevel() {
                                return this->level;
                            }
                            
                            /**
                             * <P>
                             * <B>IMPORTANT:</B> Must be invoked on the render thread.
                             *
                             * @param tileColumn
                             * @param tileRow
                             * @param level
                             */
                            void set(int64_t tileColumn, int64_t tileRow, int level);
                            
                        public:
                            /*TODO--void setTextureCache(GLTextureCache cache) {
                             this->textureCache = cache;
                             }*/
                            
                        protected:
                            virtual GLTexture *getLoadingTexture(float *texCoords, int texGridWidth, int texGridHeight);
                            
                            void validateTexture();
                            
                            void validateTexVerts();
                            
                            void validateVertexCoords(const GLMapView *view);
                            
                            virtual void resolveTexture();
                        public:
                            void draw(const GLMapView *view);
                            
                        private:
                            void debugDraw(const GLMapView *view);
                            
                        protected:
                            void drawTexture(GLTexture *tex, float *texCoords);
                            
                        public:
                            void release();
                            
                        protected:
                            std::string getTextureKey();
                            
                            void releaseTexture();
                            
                        private:
                            struct VertexCoordInfo {
                                int srid;
                                bool valid;
                                
                                VertexCoordInfo(int srid, bool valid) {
                                    this->srid = srid;
                                    this->valid = valid;
                                }
                            };
                            
                        protected:
                            bool useCachedTexture();
                            
                            /**
                             * <P>
                             * <B>IMPORTANT:</B> Must be invoked on the render thread.
                             *
                             * @param id
                             * @return
                             */
                        private:
                            bool checkRequest(int id) {
                                return (this->currentRequest != 0 && this->currentRequest == id);
                            }
                            
                        protected:
                            void expandTexGrid() {
                                this->glTexGridWidth *= 2;
                                this->glTexGridHeight *= 2;
                                
                                this->textureCoordsValid = false;
                                this->drawInitialized = false;
                            }
                            
                            /**************************************************************************/
                            // Asynchronous Read Request Listener
                            
                            virtual void requestCreated(int request) {
                                this->currentRequest = request;
                            }
                            
                        public:
                            virtual void requestStarted(int id) {
                                /*if (DEBUG) {
                                 Log.d(TAG, toString(false) + " requestStarted(id=" + id + "), currentRequest="
                                 + this->currentRequest);
                                 }*/
                            }
                            
                            virtual void requestUpdate(int id, const void *data, size_t dataSize, int dstX,  int dstY,
                                                       int dstW,  int dstH);
                            
                            virtual void requestCompleted(int id);
                            
                            virtual void requestCanceled( int id);
                            
                            virtual void requestError(int id, const char *what);
                            
                            
                            static void loadTextureRunnable(void *opaque);
                            
                            void loadTextureImpl(int id, void *buf, int dstX, int dstY, int dstW, int dstH);
                            
                            static void clearRequestRunnable(void *opaque);
                            
                            void clearRequestImpl(int id);
                            
                            static void setStateRunnable(void *opaque);
                            
                            void setStateImpl(int id, State state);
                            
                            /**************************************************************************/
                            // Map Data
                            
                            const char *getType() {
                                return this->type;
                            }
                            
                            const char *getUri() {
                                return this->uri;
                            }
                            
                            bool imageToGround(const math::PointD &p, core::GeoPoint *g);
                            bool groundToImage(const core::GeoPoint &g, math::PointD *p);
                            bool contains(const core::GeoPoint &g);
                            
                            /**************************************************************************/
                            // GL Resolvable Map Renderable
                            
                            virtual State getState() {
                                return this->state;
                            }
                            
                            virtual void suspend() {
                                if (this->state == RESOLVING && this->currentRequest != 0) {
                                    this->tileReader->cancelAsyncRead(this->currentRequest);
                                    this->state = SUSPENDED;
                                }
                            }
                            
                            virtual void resume() {
                                // move us back into the unresolved from suspended to re-enable texture
                                // loading
                                if (this->state == SUSPENDED)
                                    this->state = UNRESOLVED;
                            }
                            
                            /**************************************************************************/
                            
                            
                            
                            
                        };
                        
                    }
                }
            }
        }
    }
}

#endif