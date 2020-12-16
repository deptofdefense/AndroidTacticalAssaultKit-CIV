
#include <sstream>

#include "renderer/map/layer/raster/tilereader/GLTileNode.h"
#include "raster/DatasetProjection.h"
#include "renderer/GLTexture.h"
#include "renderer/GLES20FixedPipeline.h"
#include "math/Utils.h"
#include "math/Point.h"
#include "renderer/map/layer/raster/gdal/GdalGraphicUtils.h"
#include "renderer/BitmapFactory.h"
#include "util/Logging.h"

#define DEBUG_DRAW 0

using namespace atakmap;
using namespace atakmap::core;
using namespace atakmap::math;

using namespace atakmap::renderer;

using namespace atakmap::renderer::map;
using namespace atakmap::renderer::map::layer::raster::tilereader;

GLTileNode::GLTileNode(const char *type, atakmap::raster::tilereader::TileReader *reader,
                       atakmap::raster::DatasetProjection *datasetProjection, bool ownsResources,
                       VertexResolver *vertexResolver, const Options *opts)
: texture(nullptr),
textureCache(nullptr),
vertexResolver(vertexResolver)
#if DEBUG_GL_TILE_NODE_RELEASE
,debugHasBeenReleased(false)
#endif 
,deleteAfterRequestAction(false)
{

    if (!datasetProjection)
        throw std::invalid_argument("NULL datasetProjection");
    
    this->type = type;
    this->tileReader = reader;
    this->proj = datasetProjection;
    
    this->textureCoordsValid = false;
    this->currentRequest = NULL;
    
    this->state = UNRESOLVED;
    this->receivedUpdate = false;
    
    this->tileColumn = -1;
    this->tileRow = -1;
    this->level = -1;
    
    this->drawInitialized = false;
    
    this->uri = this->tileReader->getUri();
    
    this->ownsResources = ownsResources;
    
    if (this->vertexResolver == nullptr) {
        this->ownedVertexResolver = this->createDefaultVertexResolver();
        this->vertexResolver = this->ownedVertexResolver.get();
    }
    
    if (opts) {
        this->options = *opts;
    }
    
    this->vertexCoordSrid = -1;
    this->vertexCoordsValid = false;
    
    this->touched = false;
}

GLTileNode::~GLTileNode() {
#if DEBUG_GL_TILE_NODE_RELEASE
    if (!this->debugHasBeenReleased) {
        printf("DELETE WITHOUT RELEASE\n");
    }
    if (this->texture != nullptr) {
        printf("STILL HAS TEXTURE\n");
    }
#endif
}

GLTileNode::VertexResolver::~VertexResolver() { }

void GLTileNode::DefaultVertexResolver::project(const GLMapView *view, int64_t imgSrcX, int64_t imgSrcY, GeoPoint *geo) {
    this->scratchImg.x = imgSrcX;
    this->scratchImg.y = imgSrcY;
    outerInst->proj->imageToGround(this->scratchImg, geo);
}

