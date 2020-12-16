
#include "gdal_priv.h"

#include "raster/DatasetDescriptor.h"
#include "raster/DatasetProjection.h"
#include "raster/gdal/GdalDatasetProjection.h"

#include "raster/tilereader/TileReader.h"
#include "raster/gdal/GdalTileReader.h"


#include "raster/gdal/GdalLayerInfo.h"

#include "renderer/map/layer/raster/tilereader/GLQuadTileNode.h"
#include "renderer/map/layer/raster/gdal/GLGdalMapLayer.h"
#include "renderer/GLRenderContext.h"

using namespace atakmap::raster::gdal;

using namespace atakmap::renderer;
using namespace atakmap::renderer::map::layer::raster;
using namespace atakmap::renderer::raster;

using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

const char * const GLGdalMapLayer::SUPPORTED_TYPES[] = {
    "native",
    nullptr
};

namespace {
    class ProjectionDatasetProjection : public atakmap::raster::DatasetProjection {
    public:
        ProjectionDatasetProjection(atakmap::core::Projection *proj) : _proj(proj) { }
        ~ProjectionDatasetProjection() { }
        virtual bool imageToGround(const atakmap::math::PointD &image, atakmap::core::GeoPoint *ground) const {
            _proj->inverse(&image, ground);
            return true;
        }
        
        virtual bool groundToImage(const atakmap::core::GeoPoint &ground, atakmap::math::PointD *image) const {
            _proj->forward(&ground, image);
            return true;
        }
        
    private:
        atakmap::core::Projection *_proj;
    };
}

GLGdalMapLayer::GLGdalMapLayer(GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info)
: GLTiledMapLayer(context, info),
  reader(nullptr),
  dataset(nullptr),
  quadTreeInit(false),
  initializer(NULL, NULL)
  { }

GLGdalMapLayer::~GLGdalMapLayer() { }

void GLGdalMapLayer::start() { }

void GLGdalMapLayer::stop() { }

void GLGdalMapLayer::init() {
    
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("GLGdalMapLayer::init: Failed to acquire mutex");
    
    if (this->initializer.get() != nullptr)
        return;

    code = Thread_start(this->initializer, initializerThreadFunc, this);
    if (code == TE_Ok)
        this->initializer->detach();
    //TODO-- this->initializer.setPriority(Thread.NORM_PRIORITY);
}

void *GLGdalMapLayer::initializerThreadFunc(void *arg) {
    GLGdalMapLayer *thiz = static_cast<GLGdalMapLayer *>(arg);
    thiz->initImpl();
    return nullptr;
}

atakmap::raster::DatasetProjection *GLGdalMapLayer::createDatasetProjection() const {
    return new ::ProjectionDatasetProjection(atakmap::raster::gdal::GdalDatasetProjection::getInstance(this->dataset));
}

namespace  {
    struct InitGLRunnableArgs {
        GLGdalMapLayer *layer;
        GDALDataset *dataset;
    };
}

void GLGdalMapLayer::initImpl() {
    // check out of sync -- this Thread can only be set as the initializer
    // once
    //TODO-- if(this->initializer != Thread.currentThread())
    //    return;
    
    
    std::string filePath = info->getURI();
    if (filePath.compare(0, 7, "file://") == 0) {
        filePath.replace(0, 7, "");
    }
    
    
    GDALDataset *d = (GDALDataset *)GDALOpen(filePath.c_str(), GA_ReadOnly);
    if(d == nullptr) {
        //TODO--Log.e("GLGdalMapLayer", "Failed to open dataset");
        return;
    }
    
    {
        TAKErr code(TE_Ok);
        LockPtr lock(NULL, NULL);
        code = Lock_create(lock, mutex);
        if (code != TE_Ok)
            throw std::runtime_error
            ("GLGdalMapLayer::initImpl: Failed to acquire mutex");
        
        /*TODO--if (this->initializer != Thread.currentThread()) {
            // release was invoked asynchronously; we are no longer the
            // initializer so delete the dataset
            d.delete();
        } else*/ {
            InitGLRunnableArgs *args = new InitGLRunnableArgs;
            args->layer = this;
            args->dataset = d;
            this->context->runOnGLThread(initGLRunnable, args);
            /*this->surface.queueEvent(new Runnable() {
                public void run() {
                    if(GLGdalMapLayer.this->dataset != null) {
                        GLGdalMapLayer.this->quadTree = GLGdalMapLayer.this->createRoot();
                        GLGdalMapLayer.this->quadTree.setTextureCache(GLGdalMapLayer.this->surface.getTextureCache());
                        
                        // XXX - BUG: should be specifiying maxLevels-1 but then no data is
                        // rendered????
                        GLGdalMapLayer.this->quadTree.set(0, 0, GLGdalMapLayer.this->reader.getMaxNumResolutionLevels());
                    }
                }
            });*/
            
            this->initializer.reset();
        }
    }
}

void GLGdalMapLayer::initGLRunnable(void *arg) {
    InitGLRunnableArgs *args = static_cast<InitGLRunnableArgs *>(arg);
    if (args->dataset != nullptr) {
        args->layer->dataset = args->dataset;
        args->layer->proj = args->layer->createDatasetProjection();
        args->layer->reader = args->layer->createTileReader();
        args->layer->quadTree = args->layer->createRoot();
        //TODO--args->layer->quadTree->setTextureCache(GLGdalMapLayer.this->surface.getTextureCache());
        
        // XXX - BUG: should be specifiying maxLevels-1 but then no data is
        // rendered????
        args->layer->quadTree->set(0, 0, args->layer->reader->getMaxNumResolutionLevels());
    }
    delete args;
}

void GLGdalMapLayer::releaseImpl() {
    // flag that we are no longer initializing
    this->initializer.reset();
    
    if(this->reader != nullptr) {
        delete this->reader;
        this->reader = nullptr;
    }
    
    if(this->dataset != nullptr) {
        GDALClose(dataset);
        this->dataset = nullptr;
    }
}

void GLGdalMapLayer::release() {
    TAKErr code(TE_Ok);
    LockPtr lock(NULL, NULL);
    code = Lock_create(lock, mutex);
    if (code != TE_Ok)
        throw std::runtime_error
        ("GLGdalMapLayer::release: Failed to acquire mutex");
    GLTiledMapLayer::release();
    this->quadTreeInit = false;
}

atakmap::renderer::map::layer::raster::tilereader::GLQuadTileNode *GLGdalMapLayer::createRoot() {
    return new atakmap::renderer::map::layer::raster::tilereader::GLQuadTileNode(this->info->getImageryTypes().front(),
                              this->reader,
                              this->proj,
                              false);
}

atakmap::raster::tilereader::TileReader *GLGdalMapLayer::createTileReader() {
    return new atakmap::raster::gdal::GdalTileReader(this->dataset,
                                                     this->dataset->GetDescription(),
                                                     512, 512,
                                                     info->getExtraData("tilecache"),
                                                     PGSC::RefCountableIndirectPtr<atakmap::raster::tilereader::TileReader::AsynchronousIO>());
}

GLGdalMapLayer::SPI::~SPI() throw() { }

GLMapLayer *GLGdalMapLayer::SPI::createLayer(atakmap::renderer::GLRenderContext *context, const atakmap::raster::DatasetDescriptor *info) {
    
    if (!checkSupportedTypes(SUPPORTED_TYPES, info->getDatasetType())) {
        return NULL;
    }
    
    return new GLGdalMapLayer(context, info);
}
