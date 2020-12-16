
#include <algorithm>

#include "renderer/GL.h"

#include "math/Utils.h"
#include "renderer/map/GLMapView.h"
#include "renderer/map/layer/raster/tilereader/GLQuadTileNode.h"
#include "raster/DatasetDescriptor.h"
#include "raster/DatasetProjection.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/GLTexture.h"
#include "raster/DatasetDescriptor.h"
#include "util/Distance.h"


using namespace atakmap::math;
using namespace atakmap::core;
using namespace atakmap::raster;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::renderer::map::layer::raster::tilereader;

bool GLQuadTileNode::offscreenFboFailed = false;

GLQuadTileNode::GLQuadTileNode(const char *type,
               atakmap::raster::tilereader::TileReader *reader,
               atakmap::raster::DatasetProjection *proj,
               bool ownsResources)
: GLQuadTileNode(type, reader, proj, ownsResources, NULL)
{ }


GLQuadTileNode::GLQuadTileNode(const char *type,
               atakmap::raster::tilereader::TileReader *reader,
               atakmap::raster::DatasetProjection *proj,
               bool ownsResources,
               const Options *opts)
: GLQuadTileNode(NULL, -1, type, reader, proj, ownsResources, NULL, opts)
{ }

GLQuadTileNode::GLQuadTileNode(const char *type,
               atakmap::raster::tilereader::TileReader *reader,
               atakmap::raster::DatasetProjection *proj,
               bool ownsResources, VertexResolver *vertexResolver, const Options *opts)
: GLQuadTileNode(NULL, -1, type, reader, proj, ownsResources, vertexResolver, opts)
{ }

GLQuadTileNode::GLQuadTileNode(GLQuadTileNode *parent, int idx)
: GLQuadTileNode(parent, idx, parent->type, parent->tileReader, parent->proj, false,
                 parent->vertexResolver, &parent->options)
{}

GLQuadTileNode::GLQuadTileNode(GLQuadTileNode *parent, int idx,
               const char *type,
               atakmap::raster::tilereader::TileReader *reader,
               atakmap::raster::DatasetProjection *proj,
               bool ownsResources, VertexResolver *vertexResolver,
               const Options *opts)
: GLTileNode(type, reader, proj, ownsResources, vertexResolver, opts),
borrowingFrom(nullptr) {
    
    for (int i = 0; i < childrenLength; ++i)
        this->children[i] = NULL;
    
    this->parent = parent;
    //this->borrowing = Collections.newSetFromMap(new IdentityHashMap<GLQuadTileNode, Boolean>());
    
    this->halfTileWidth = (this->tileReader->getTileWidth() / 2);
    this->halfTileHeight = (this->tileReader->getTileHeight() / 2);
    
    if (this->parent != NULL)
        this->set((this->parent->tileColumn * 2) + (idx % 2), (this->parent->tileRow * 2)
                  + (idx / 2), this->parent->level - 1);
    else
        this->set(0, 0, reader->getMaxNumResolutionLevels());
    
    this->loadingTextureEnabled = true;
    
    this->derivedUnresolvableData = false;
    
    this->quadInitImpl();
}

GLQuadTileNode::~GLQuadTileNode() { }

void GLQuadTileNode::quadInitImpl() {
    if (this->parent == NULL) {
        math::PointD zero(0, 0);
        this->proj->imageToGround(zero, &this->upperLeft);
        this->proj->imageToGround(math::PointD(this->tileReader->getWidth() - 1, 0), &this->upperRight);
        this->proj->imageToGround(math::PointD(this->tileReader->getWidth() - 1,
                                               this->tileReader->getHeight() - 1), &this->lowerRight);
        this->proj->imageToGround(math::PointD(0, this->tileReader->getHeight() - 1), &this->lowerLeft);
        
        this->gsd = DatasetDescriptor::computeGSD(this->tileReader->getWidth(),
                                                 this->tileReader->getHeight(),
                                                 this->upperLeft,
                                                 this->upperRight,
                                                 this->lowerRight,
                                                 this->lowerLeft);
        
        this->root = this;
        
        this->frameBufferHandle = 0;
        this->depthBufferHandle = 0;
        
        this->minFilter = GL_NEAREST;
        this->magFilter = GL_LINEAR;
    } else {
        this->gsd = this->parent->gsd;
        this->upperLeft = this->parent->upperLeft;
        this->upperRight = this->parent->upperRight;
        this->lowerRight = this->parent->lowerRight;
        this->lowerLeft = this->parent->lowerLeft;
        
        this->root = this->parent->root;
        this->textureCache = this->parent->textureCache;
    }
}