void GLTileNode::set(int64_t tileColumn, int64_t tileRow, int level) {
    /*if (DEBUG)
     Log.d(TAG, toString(false) + " set(tileColumn=" + tileColumn + ",tileRow=" + tileRow
     + ",level=" + level + ")");*/
    if (this->tileColumn == tileColumn && this->tileRow == tileRow && this->level == level)
        return;
    
    if (this->currentRequest != 0) {
        if (this->tileReader) {
            this->tileReader->cancelAsyncRead(this->currentRequest);
        }
        this->currentRequest = 0;
    }
    
    if (this->textureCache != NULL && ((this->state == RESOLVED) || this->receivedUpdate))
        this->releaseTexture();
    
    this->state = UNRESOLVED;
    this->receivedUpdate = false;
    
    this->tileSrcX = this->tileReader->getTileSourceX(level, tileColumn);
    this->tileSrcY = this->tileReader->getTileSourceY(level, tileRow);
    this->tileSrcWidth = this->tileReader->getTileSourceWidth(level, tileColumn);
    this->tileSrcHeight = this->tileReader->getTileSourceHeight(level, tileRow);
    
    this->tileWidth = this->tileReader->getTileWidth(level, tileColumn);
    this->tileHeight = this->tileReader->getTileHeight(level, tileRow);
    
    this->textureCoordsValid = false;
    
    this->tileColumn = tileColumn;
    this->tileRow = tileRow;
    this->level = level;
    
    this->drawInitialized = false;
    
    this->glTexFormat = gdal::GdalGraphicUtils::getBufferFormat(this->tileReader->getFormat());
    this->glTexType = gdal::GdalGraphicUtils::getBufferType(this->tileReader->getFormat());
    
    if (this->glTexFormat != GL_RGBA
        && !(isPowerOf2(this->tileWidth) && isPowerOf2(this->tileHeight))) {
        this->glTexFormat = GL_RGBA;
        this->glTexType = GL_UNSIGNED_BYTE;
    } else if (this->level >= this->tileReader->getMinCacheLevel()) {
        // if we are pulling from the cache, we want alpha since the tile
        // will be delivered as parts rather than as a whole
        if (this->glTexFormat == GL_LUMINANCE) {
            this->glTexFormat = GL_LUMINANCE_ALPHA;
            this->glTexType = GL_UNSIGNED_BYTE;
        } else if (this->glTexFormat == GL_RGB) {
            this->glTexFormat = GL_RGBA;
            // if(this->glTexType == GLES20FixedPipeline::getInstance()->GL_UNSIGNED_SHORT_5_6_5)
            // this->glTexType = GLES20FixedPipeline::getInstance()->GL_UNSIGNED_SHORT_5_5_5_1;
            this->glTexType = GL_UNSIGNED_BYTE;
        }
    }
    
    PointD scratchP(0, 0);
    GeoPoint scratchG;
    
    double minLat = 90;
    double maxLat = -90;
    double minLng = 180;
    double maxLng = -180;
    
    scratchP.x = this->tileSrcX;
    scratchP.y = this->tileSrcY;
    this->proj->imageToGround(scratchP, &scratchG);
    if(scratchG.latitude < minLat) minLat = scratchG.latitude;
    if(scratchG.latitude > maxLat) maxLat = scratchG.latitude;
    if(scratchG.longitude < minLng) minLng = scratchG.longitude;
    if(scratchG.longitude > maxLng) maxLng = scratchG.longitude;
    
    scratchP.x = this->tileSrcX+this->tileSrcWidth;
    scratchP.y = this->tileSrcY;
    this->proj->imageToGround(scratchP, &scratchG);
    if(scratchG.latitude < minLat) minLat = scratchG.latitude;
    if(scratchG.latitude > maxLat) maxLat = scratchG.latitude;
    if(scratchG.longitude < minLng) minLng = scratchG.longitude;
    if(scratchG.longitude > maxLng) maxLng = scratchG.longitude;
    
    scratchP.x = this->tileSrcX+this->tileSrcWidth;
    scratchP.y = this->tileSrcY+this->tileSrcHeight;
    this->proj->imageToGround(scratchP, &scratchG);
    if(scratchG.latitude < minLat) minLat = scratchG.latitude;
    if(scratchG.latitude > maxLat) maxLat = scratchG.latitude;
    if(scratchG.longitude < minLng) minLng = scratchG.longitude;
    if(scratchG.longitude > maxLng) maxLng = scratchG.longitude;
    
    scratchP.x = this->tileSrcX;
    scratchP.y = this->tileSrcY+this->tileSrcHeight;
    this->proj->imageToGround(scratchP, &scratchG);
    if(scratchG.latitude < minLat) minLat = scratchG.latitude;
    if(scratchG.latitude > maxLat) maxLat = scratchG.latitude;
    if(scratchG.longitude < minLng) minLng = scratchG.longitude;
    if(scratchG.longitude > maxLng) maxLng = scratchG.longitude;
    
    // XXX - needs to be based off of the full image to prevent seams
    //       between adjacent tiles which may have different local spatial
    //       resolutions
    int subsX = clamp(nextPowerOf2((int)ceil((maxLat-minLat) / GLMapView::recommendedGridSampleDistance)), 1, 32);
    int subsY = clamp(nextPowerOf2((int)ceil((maxLng-minLng) / GLMapView::recommendedGridSampleDistance)), 1, 32);
    
    // XXX - rendering issues if grid is not square...
    this->glTexGridWidth = max(subsX, subsY);
    this->glTexGridHeight = max(subsX, subsY);
}

