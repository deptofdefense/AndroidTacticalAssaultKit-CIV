#include "renderer/feature/GLBatchGeometryCollection2.h"

#include "feature/GeometryCollection.h"
#include "renderer/feature/GLBatchLineString2.h"
#include "renderer/feature/GLBatchPoint2.h"
#include "renderer/feature/GLBatchPolygon2.h"
#include "port/STLSetAdapter.h"
#include "thread/Lock.h"
#include "util/Error.h"
#include "util/Logging.h"


using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::renderer::map;
using namespace atakmap::util;

GLBatchGeometryCollection2::GLBatchGeometryCollection2(RenderContext &surface_) NOTHROWS :
    GLBatchGeometry2(surface_, 10),
    collectionEntityType(0)
{}

GLBatchGeometryCollection2::GLBatchGeometryCollection2(RenderContext &surface_, const int zOrder_, const int collectionEntityType_) NOTHROWS :
    GLBatchGeometry2(surface_, zOrder_),
    collectionEntityType(collectionEntityType_)
{}

GLBatchGeometryCollection2::~GLBatchGeometryCollection2() NOTHROWS
{}

TAKErr GLBatchGeometryCollection2::init(int64_t feature_id, const char *name_val) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    GLBatchGeometry2::init(feature_id, name_val);
    std::set<GLBatchGeometry2 *>::iterator child;
    for (child = this->children.begin(); child != this->children.end(); child++) {
        code = (*child)->init(feature_id, name_val);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}

//public void draw(GLMapView view) {
void GLBatchGeometryCollection2::draw(const GLMapView *view)
{
    std::set<GLBatchGeometry2 *>::iterator child;
    for (child = this->children.begin(); child != this->children.end(); child++)
        (*child)->draw(view);
}

//public void release() {
void GLBatchGeometryCollection2::release()
{
    std::set<GLBatchGeometry2 *>::iterator child;
    for (child = this->children.begin(); child != this->children.end(); child++)
        (*child)->release();
}

//public boolean isBatchable(GLMapView view) {
bool GLBatchGeometryCollection2::isBatchable(const GLMapView *view)
{
    return true;
}

//public void batch(GLMapView view, GLRenderBatch batch) {
void GLBatchGeometryCollection2::batch(const GLMapView *view, GLRenderBatch *batch)
{
    bool inbatch = true;
    bool batchable;
    std::set<GLBatchGeometry2 *>::iterator child;
    for (child = this->children.begin(); child != this->children.end(); child++) {
        batchable = (*child)->isBatchable(view);
        if (batchable && !inbatch)
            batch->begin();
        else if (!batchable && inbatch)
            batch->end();
        if (batchable)
            (*child)->batch(view, batch);
        else
            (*child)->draw(view);
    }
    if (!inbatch)
        batch->begin();
}

//public synchronized void setStyle(Style style) {
TAKErr GLBatchGeometryCollection2::setStyle(StylePtr_const &&value) NOTHROWS
{
    return setStyle(std::shared_ptr<const atakmap::feature::Style>(std::move(value)));
}

TAKErr GLBatchGeometryCollection2::setStyle(std::shared_ptr<const atakmap::feature::Style> value) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    this->style = value;
    std::set<GLBatchGeometry2 *>::iterator child;
    for (child = this->children.begin(); child != this->children.end(); child++)
        (*child)->setStyle(this->style);
    return TE_Ok;
}

#if 0
private String toHexString(byte[] arr) {
    StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < arr.length; i++)
        sb.append(String.format("%02X, ", arr[i] & 0xFF));
    sb.append("}");
    return sb.toString();
}
#endif