void GLQuadTileNode::set(int64_t tileColumn, int64_t tileRow, int level) {
    if (this->tileColumn != tileColumn || this->tileRow != tileRow || this->level != level) {
        if (this->borrowingFrom != NULL) {
            this->borrowingFrom->unborrowTexture(this);
            this->borrowingFrom = NULL;
        }
        
        for (int i = 0; i < 4; i++)
            if (this->children[i] != NULL)
                this->children[i]->set((tileColumn * 2) + (i % 2), (tileRow * 2) + (i / 2),
                                       level - 1);
    }
    
    GLTileNode::set(tileColumn, tileRow, level);
}

void GLQuadTileNode::setTextureCache(GLTextureCache *cache) {
    /*for (int i = 0; i < this->children.length; i++)
     if (this->children[i] != NULL)
     this->children[i].setTextureCache(cache);*/
    //GLTileNode::setTextureCache(cache);
}

void GLQuadTileNode::setLoadingTextureEnabled(bool enabled) {
    this->root->loadingTextureEnabled = enabled;
}

void GLQuadTileNode::release() {

    GLTileNode::release();

    this->abandon();
    
    if (this->borrowingFrom != NULL) {
        this->borrowingFrom->unborrowTexture(this);
        this->borrowingFrom = NULL;
    }
    
    this->parent = NULL;
    this->root = NULL;
    
    this->frameBufferHandle = NULL;
    this->depthBufferHandle = NULL;
    
    this->loadingTexCoordsVertCount = 0;
}

GLTexture *GLQuadTileNode::borrowTexture(GLQuadTileNode *ref, int64_t srcX, int64_t srcY, int64_t srcW,
                         int64_t srcH, float *texCoords, int texGridWidth, int texGridHeight) {
    float extentX = ((float) this->tileWidth / (float) this->texture->getTexWidth());
    float extentY = ((float) this->tileHeight / (float) this->texture->getTexHeight());
    
    float minX = std::max(
                          ((float) (srcX - this->tileSrcX - 1) / (float) this->tileSrcWidth) * extentX, 0.0f);
    float minY = std::max(
                          ((float) (srcY - this->tileSrcY - 1) / (float) this->tileSrcHeight) * extentY, 0.0f);
    float maxX = std::min(((float) ((srcX + srcW) - this->tileSrcX + 1) / (float) this->tileSrcWidth)
         * extentX, 1.0f);
    float maxY = std::min(
                          ((float) ((srcY + srcH) - this->tileSrcY + 1) / (float) this->tileSrcHeight)
                          * extentY, 1.0f);
    
    GLTexture::createQuadMeshTexCoords(Point<float>(minX, minY),
                                       Point<float>(maxX, minY),
                                       Point<float>(maxX, maxY),
                                       Point<float>(minX, maxY),
                                       texGridWidth,
                                       texGridHeight,
                                       texCoords);
    
    if (ref != NULL)
        this->borrowing.push_back(ref);
    
    return this->texture;
}

void GLQuadTileNode::unborrowTexture(GLQuadTileNode *ref) {
    auto it = std::find(this->borrowing.begin(), this->borrowing.end(), ref);
    if (it != this->borrowing.end()) {
        this->borrowing.erase(it);
    }
}