namespace {
    GLTexture *getLoadingTexture() {
        static GLTexture *loadingTexture = NULL;
        if (loadingTexture == NULL) {
            int w = 64;
            int h = 64;
            int sw = 16;
            int sw2 = sw * 2;
            uint32_t px[] = {
                //0xFF7F7F7F, 0xFFAAAAAA
                0x00000000, 0x00000000
            };
            
            loadingTexture = new GLTexture(w, h, GL_RGBA, GL_UNSIGNED_BYTE);
            size_t intCount = 64 * 64;
            uint32_t *argb = new uint32_t[intCount];
            int r;
            int c;
            for (int i = 0; i < intCount; i++) {
                c = (i % w);
                r = (i / w);
                
                argb[i] = px[(((c % sw2) + (r % sw2)) / sw) % 2];
            }
            
            loadingTexture->setWrapS(GL_REPEAT);
            loadingTexture->setWrapT(GL_REPEAT);
            loadingTexture->load(argb, 0, 0, w, h);
            delete[] argb;
        }
        
        return loadingTexture;
    }
}

GLTexture *GLTileNode::getLoadingTexture(float *texCoords, int texGridWidth, int texGridHeight) {
    GLTexture *retval = ::getLoadingTexture();//GLTiledMapLayer.getLoadingTexture();
    
    float x = ((float) this->tileWidth / (float) retval->getTexWidth());
    float y = ((float) this->tileHeight / (float) retval->getTexHeight());
    
    GLTexture::createQuadMeshTexCoords(x, y, texGridWidth, texGridHeight, texCoords);
    
    return retval;
}

void GLTileNode::validateTexture() {
    if (this->texture == NULL ||
        this->texture->getTexWidth() < this->tileWidth
        || this->texture->getTexHeight() < this->tileHeight ||
        this->texture->getFormat() != this->glTexFormat
        || this->texture->getType() != this->glTexType) {
        
        if (this->texture != NULL) {
            this->texture->release();
            delete this->texture;
        }
        
        this->texture = new GLTexture(this->tileWidth, this->tileHeight, this->glTexFormat,
                                      this->glTexType);
        
        this->textureCoordsValid = false;
        this->textureCoordinates.clear();
        this->vertexCoordinates.clear();
        
    }
    
    this->validateTexVerts();
}

void GLTileNode::validateTexVerts() {
    if (!this->textureCoordsValid) {
        this->glTexGridIdxCount = GLTexture::getNumQuadMeshIndices(this->glTexGridWidth,
                                                                   this->glTexGridHeight);
        this->glTexGridVertCount = GLTexture::getNumQuadMeshVertices(this->glTexGridWidth,
                                                                     this->glTexGridHeight);
        
        int numVerts = this->glTexGridVertCount;
        if (this->textureCoordinates.size() < (numVerts * 2)) {
            this->textureCoordinates.resize(numVerts * 2);
        }
        
        if (this->glTexGridVertCount > 4) {
            if (this->glTexCoordIndices.size() < (this->glTexGridIdxCount)) {
                this->glTexCoordIndices.resize(this->glTexGridIdxCount);
            }
        } else {
            this->glTexCoordIndices.clear();
        }
        
        if (this->vertexCoordinates.size() < (numVerts * 3)) {
            this->vertexCoordinates.resize(numVerts * 3);
        }
        
        float x = ((float) this->tileWidth / (float) this->texture->getTexWidth());
        float y = ((float) this->tileHeight / (float) this->texture->getTexHeight());
        
        GLTexture::createQuadMeshTexCoords(math::Point<float>(0.0f, 0.0f),
                                           math::Point<float>(x, 0.0f),
                                           math::Point<float>(x, y),
                                           math::Point<float>(0.0f, y),
                                           this->glTexGridWidth,
                                           this->glTexGridHeight,
                                           &this->textureCoordinates[0]);
        
        if (this->glTexGridVertCount > 4) {
            GLTexture::createQuadMeshIndexBuffer(this->glTexGridWidth,
                                                 this->glTexGridHeight,
                                                 &this->glTexCoordIndices[0]);
        }
        
        
        
        
    }
    this->textureCoordsValid = true;
}