//public synchronized void setGeometry(final ByteBuffer blob, final int type, int lod) {
TAKErr GLBatchGeometryCollection2::setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    this->lod = lod_val;
    this->children.clear();
    this->childrenPtrs.clear();

    int numPoints;
    code = blob->readInt(&numPoints);
    TE_CHECKRETURN_CODE(code);
    for (int i = 0; i < numPoints; i++) {
        uint8_t entity;
        code = blob->readByte(&entity);
        if (entity != 0x69u) {
            Logger::log(Logger::Level::Error, "GLBatchGeometryCollection2: Bad coding: fid=%" PRId64" name=%s", this->featureId, this->name.get());
            //throw new IllegalArgumentException("Bad coding: fid=" + this.featureId + " name=" + name + " blob="+ toHexString(blob.array()));
            return TE_InvalidArg;
        }
        int codedEntityType;
        code = blob->readInt(&codedEntityType);
        TE_CHECKBREAK_CODE(code);
        if (this->collectionEntityType != 0 && codedEntityType != this->collectionEntityType) {
            Logger::log(Logger::Level::Error, "GLBatchGeometryCollection2: Invalid collectionentity encountered, expected %d, decoded %d", this->collectionEntityType, codedEntityType);
            return TE_Err;
        }
        GLBatchGeometryPtr childPtr(nullptr, nullptr);
        switch (codedEntityType) {
        case 1:
            childPtr = GLBatchGeometryPtr(new GLBatchPoint2(this->surface), Memory_deleter_const<GLBatchGeometry2>);
            break;
        case 2:
            childPtr = GLBatchGeometryPtr(new GLBatchLineString2(this->surface), Memory_deleter_const<GLBatchGeometry2>);
            break;
        case 3:
            childPtr = GLBatchGeometryPtr(new GLBatchPolygon2(this->surface), Memory_deleter_const<GLBatchGeometry2>);
            break;
        default:
            Logger::log(Logger::Level::Error, "GLBatchGeometryCollection2: Invalid collectionentity encountered: %d", codedEntityType);
            return TE_InvalidArg;
        }

        childPtr->init(this->featureId, this->name);
        childPtr->subid = i;
        childPtr->setGeometryImpl(std::move(BlobPtr(blob.get(), Memory_leaker_const<MemoryInput2>)), type);
        if (this->style != nullptr)
            childPtr->setStyle(std::move(StylePtr_const(this->style.get(), Memory_leaker_const<atakmap::feature::Style>)));
        GLBatchGeometry2 *child = childPtr.get();
        this->childrenPtrs.insert(std::move(childPtr));
        this->children.insert(child);
    }

    return code;
}

TAKErr GLBatchGeometryCollection2::setGeometry(const Geometry &geom) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    this->lod = lod;
    this->children.clear();
    this->childrenPtrs.clear();

    if (geom.getType() != Geometry::COLLECTION)
        return TE_InvalidArg;

    const auto &collection = static_cast<const GeometryCollection &>(geom);

    try {
        std::pair<std::vector<Geometry *>::const_iterator,
            std::vector<Geometry *>::const_iterator> elems = collection.contents();
        std::vector<Geometry *>::const_iterator it;
        int i = 1;
        for (it = elems.first; it != elems.second; it++) {
            // XXX - type specialization check

            GLBatchGeometryPtr childPtr(nullptr, nullptr);
            switch ((*it)->getType()) {
            case Geometry::POINT:
                childPtr = GLBatchGeometryPtr(new GLBatchPoint2(this->surface), Memory_deleter_const<GLBatchGeometry2>);
                break;
            case Geometry::LINESTRING:
                childPtr = GLBatchGeometryPtr(new GLBatchLineString2(this->surface), Memory_deleter_const<GLBatchGeometry2>);
                break;
            case Geometry::POLYGON:
                childPtr = GLBatchGeometryPtr(new GLBatchPolygon2(this->surface), Memory_deleter_const<GLBatchGeometry2>);
                break;
            default:
                // XXX - need to flatten nested collections
                continue;
            }

            childPtr->init(this->featureId, this->name);
            childPtr->subid = i++;
            childPtr->setGeometryImpl(*(*it));
            if (this->style != nullptr)
                childPtr->setStyle(std::move(StylePtr_const(this->style.get(), Memory_leaker_const<atakmap::feature::Style>)));
            GLBatchGeometry2 *child = childPtr.get();
            this->childrenPtrs.insert(std::move(childPtr));
            this->children.insert(child);
        }
    } catch (...) {
        return TE_Err;
    }

    return code;
}

//protected final void setGeometryImpl(ByteBuffer blob, int type) {
TAKErr GLBatchGeometryCollection2::setGeometryImpl(BlobPtr &&blob, int type) NOTHROWS
{
    return TE_IllegalState;
}

TAKErr GLBatchGeometryCollection2::setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS
{
    return TE_IllegalState;
}

TAKErr GLBatchGeometryCollection2::getChildren(Collection<GLBatchGeometry2 *>::IteratorPtr &result) NOTHROWS
{
    STLSetAdapter<GLBatchGeometry2 *> adapter(this->children);
    return adapter.iterator(result);
}

TAKErr GLBatchGeometryCollection2::getChildren(Collection<std::shared_ptr<GLBatchGeometry2>> &value) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::set<std::shared_ptr<GLBatchGeometry2>>::iterator it;
    for (it = childrenPtrs.begin(); it != childrenPtrs.end(); it++)
    {
        code = value.add(*it);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