GLTexture *GLQuadTileNode::getLoadingTexture(float *texCoords, int texGridWidth, int texGridHeight) {
    GLQuadTileNode *scratch = this->borrowingFrom;
    if(TEXTURE_BORROW_ENABLED)
        if (scratch == NULL) {
            GLQuadTileNode *updatedAncestor = NULL;
            scratch = this->parent;
            //TODO--GLTextureCache.Entry scratchTexInfo;
            while (scratch != NULL) {
                if (scratch->state == RESOLVED)
                    break;
                /*if (this->textureCache != NULL) {
                 scratchTexInfo = this->textureCache.get(scratch.getTextureKey());
                 if (scratchTexInfo != NULL
                 && scratchTexInfo.hasHint(TEXTURE_CACHE_HINT_RESOLVED))
                 break;
                 else if (scratchTexInfo != NULL && updatedAncestor == NULL)
                 updatedAncestor = scratch;
                 }*/
                if (scratch->receivedUpdate && updatedAncestor == NULL)
                    updatedAncestor = scratch;
                scratch = scratch->parent;
            }
            if (scratch == NULL)
                scratch = updatedAncestor;
            // if the ancestor is neither updated or resolved, we must have
            // found its texture in the cache
            if (scratch != NULL && !(scratch->receivedUpdate || (scratch->state == RESOLVED))) {
                if (!scratch->useCachedTexture())
                    throw std::runtime_error("illegal state");
            }
        }
    if (scratch != NULL && scratch != this->borrowingFrom) {
        if (this->borrowingFrom != NULL)
            this->borrowingFrom->unborrowTexture(this);
        this->borrowingFrom = scratch;
        this->loadingTexCoordsVertCount = this->glTexGridVertCount;
        return this->borrowingFrom->borrowTexture(this, this->tileSrcX, this->tileSrcY,
                                                  this->tileSrcWidth, this->tileSrcHeight, texCoords, texGridWidth, texGridHeight);
    } else if (this->borrowingFrom != NULL) {
        if (this->loadingTexCoordsVertCount != this->glTexGridVertCount) {
            this->borrowingFrom->borrowTexture(NULL, this->tileSrcX, this->tileSrcY,
                                               this->tileSrcWidth, this->tileSrcHeight, texCoords, texGridWidth,
                                               texGridHeight);
            this->loadingTexCoordsVertCount = this->glTexGridVertCount;
        }
        return this->borrowingFrom->texture;
    } else if (this->root->loadingTextureEnabled) {
        return GLTileNode::getLoadingTexture(texCoords, texGridWidth, texGridHeight);
    } else {
        return NULL;
    }
}

namespace {
    struct RectF {
        float left;
        float top;
        float right;
        float bottom;
    };
    
    bool getRasterROI(const GLMapView *view, int64_t rasterWidth, int64_t rasterHeight,
                      DatasetProjection *proj, GeoPoint ulG_R, GeoPoint urG_R, GeoPoint lrG_R, GeoPoint llG_R,
                      RectF *out) {
        
        double minLat = atakmap::math::min(ulG_R.latitude, urG_R.latitude, lrG_R.latitude, llG_R.latitude);
        double minLng = atakmap::math::min(ulG_R.longitude, urG_R.longitude, lrG_R.longitude, llG_R.longitude);
        double maxLat = atakmap::math::max(ulG_R.latitude, urG_R.latitude, lrG_R.latitude, llG_R.latitude);
        double maxLng = atakmap::math::max(ulG_R.longitude, urG_R.longitude, lrG_R.longitude, llG_R.longitude);
        
        double roiMinLat = clamp(minLat, view->southBound, view->northBound);
        double roiMinLng = clamp(minLng, view->westBound, view->eastBound);
        double roiMaxLat = clamp(maxLat, view->southBound, view->northBound);
        double roiMaxLng = clamp(maxLng, view->westBound, view->eastBound);
        
        RectF roi;
        if(roiMinLat != roiMaxLat && roiMinLng != roiMaxLng) {
            GeoPoint scratchGeo(roiMaxLat, roiMaxLng);
            PointD roiUL(0.0, 0.0);
            proj->groundToImage(scratchGeo, &roiUL);
            scratchGeo.set(roiMaxLat, roiMaxLng);
            PointD roiUR(0.0, 0.0);
            proj->groundToImage(scratchGeo, &roiUR);
            scratchGeo.set(roiMinLat, roiMaxLng);
            PointD roiLR(0.0, 0.0);
            proj->groundToImage(scratchGeo, &roiLR);
            scratchGeo.set(roiMinLat, roiMinLng);
            PointD roiLL(0.0, 0.0);
            proj->groundToImage(scratchGeo, &roiLL);
            
            roi.left = (float)clamp(atakmap::math::min(roiUL.x, roiUR.x, roiLR.x, roiLL.x), 0.0, (double)rasterWidth);
            roi.top = (float)clamp(atakmap::math::min(roiUL.y, roiUR.y, roiLR.y, roiLL.y), 0.0, (double)rasterHeight);
            roi.right = (float)clamp(atakmap::math::max(roiUL.x, roiUR.x, roiLR.x, roiLL.x), 0.0, (double)rasterWidth);
            roi.bottom = (float)clamp(atakmap::math::max(roiUL.y, roiUR.y, roiLR.y, roiLL.y), 0.0, (double)rasterHeight);
            
            if(roi.left == roi.right || roi.top == roi.bottom)
                return false;
            if (out)
                *out = roi;
        }
        return true;
    }
}