void GLTileNode::validateVertexCoords(const GLMapView *view) {
    if(!this->vertexCoordsValid || this->vertexCoordSrid != view->drawSrid) {
        // recompute vertex coordinates as necessary
        this->vertexResolver->begin(this);
        GeoPoint scratchGeo;
        PointD scratchPointD;
        int idx = 0;
        for (int i = 0; i <= this->glTexGridHeight; i++) {
            for (int j = 0; j <= this->glTexGridWidth; j++) {
                this->vertexResolver->project(view,
                                              this->tileSrcX + ((this->tileSrcWidth * j) / this->glTexGridWidth),
                                              this->tileSrcY + ((this->tileSrcHeight * i) / this->glTexGridHeight),
                                              &scratchGeo);
                
                view->scene.projection->forward(&scratchGeo, &scratchPointD);
                if(!HARDWARE_TRANSFORMS) {
                    view->scene.forwardTransform.transform(&scratchPointD, &scratchPointD);
                }
                
                this->vertexCoordinates[idx++] = (float)scratchPointD.x;
                this->vertexCoordinates[idx++] = (float)scratchPointD.y;
                this->vertexCoordinates[idx++] = (float)scratchPointD.z;
            }
        }
        this->vertexResolver->end(this);
        
        if(HARDWARE_TRANSFORMS) {
            this->vertexCoordsValid = true;
            this->vertexCoordSrid = view->drawSrid;
        }
    }
}

void GLTileNode::resolveTexture() {
    this->state = RESOLVING;
    this->currentRequest = this->tileReader->asyncRead(this->level, this->tileColumn, this->tileRow, this);
}

void GLTileNode::draw(const GLMapView *view) {
    this->view = view;
    
    if (this->textureCache != NULL && !((this->state == RESOLVED) || this->receivedUpdate))
        this->useCachedTexture();
    
    this->validateTexture();
    
    // read the data if we don't have it yet
    if ((this->state == UNRESOLVED) && (this->currentRequest == 0))
        this->resolveTexture();
    
    this->touched |= (this->state == RESOLVED || this->state == UNRESOLVABLE);
    
    if (this->state != RESOLVED) {
        if ((this->loadingTextureCoordinates.size() < (this->glTexGridVertCount * 2))) {
            this->loadingTextureCoordinates.resize(this->glTexGridVertCount * 2);
        }
        GLTexture *loadingTexture = this->getLoadingTexture(&loadingTextureCoordinates[0], this->glTexGridWidth, this->glTexGridHeight);
        
        if (loadingTexture != NULL) {
            this->validateVertexCoords(view);
            
            if(HARDWARE_TRANSFORMS) {
                GLES20FixedPipeline::getInstance()->glPushMatrix();
                GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);
            }
            this->drawTexture(loadingTexture, &this->loadingTextureCoordinates[0]);
            if(HARDWARE_TRANSFORMS) {
                GLES20FixedPipeline::getInstance()->glPopMatrix();
            }
            
            loadingTexture = NULL;
        }
    } else {
        this->loadingTextureCoordinates.clear();
    }
    if (this->receivedUpdate) {
        this->validateVertexCoords(view);
        
        if(HARDWARE_TRANSFORMS) {
            GLES20FixedPipeline::getInstance()->glPushMatrix();
            GLES20FixedPipeline::getInstance()->glLoadMatrixf(view->sceneModelForwardMatrix);
        }
        this->drawTexture(this->texture, &this->textureCoordinates[0]);
        if(HARDWARE_TRANSFORMS) {
            GLES20FixedPipeline::getInstance()->glPopMatrix();
        }
    }
    
#if DEBUG_DRAW
    this->debugDraw(view);
#endif
}

