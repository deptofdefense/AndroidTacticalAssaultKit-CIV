#include "renderer/GL.h"
#include "renderer/feature/GLBatchGeometryCollection3.h"

#include "feature/GeometryCollection.h"
#include "renderer/core/GLLabelManager.h"
#include "renderer/feature/GLBatchLineString3.h"
#include "renderer/feature/GLBatchPoint3.h"
#include "renderer/feature/GLBatchPolygon3.h"
#include "port/STLSetAdapter.h"
#include "thread/Lock.h"
#include "util/Error.h"
#include "util/Logging.h"


using namespace TAK::Engine::Renderer::Feature;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::feature;
using namespace atakmap::renderer;
using namespace atakmap::util;

GLBatchGeometryCollection3::GLBatchGeometryCollection3(TAK::Engine::Core::RenderContext &surface_) NOTHROWS :
    GLBatchGeometry3(surface_, 10),
    collectionEntityType(0)
{}

GLBatchGeometryCollection3::GLBatchGeometryCollection3(TAK::Engine::Core::RenderContext &surface_, const int zOrder_, const int collectionEntityType_) NOTHROWS :
    GLBatchGeometry3(surface_, zOrder_),
    collectionEntityType(collectionEntityType_)
{}

GLBatchGeometryCollection3::~GLBatchGeometryCollection3() NOTHROWS
{}

//public void draw(GLMapView view) {
void GLBatchGeometryCollection3::draw(const GLMapView2 &view, const int render_pass) NOTHROWS
{
    std::set<GLBatchGeometry3 *>::iterator child;
    for (child = this->children.begin(); child != this->children.end(); child++)
        (*child)->draw(view, render_pass);
}

//public void release() {
void GLBatchGeometryCollection3::release() NOTHROWS
{
    std::set<GLBatchGeometry3 *>::iterator child;
    for (child = this->children.begin(); child != this->children.end(); child++)
        (*child)->release();
}

//public void batch(GLMapView view, GLRenderBatch batch) {
TAKErr GLBatchGeometryCollection3::batch(const GLMapView2 &view, const int render_pass, GLRenderBatch2 &batch) NOTHROWS
{
    TAKErr code(TE_Ok);

    std::set<GLBatchGeometry3 *>::iterator child;
    for (child = this->children.begin(); child != this->children.end(); child++) {
        code = (*child)->batch(view, render_pass, batch);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);
    return code;
}

//public synchronized void setStyle(Style style) {
TAKErr GLBatchGeometryCollection3::setStyle(StylePtr_const &&value) NOTHROWS
{
    return setStyle(std::shared_ptr<const atakmap::feature::Style>(std::move(value)));
}

TAKErr GLBatchGeometryCollection3::setStyle(const std::shared_ptr<const atakmap::feature::Style> &value) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);
    this->style = value;
    std::set<GLBatchGeometry3 *>::iterator child;
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
TAKErr GLBatchGeometryCollection3::setGeometry(BlobPtr &&blob, const int type, const int lod_val) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    uint32_t existingLabelId = GLLabelManager::NO_ID;

    std::set<GLBatchGeometry3*>::iterator it;
    for (it = children.begin(); it != children.end(); it++) {
        if (auto *childPoint = dynamic_cast<GLBatchPoint3 *>(*it)) {
            if (childPoint->labelId != GLLabelManager::NO_ID) {
                existingLabelId = childPoint->labelId;
            }
        }
    }

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
            Logger::log(Logger::Level::Error, "GLBatchGeometryCollection3: Bad coding: fid=%" PRId64" name=%s", this->featureId, this->name.get());
            //throw new IllegalArgumentException("Bad coding: fid=" + this.featureId + " name=" + name + " blob="+ toHexString(blob.array()));
            return TE_InvalidArg;
        }
        int codedEntityType;
        code = blob->readInt(&codedEntityType);
        TE_CHECKBREAK_CODE(code);
        if (this->collectionEntityType != 0 && codedEntityType != this->collectionEntityType) {
            Logger::log(Logger::Level::Error, "GLBatchGeometryCollection3: Invalid collectionentity encountered, expected %d, decoded %d", this->collectionEntityType, codedEntityType);
            return TE_Err;
        }
        GLBatchGeometry3Ptr childPtr(nullptr, nullptr);
        switch (codedEntityType % 1000) {
        case 1:
            childPtr = GLBatchGeometry3Ptr(new GLBatchPoint3(this->surface), Memory_deleter_const<GLBatchGeometry3>);
            static_cast<GLBatchPoint3 *>(childPtr.get())->labelId = existingLabelId;
            break;
        case 2:
            childPtr = GLBatchGeometry3Ptr(new GLBatchLineString3(this->surface), Memory_deleter_const<GLBatchGeometry3>);
            break;
        case 3:
            childPtr = GLBatchGeometry3Ptr(new GLBatchPolygon3(this->surface), Memory_deleter_const<GLBatchGeometry3>);
            break;
        default:
            Logger::log(Logger::Level::Error, "GLBatchGeometryCollection3: Invalid collectionentity encountered: %d", codedEntityType);
            return TE_InvalidArg;
        }

        childPtr->init(this->featureId, this->name, GeometryPtr_const(nullptr, Memory_leaker_const<Geometry>), this->altitudeMode, this->extrude, this->style);

        childPtr->subid = i;
        childPtr->setGeometryImpl(std::move(BlobPtr(blob.get(), Memory_leaker_const<MemoryInput2>)), type);
        GLBatchGeometry3 *child = childPtr.get();
        this->childrenPtrs.insert(std::move(childPtr));
        this->children.insert(child);
    }

    return code;
}