void GLQuadTileNode::draw(const GLMapView *view) {
    if (this->parent != NULL)
        throw std::runtime_error("External draw method should only be invoked on root node!");
    
    RectF roi;
    
    if (!getRasterROI(view,
                      this->tileReader->getWidth(),
                      this->tileReader->getHeight(), this->proj, this->upperLeft, this->upperRight,
                      this->lowerRight, this->lowerLeft, &roi)) {
        return; // no intersection
    }
    
    math::Point<float> a = view->forward(this->upperLeft);
    math::Point<float> b = view->forward(this->upperRight);
    math::Point<float> c = view->forward(this->lowerRight);
    math::Point<float> d = view->forward(this->lowerLeft);
    
    double a_c = std::sqrt(std::pow(a.x - c.x, 2) + std::pow(a.y - c.y, 2));
    double b_d = std::sqrt(std::pow(b.x - d.x, 2) + std::pow(b.y - d.y, 2));
    
    double localResolution_UL_LR = util::distance::calculateRange(this->upperLeft, this->lowerRight) / a_c;
    double localResolution_UR_LL = util::distance::calculateRange(this->upperRight, this->lowerLeft) / b_d;
    
    double mapGSD = std::sqrt(localResolution_UL_LR * localResolution_UR_LL);
    
    double scale = (this->gsd / mapGSD);
    
    // XXX - tune level calculation -- it may look better to swap to the
    // next level before we actually cross the threshold
    int level = (int)std::ceil(std::max((std::log(1.0 / scale) / std::log(2.0)) + this->options.levelTransitionAdjustment, 0.0));
    
    this->draw(view,
               std::min(level, tileReader->getMaxNumResolutionLevels() - 1),
               (int) roi.left,
               (int) roi.top,
               (int) (std::ceil(roi.right) - (int) roi.left),
               (int) (std::ceil(roi.bottom) - (int) roi.top));
}