void GLTileNode::debugDraw(const GLMapView *view) {
    GLES20FixedPipeline::getInstance()->glEnableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    // GLES20FixedPipeline::getInstance()->glVertexPointer(2, GLES20FixedPipeline::getInstance()->GL_FLOAT, 0,
    // this->vertexCoordinates);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    GLES20FixedPipeline::getInstance()->glColor4f(1.0f, 0.0f, 0.0f, 1.0f);
    //TODO--GLES20FixedPipeline::getInstance()->glLineWidth(2.0f);
    
    float fb[8];
    
    GLES20FixedPipeline::getInstance()->glVertexPointer(2, GL_FLOAT, 0, fb);
    int idx;
    for (int i = 0; i < this->glTexGridHeight; i++) {
        for (int j = 0; j < this->glTexGridWidth; j++) {
            idx = (i * (this->glTexGridWidth + 1)) + j;
            fb[0] = this->vertexCoordinates[idx * 3];
            fb[1] = this->vertexCoordinates[(idx * 3) + 1];
            idx = (i * (this->glTexGridWidth + 1)) + j + 1;
            fb[2] = this->vertexCoordinates[idx * 3];
            fb[3] = this->vertexCoordinates[(idx * 3) + 1];
            idx = ((i + 1) * (this->glTexGridWidth + 1)) + j + 1;
            fb[4] = this->vertexCoordinates[idx * 3];
            fb[5] = this->vertexCoordinates[(idx * 3) + 1];
            idx = ((i + 1) * (this->glTexGridWidth + 1)) + j;
            fb[6] = this->vertexCoordinates[idx * 3];
            fb[7] = this->vertexCoordinates[(idx * 3) + 1];
            
            GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_LOOP, 0, 4);
        }
    }
    
    GLES20FixedPipeline::getInstance()->glColor4f(0.0f, 0.0f, 1.0f, 1.0f);
    
    idx = 0;
    fb[0] = this->vertexCoordinates[idx * 3];
    fb[1] = this->vertexCoordinates[(idx * 3) + 1];
    idx = this->glTexGridWidth;
    fb[2] = this->vertexCoordinates[idx * 3];
    fb[3] = this->vertexCoordinates[(idx * 3) + 1];
    idx = ((this->glTexGridHeight + 1) * (this->glTexGridWidth + 1)) - 1;
    fb[4] = this->vertexCoordinates[idx * 3];
    fb[5] = this->vertexCoordinates[(idx * 3) + 1];
    idx = (this->glTexGridHeight * (this->glTexGridWidth + 1));
    fb[6] = this->vertexCoordinates[idx * 3];
    fb[7] = this->vertexCoordinates[(idx * 3) + 1];
    
    GLES20FixedPipeline::getInstance()->glDrawArrays(GL_LINE_LOOP, 0, 4);
    
    glDisable(GL_BLEND);
    GLES20FixedPipeline::getInstance()->glDisableClientState(GLES20FixedPipeline::ClientState::CS_GL_VERTEX_ARRAY);
    
    /*
     * GLText _titleText = new
     * GLText(view.getSurface().getGlyphAtlas(view.getSurface().getMapView
     * ().getDefaultTextFormat()), this->tileColumn + "," + this->tileRow);
     * android.graphics.PointF p = new PointF(); view.forward(this->proj.inverse(new
     * PointD((this->tileSrcX+this->tileSrcWidth)/2, (this->tileSrcY+this->tileSrcHeight)/2), NULL),
     * p); GLES20FixedPipeline::getInstance()->glPushMatrix(); GLES20FixedPipeline::getInstance()->glTranslatef(p.x, p.y, 0);
     * _titleText.draw(this->tileColumn + "," + this->tileRow, new float[] {1.0f, 1.0f, 1.0f,
     * 1.0f}); GLES20FixedPipeline::getInstance()->glPopMatrix();
     */
}

void GLTileNode::drawTexture(GLTexture *tex, float *texCoords) {
    if (this->glTexCoordIndices.size() == 0) {
        GLTexture::draw(tex->getTexId(),
                        GL_TRIANGLE_STRIP,
                        this->glTexGridVertCount,
                        2, GL_FLOAT, texCoords,
                        3, GL_FLOAT, &this->vertexCoordinates[0]);
    } else {
        GLTexture::draw(tex->getTexId(),
                        GL_TRIANGLE_STRIP,
                        this->glTexGridIdxCount,
                        2, GL_FLOAT, texCoords,
                        3, GL_FLOAT, &this->vertexCoordinates[0],
                        GL_UNSIGNED_SHORT, &this->glTexCoordIndices[0]);
    }
}

void GLTileNode::release() {
    
    if (this->currentRequest != 0) {
        
        if (this->tileReader) {
            this->deleteAfterRequestAction = true;
            this->tileReader->cancelAsyncRead(this->currentRequest);
        }
        
        this->currentRequest = 0;
    }
    if (this->texture != NULL)
        this->releaseTexture();
    
    this->loadingTextureCoordinates.clear();
    
    this->tileColumn = -1;
    this->tileRow = -1;
    this->level = -1;
    
    this->textureCoordsValid = false;
    
    this->drawInitialized = false;
    
    this->state = UNRESOLVED;
    this->receivedUpdate = false;
    
    if (this->ownsResources) {
        this->tileReader->dispose();
        this->tileReader = nullptr;
    }
    
    this->debugHasBeenReleased = true;
}