TAKErr GLBatchGeometryCollection3::setGeometry(const Geometry &geom) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    uint32_t existingLabelId = GLLabelManager::NO_ID;

    for (auto it_children = children.begin(); it_children != children.end(); it_children++) {
        if (auto *childPoint = dynamic_cast<GLBatchPoint3 *>(*it_children)) {
            if (childPoint->labelId != GLLabelManager::NO_ID) {
                existingLabelId = childPoint->labelId;
            }
        }
    }

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

            GLBatchGeometry3Ptr childPtr(nullptr, nullptr);
            switch ((*it)->getType()) {
            case Geometry::POINT:
                childPtr = GLBatchGeometry3Ptr(new GLBatchPoint3(this->surface), Memory_deleter_const<GLBatchGeometry3>);
                static_cast<GLBatchPoint3 *>(childPtr.get())->labelId = existingLabelId;
                break;
            case Geometry::LINESTRING:
                childPtr = GLBatchGeometry3Ptr(new GLBatchLineString3(this->surface), Memory_deleter_const<GLBatchGeometry3>);
                break;
            case Geometry::POLYGON:
                childPtr = GLBatchGeometry3Ptr(new GLBatchPolygon3(this->surface), Memory_deleter_const<GLBatchGeometry3>);
                break;
            default:
                // XXX - need to flatten nested collections
                continue;
            }

            childPtr->init(this->featureId, this->name, GeometryPtr_const(nullptr, Memory_leaker_const<Geometry>), this->altitudeMode, this->extrude, this->style);

            childPtr->subid = i++;
            childPtr->setGeometryImpl(*(*it));
            GLBatchGeometry3 *child = childPtr.get();
            this->childrenPtrs.insert(std::move(childPtr));
            this->children.insert(child);
        }
    } catch (...) {
        return TE_Err;
    }

    return code;
}

TAKErr GLBatchGeometryCollection3::setNameImpl(const char* name_val) NOTHROWS
{
    TAKErr code = GLBatchGeometry3::setNameImpl(name_val);
    std::set<GLBatchGeometry3 *>::iterator it;
    for (it = children.begin(); it != children.end(); it++) {
        (*it)->setNameImpl(name_val);
    }
    return code;
}

//protected final void setGeometryImpl(ByteBuffer blob, int type) {
TAKErr GLBatchGeometryCollection3::setGeometryImpl(BlobPtr &&blob, int type) NOTHROWS
{
    return TE_IllegalState;
}

TAKErr GLBatchGeometryCollection3::setGeometryImpl(const atakmap::feature::Geometry &geom) NOTHROWS
{
    return TE_IllegalState;
}

TAKErr GLBatchGeometryCollection3::getChildren(Collection<GLBatchGeometry3 *>::IteratorPtr &result) NOTHROWS
{
    STLSetAdapter<GLBatchGeometry3 *> adapter(this->children);
    return adapter.iterator(result);
}

TAKErr GLBatchGeometryCollection3::getChildren(Collection<std::shared_ptr<GLBatchGeometry3>> &value) NOTHROWS
{
    TAKErr code(TE_Ok);

    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    std::set<std::shared_ptr<GLBatchGeometry3>>::iterator it;
    for (it = childrenPtrs.begin(); it != childrenPtrs.end(); it++)
    {
        code = value.add(*it);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