void GLQuadTileNode::resolveTexture() {
    bool hasChildData = false;
    bool willBeResolved = true;
    for (int i = 0; i < 4; i++) {
        willBeResolved &= (this->children[i] != NULL && (this->children[i]->state == RESOLVED));
        if (this->children[i] != NULL
            && ((this->children[i]->state == RESOLVED) || this->children[i]->receivedUpdate)) {
            hasChildData = true;
        }
    }
    
    // copy data from the children to our texture
    if(this->options.textureCopyEnabled /*&& GLMapSurface.SETTING_enableTextureTargetFBO*/ && TEXTURE_COPY_ENABLED && !offscreenFboFailed) {
        if (hasChildData) {
            // XXX - luminance is not renderable for FBO
            if (!willBeResolved) {
                this->glTexFormat = GL_RGBA;
                this->glTexType = GL_UNSIGNED_BYTE;
            } else {
                if (this->glTexFormat == GL_LUMINANCE)
                    this->glTexFormat = GL_RGB;
                else if (this->glTexFormat == GL_LUMINANCE_ALPHA)
                    this->glTexFormat = GL_RGBA;
            }
            
            this->validateTexture();
            this->texture->init();
            
            int parts = 0;
            GLuint frameBuffer = this->root->frameBufferHandle;
            GLuint depthBuffer = this->root->depthBufferHandle;
            
            bool fboCreated = false;
            do {
                if (frameBuffer == 0)
                    glGenFramebuffers(1, &frameBuffer);
                
                if (depthBuffer == 0)
                    glGenRenderbuffers(1, &depthBuffer);
                
                glBindRenderbuffer(GL_RENDERBUFFER, depthBuffer);
                glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT16,
                                      this->texture->getTexWidth(), this->texture->getTexHeight());
                glBindRenderbuffer(GL_RENDERBUFFER, 0);
                glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer);
                
                // clear any pending errors
                while(glGetError() != GL_NO_ERROR)
                    ;
                glFramebufferTexture2D(GL_FRAMEBUFFER,
                                       GL_COLOR_ATTACHMENT0,
                                       GL_TEXTURE_2D,
                                       this->texture->getTexId(),
                                       0);
                
                // XXX - observing hard crash following bind of "complete"
                //       FBO on SM-T230NU. reported error is 1280 (invalid
                //       enum) on glFramebufferTexture2D. I have tried using
                //       the color-renderable formats required by GLES 2.0
                //       (RGBA4, RGB5_A1, RGB565) but all seem to produce
                //       the same outcome.
                if(glGetError() != GL_NO_ERROR)
                    break;
                
                glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, depthBuffer);
                int fboStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER);
                fboCreated = (fboStatus == GL_FRAMEBUFFER_COMPLETE);
            } while(false);
            
            if (fboCreated) {
                float *texCoordBuffer = &this->textureCoordinates[0];
                
                glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
                glClear(GL_COLOR_BUFFER_BIT);
                
                int tx;
                int ty;
                int partX;
                int partY;
                int partWidth;
                int partHeight;
                
                float childTexWidth;
                float childTexHeight;
                for (int i = 0; i < 4; i++) {
                    if (this->children[i] != NULL
                        && ((this->children[i]->state == RESOLVED) || this->children[i]->receivedUpdate)) {
                        tx = i % 2;
                        ty = i / 2;
                        partX = tx * this->halfTileWidth;
                        partY = ty * this->halfTileHeight;
                        partWidth = (std::min((tx + 1) * (this->halfTileWidth), this->tileWidth) - partX);
                        partHeight = (std::min((ty + 1) * (this->halfTileHeight), this->tileHeight) - partY);
                        childTexWidth = this->children[i]->texture->getTexWidth();
                        childTexHeight = this->children[i]->texture->getTexHeight();
                        // ll
                        texCoordBuffer[0] = 0.f/childTexWidth;
                        texCoordBuffer[1] = 0.f/childTexHeight;
                        this->children[i]->vertexCoordinates[0] = partX;
                        this->children[i]->vertexCoordinates[1] = partY;
                        // lr
                        texCoordBuffer[2] = (float) this->children[i]->tileWidth
                                           / childTexWidth;
                        texCoordBuffer[3] = 0.f/childTexHeight;
                        this->children[i]->vertexCoordinates[2] = partX + partWidth;
                        this->children[i]->vertexCoordinates[3] = partY;
                        // ur
                        texCoordBuffer[4] = (float) this->children[i]->tileWidth
                                           / childTexWidth;
                        texCoordBuffer[5] = (float) this->children[i]->tileHeight
                                           / childTexHeight;
                        this->children[i]->vertexCoordinates[4] = partX + partWidth;
                        this->children[i]->vertexCoordinates[5] = partY + partHeight;
                        // ul
                        texCoordBuffer[6] = 0.f/childTexWidth;
                        texCoordBuffer[7] = (float) this->children[i]->tileHeight
                                           / childTexHeight;
                        this->children[i]->vertexCoordinates[6] = partX;
                        this->children[i]->vertexCoordinates[7] = partY + partHeight;
                        
                        this->children[i]->texture->draw(4, GL_FLOAT,
                                                         &this->textureCoordinates[0],
                                                         &this->children[i]->vertexCoordinates[0]);
                        
                        // the child's vertex coordinates are now invalid
                        this->children[i]->drawInitialized = false;
                        this->children[i]->vertexCoordsValid = false;
                        
                        // if the child is resolved, increment parts
                        if (this->children[i]->state == RESOLVED)
                            parts++;
                    }
                }
                
                glBindFramebuffer(GL_FRAMEBUFFER, 0);
                
                this->textureCoordsValid = false;
                this->receivedUpdate = true;
                
                this->validateTexVerts();
                
                if(!willBeResolved) {
                    this->texture->setMagFilter(GL_NEAREST);
                    this->texture->getTexId();
                }
            } else {
                //Log.w(TAG, "Failed to create FBO for texture copy.");
                offscreenFboFailed = true;
            }
            
            bool wasUnresolvable = (this->state == UNRESOLVABLE);
            
            // mark resolved if all 4 children were resolved
            if (parts == 4 && (this->state == UNRESOLVABLE || this->options.childTextureCopyResolvesParent))
                this->state = RESOLVED;
            else if (this->state != SUSPENDED)
                this->state = UNRESOLVED;
            
            if(wasUnresolvable)
                this->derivedUnresolvableData |= (parts > 0);
            
            if(!willBeResolved)
                this->texture->setMinFilter(GL_NEAREST);
        }
    }
    
    // abandon the children before drawing ourself
    this->abandon();
    
    if ((this->state == RESOLVED) && this->borrowingFrom != NULL) {
        this->borrowingFrom->unborrowTexture(this);
        this->borrowingFrom = NULL;
    } else if (this->state == UNRESOLVED) {
        GLTileNode::resolveTexture();
    }
}