std::string GLTileNode::getTextureKey() {
    std::ostringstream ss;
    ss << this->getUri() << "," << this->level << "," << this->tileColumn << "," << this->tileRow;
    return ss.str();
}

void GLTileNode::releaseTexture() {
    bool resolved = (this->state == RESOLVED);
    if (this->options.textureCacheEnabled &&
        (this->textureCache != NULL && (resolved || this->receivedUpdate))) {
        
        /*TODO--this->textureCache.put(this->getTextureKey(),
         this->texture,
         this->glTexGridVertCount,
         this->textureCoordinates,
         this->vertexCoordinates,
         this->glTexGridIdxCount,
         this->glTexCoordIndices,
         resolved ? TEXTURE_CACHE_HINT_RESOLVED : 0,
         new VertexCoordInfo(this->vertexCoordSrid, this->vertexCoordsValid));*/
    } else {
        this->texture->release();
        delete this->texture;
        this->texture = nullptr;
    }
    this->texture = NULL;
    this->textureCoordinates.clear();
    this->vertexCoordinates.clear();
    this->glTexCoordIndices.clear();
    
    this->textureCoordsValid = false;
    this->vertexCoordsValid = false;
    
    this->state = UNRESOLVED;
    this->receivedUpdate = false;
}

bool GLTileNode::useCachedTexture() {
    if(!this->options.textureCacheEnabled)
        return false;
    
#if 0
    GLTextureCache.Entry cachedTexture = this->textureCache.remove(this->getTextureKey());
    if (cachedTexture == NULL)
        return false;
    if (this->texture != NULL)
        this->texture->release();
    this->texture = cachedtexture->texture;
    this->glTexFormat = this->texture->getFormat();
    this->glTexType = this->texture->getType();
    this->receivedUpdate = true;
    if (cachedtexture->hasHint(TEXTURE_CACHE_HINT_RESOLVED))
        this->state = State.RESOLVED;
    
    this->textureCoordsValid = false;
    
    this->textureCoordinates = (FloatBuffer) cachedtexture->textureCoordinates;
    this->vertexCoordinates = (FloatBuffer) cachedtexture->vertexCoordinates;
    this->glTexCoordIndices = (ByteBuffer) cachedtexture->indices;
    this->glTexGridIdxCount = cachedtexture->numIndices;
    this->glTexGridVertCount = cachedtexture->numVertices;
    // XXX -
    this->glTexGridWidth = (int) Math.sqrt(this->glTexGridVertCount) - 1;
    this->glTexGridHeight = (int) Math.sqrt(this->glTexGridVertCount) - 1;
    
    VertexCoordInfo vertexInfo = (VertexCoordInfo)cachedtexture->opaque;
    this->vertexCoordSrid = vertexInfo.srid;
    this->vertexCoordsValid = vertexInfo.valid;
    
    return true;
#else
    return false;
#endif
}

struct LoadTextureArgs {
    GLTileNode *node;
    void *buf;
    int id;
    int dstX, dstY;
    int dstW, dstH;
};

void GLTileNode::loadTextureRunnable(void *opaque) {
    LoadTextureArgs *args = static_cast<LoadTextureArgs *>(opaque);
    args->node->loadTextureImpl(args->id, args->buf, args->dstX, args->dstY, args->dstW, args->dstH);
    delete args;
}

void GLTileNode::loadTextureImpl(int id, void *buf, int dstX, int dstY, int dstW, int dstH) {
    if (checkRequest(id)) {
        this->texture->load(buf, dstX, dstY, dstW, dstH);
        this->receivedUpdate = true;
    }
    gdal::GdalGraphicUtils::freeBuffer(buf);
}

struct ClearRequestArgs {
    GLTileNode *node;
    int id;
};

void GLTileNode::clearRequestRunnable(void *opaque) {
    ClearRequestArgs *args = static_cast<ClearRequestArgs *>(opaque);
    args->node->clearRequestImpl(args->id);
    delete args;
}

void GLTileNode::clearRequestImpl(int id) {
    if (!checkRequest(id))
        return;
    this->currentRequest = 0;
}

struct SetStateArgs {
    GLTileNode *node;
    int id;
    GLTileNode::State state;
};

void GLTileNode::setStateRunnable(void *opaque) {
    SetStateArgs *args = static_cast<SetStateArgs *>(opaque);
    args->node->setStateImpl(args->id, args->state);
    delete args;
}

void GLTileNode::setStateImpl(int id, State state) {
    if (!checkRequest(id))
        return;
    this->state = state;
    this->currentRequest = NULL;
}

void GLTileNode::requestUpdate(int id, const void *data, size_t dataSize, int dstX,  int dstY,
                               int dstW,  int dstH) {
    /*if (DEBUG) {
     Log.d(TAG, toString(false) + " requestUpdate(id=" + id + "), currentRequest="
     + this->currentRequest);
     }*/
    
    int current = this->currentRequest;
    if (current == 0 || current != id || !this->tileReader)
        return;
    
    void *buf = gdal::GdalGraphicUtils::createBuffer(data,
                                                     dstW,
                                                     dstH,
                                                     this->tileReader->getInterleave(),
                                                     this->tileReader->getFormat(),
                                                     this->glTexFormat,
                                                     this->glTexType);
    //XXX---
 //   memcpy(buf, data, dataSize);
    LoadTextureArgs *args = new LoadTextureArgs;
    args->buf = buf;
    args->dstX = dstX;
    args->dstY = dstY;
    args->dstW = dstW;
    args->dstH = dstH;
    args->id = id;
    args->node = this;
    
    this->view->getRenderContext()->runOnGLThread(loadTextureRunnable, args);
}

void deleteGLTileNodeRunnable(void *args) {
    GLTileNode *node = static_cast<GLTileNode *>(args);
    delete node;
}

void GLTileNode::requestCompleted(int id) {
    /*if (DEBUG) {
     Log.d(TAG, toString(false) + " requestCompleted(id=" + id + "), currentRequest="
     + this->currentRequest);
     }*/
    
    if (this->deleteAfterRequestAction) {
        this->view->getRenderContext()->runOnGLThread(deleteGLTileNodeRunnable, this);
    } else {
        int current = this->currentRequest;
        if (current == 0 || current != id)
            return;
        
        SetStateArgs *args = new SetStateArgs;
        args->node = this;
        args->id = id;
        args->state = RESOLVED;
        this->view->getRenderContext()->runOnGLThread(setStateRunnable, args);
    }
}

void GLTileNode::requestCanceled( int id) {
    /*if (DEBUG)
     Log.d(TAG, toString(false) + " requestCanceled(id=" + id + "), currentRequest="
     + this->currentRequest);*/
    
    if (this->deleteAfterRequestAction) {
        this->view->getRenderContext()->runOnGLThread(deleteGLTileNodeRunnable, this);
    } else {
        int current = this->currentRequest;
        if (current == 0 || current != id)
            return;
        
        ClearRequestArgs *args = new ClearRequestArgs;
        args->node = this;
        args->id = id;
        this->view->getRenderContext()->runOnGLThread(clearRequestRunnable, args);
    }
}

void GLTileNode::requestError( int id, const char *what) {
    /*if (DEBUG) {
     Log.d(TAG, toString(false) + " requestError(id=" + id + "), currentRequest="
     + this->currentRequest);
     }*/
    
    //Log.e(TAG, "asynchronous read error", error);
    util::Logger::log(util::Logger::Debug, "GLTileNode: asynchronous read error");
    
    if (this->deleteAfterRequestAction) {
        this->view->getRenderContext()->runOnGLThread(deleteGLTileNodeRunnable, this);
    } else {
        int current = this->currentRequest;
        if (current == 0 || current != id)
            return;
        
        SetStateArgs *args = new SetStateArgs;
        args->node = this;
        args->id = id;
        args->state = UNRESOLVABLE;
        
        this->view->getRenderContext()->runOnGLThread(setStateRunnable, args);
    }
}

bool GLTileNode::imageToGround(const math::PointD &p, core::GeoPoint *g) {
    return this->proj->imageToGround(p, g);
}

bool GLTileNode::groundToImage(const core::GeoPoint &g, math::PointD *p) {
    return this->proj->groundToImage(g, p);
}

bool GLTileNode::contains(const core::GeoPoint &g) {
    math::PointD image(0.0, 0.0);
    if(!this->proj->groundToImage(g, &image) || !this->tileReader)
        return false;
    return (image.x >= 0 && image.x < this->tileReader->getWidth() && image.y >= 0 && image.y < this->tileReader
            ->getHeight());
}