void GLQuadTileNode::draw(const GLMapView *view, int level, int64_t srcX, int64_t srcY, int64_t srcW, int64_t srcH) {
    this->view = view;
    
    if (this->level == level) {
        bool abandon = (this->texture == NULL);
        this->drawImpl(view);
        if (abandon && (this->texture != NULL))
            this->abandon();
    } else if (this->level > level) {
        bool unresolvedChildren = false;
        if(this->options.forceLoResLoadOnError) {
            for (int i = 0; i < this->childrenLength; i++)
                unresolvedChildren |= (this->children[i] != NULL && this->children[i]->state == UNRESOLVABLE);
            
            if (unresolvedChildren
                && (this->state == UNRESOLVED || (this->state == UNRESOLVABLE && this->derivedUnresolvableData))) {
                
                this->useCachedTexture();
                if (this->state == UNRESOLVED) {
                    GLTileNode::validateTexture();
                    GLTileNode::resolveTexture();
                }
            }
        }
        
        // when progressive load is enabled, only allow children to draw
        // once parent data for every other level has been rendered
        if(this->options.progressiveLoad && ((this->level%2) == 0 && !this->touched)) {
            this->drawImpl(view);
            return;
        }
        
        if (!unresolvedChildren && this->currentRequest != 0) {
            this->tileReader->cancelAsyncRead(this->currentRequest);
            this->currentRequest = 0;
        }
        
        int64_t maxSrcX = (srcX + srcW) - 1;
        int64_t maxSrcY = (srcY + srcH) - 1;
        
        int64_t tileMidSrcX = (this->tileSrcX + (this->halfTileWidth << this->level));
        int64_t tileMidSrcY = (this->tileSrcY + (this->halfTileHeight << this->level));
        int64_t tileMaxSrcX = (this->tileSrcX + this->tileSrcWidth) - 1;
        int64_t tileMaxSrcY = (this->tileSrcY + this->tileSrcHeight) - 1;
        
        bool left = (srcX < tileMidSrcX) && (maxSrcX > this->tileSrcX);
        bool upper = (srcY < tileMidSrcY) && (maxSrcY > this->tileSrcY);
        bool right = (srcX < tileMaxSrcX) && (maxSrcX > tileMidSrcX);
        bool lower = (srcY < tileMaxSrcY) && (maxSrcY > tileMidSrcY);
        
        // orphan all children that are not visible
        GLQuadTileNode *orphans[childrenLength];
        int orphanCount = this->orphan(!(upper && left), !(upper & right),
                                       !(lower && left), !(lower && right), orphans);
        bool visibleChildren[] = {
            (upper && left),
            (upper && right),
            (lower && left),
            (lower && right),
        };
        
        int orphanIndex = 0;
        for (int i = 0; i < childrenLength; i++) {
            if (visibleChildren[i] && this->children[i] == NULL) {
                if (orphanIndex < orphanCount)
                    this->adopt(i, orphans[orphanIndex++]);
                else
                    this->children[i] = this->createChild(i);
            }
        }
        
        // release any unused orphans
        while (orphanIndex < orphanCount) {
            GLQuadTileNode *orphan = orphans[orphanIndex++];
            orphan->release();
            if (!orphan->deleteAfterRequestAction) {
                delete orphan;
            }
        }
        
        for (int i = 0; i < childrenLength; i++)
            if (visibleChildren[i]) {
                this->children[i]->verticesInvalid = this->verticesInvalid;
                this->children[i]->draw(view, level, srcX, srcY, srcW, srcH);
            }
        
        // if no one is borrowing from us, release our texture
        if (this->borrowing.size() < 1 && this->texture != NULL && !unresolvedChildren)
            this->releaseTexture();
    } else {
        throw std::runtime_error("illegal state");
    }
    
    if (this->parent == NULL) {
        if (this->frameBufferHandle != 0) {
            glDeleteFramebuffers(1, &this->frameBufferHandle);
            this->frameBufferHandle = 0;
        }
        if (this->depthBufferHandle != 0) {
            glDeleteRenderbuffers(1, &this->depthBufferHandle);
            this->depthBufferHandle = 0;
        }
    }
    
    this->verticesInvalid = false;
}

void GLQuadTileNode::drawTexture(GLTexture *tex, float *texCoords) {
    if (tex == this->texture) {
        if (this->texture->getMinFilter() != this->root->minFilter)
            this->texture->setMinFilter(this->root->minFilter);
        if (this->texture->getMagFilter() != this->root->magFilter)
            this->texture->setMagFilter(this->root->magFilter);
    }
    GLTileNode::drawTexture(tex, texCoords);
}

void GLQuadTileNode::drawImpl(const GLMapView *view) {
    this->drawInitialized &= !this->verticesInvalid;
    this->vertexCoordsValid &= !this->verticesInvalid;
    GLTileNode::draw(view);
}

void GLQuadTileNode::adopt(int idx, GLQuadTileNode *child) {
    if (this->children[idx] != NULL)
        throw std::runtime_error("illegal state");
    this->children[idx] = child;
    this->children[idx]->set((this->tileColumn * 2) + (idx % 2), (this->tileRow * 2) + (idx / 2),
                             this->level - 1);
    this->children[idx]->parent = this;
    this->children[idx]->quadInitImpl();
}

GLQuadTileNode *GLQuadTileNode::createChild(int idx) {
    return new GLQuadTileNode(this, idx);
}

int GLQuadTileNode::orphan(bool upperLeft, bool upperRight,
                            bool lowerLeft, bool lowerRight, GLQuadTileNode *outOrphans[]) {
    struct {
        bool operator()(GLQuadTileNode *a, GLQuadTileNode *b) const {
            if (a->texture != NULL && b->texture != NULL) {
                int dx = (a->texture->getTexWidth() - b->texture->getTexWidth());
                int dy = (a->texture->getTexHeight() - b->texture->getTexHeight());
                return ((dx * dy) > 0);
            }
            if (a->texture != NULL && b->texture == NULL) {
                return true;
            }
            return false;
        }
    } OrphanCompare;
    
    
    bool shouldOrphan[] = {
        upperLeft,
        upperRight,
        lowerLeft,
        lowerRight,
    };
    
    int orphanCount = 0;
    for (int i = 0; i < sizeof(this->children) / sizeof(GLQuadTileNode *); i++) {
        outOrphans[orphanCount] = NULL;
        if (shouldOrphan[i] && this->children[i] != NULL) {
            outOrphans[orphanCount++] = this->children[i];
            this->children[i]->parent = NULL;
            this->children[i] = NULL;
        }
    }
    
    if (orphanCount > 0) {
        std::sort(outOrphans, outOrphans + orphanCount, OrphanCompare);
    }
    
    return orphanCount;
}

void GLQuadTileNode::abandon() {
    for (int i = 0; i < childrenLength; i++) {
        if (this->children[i] != NULL) {
            this->children[i]->release();
            if (!this->children[i]->deleteAfterRequestAction) {
                delete this->children[i];
            }
            this->children[i] = NULL;
        }
    }
}

GLResolvableMapRenderable::State GLQuadTileNode::getState() {
    State retval = this->state;
    State childState;
    for (int i = 0; i < childrenLength; i++) {
        if (this->children[i] == NULL)
            continue;
        childState = this->children[i]->getState();
        if (childState == UNRESOLVABLE || childState == SUSPENDED)
            return childState;
        else if (retval == UNRESOLVED || childState != RESOLVED)
            retval = childState;
    }
    return retval;
}

void GLQuadTileNode::suspend() {
    for (int i = 0; i < childrenLength; i++)
        if (this->children[i] != NULL)
            this->children[i]->suspend();
    GLTileNode::suspend();
}

void GLQuadTileNode::resume() {
    for (int i = 0; i < childrenLength; i++)
        if (this->children[i] != NULL)
            this->children[i]->resume();
    GLTileNode::resume();
}
