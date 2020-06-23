#include "model/MeshBuilder.h"

#include <algorithm>
#include <limits>
#include <map>
#include <vector>

#ifdef _MSC_VER
#include <Windows.h>
#ifndef _X86_
#define _X86_
#endif
#include <memoryapi.h>
#endif

#include "port/String.h"
#include "util/MemBuffer2.h"
#include "util/Memory.h"

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

extern "C" void te_free_v(const void *buf);
extern "C" void *te_alloc_v(const std::size_t size);

namespace
{
    typedef std::unique_ptr<const void, void(*)(const void *)> VertexArrayPtr;
    typedef VertexArrayPtr VertexPtr;

    template<class T>
    class ElementAccess
    {
    protected :
        ElementAccess(const std::size_t elementSize) NOTHROWS;
    public :
        virtual TAKErr put(MemBuffer2 &buf, const T a) NOTHROWS = 0;
        virtual TAKErr put(MemBuffer2 &buf, const T a, const T b) NOTHROWS = 0;
        virtual TAKErr put(MemBuffer2 &buf, const T a, const T b, const T c) NOTHROWS = 0;
        virtual TAKErr put(MemBuffer2 &buf, const T a, const T b, const T c, const T d) NOTHROWS = 0;
        virtual TAKErr get(T *a, MemBuffer2 &buf) NOTHROWS = 0;
        virtual TAKErr get(T *a, T *b, MemBuffer2 &buf) NOTHROWS = 0;
        virtual TAKErr get(T *a, T *b, T *c, MemBuffer2 &buf) NOTHROWS = 0;
        virtual TAKErr get(T *a, T *b, T *c, T *d, MemBuffer2 &buf) NOTHROWS = 0;
        const std::size_t transferSize(const std::size_t count) NOTHROWS;
    private :
        std::size_t elementSize;
    };


#define ELEMENT_ACCESS_DECL(t) \
    template<class T> \
    class t##_ElementAccess : public ElementAccess<T> \
    { \
    public : \
        t##_ElementAccess() NOTHROWS; \
    public : \
        TAKErr put(MemBuffer2 &buf, const T a) NOTHROWS; \
        TAKErr put(MemBuffer2 &buf, const T a, const T b) NOTHROWS; \
        TAKErr put(MemBuffer2 &buf, const T a, const T b, const T c) NOTHROWS; \
        TAKErr put(MemBuffer2 &buf, const T a, const T b, const T c, const T d) NOTHROWS; \
        TAKErr get(T *a, MemBuffer2 &buf) NOTHROWS; \
        TAKErr get(T *a, T *b, MemBuffer2 &buf) NOTHROWS; \
        TAKErr get(T *a, T *b, T *c, MemBuffer2 &buf) NOTHROWS; \
        TAKErr get(T *a, T *b, T *c, T *d, MemBuffer2 &buf) NOTHROWS; \
    };

    ELEMENT_ACCESS_DECL(uint8_t);
    ELEMENT_ACCESS_DECL(int8_t);
    ELEMENT_ACCESS_DECL(uint16_t);
    ELEMENT_ACCESS_DECL(int16_t);
    ELEMENT_ACCESS_DECL(uint32_t);
    ELEMENT_ACCESS_DECL(int32_t);
    ELEMENT_ACCESS_DECL(float);
    ELEMENT_ACCESS_DECL(double);

#undef ELEMENT_ACCESS_DECL

#define NORMALIZED_ELEMENT_ACCESS_DECL(t) \
    template<class T> \
    class t##_NormalizedElementAccess : public ElementAccess<T> \
    { \
    public : \
        t##_NormalizedElementAccess() NOTHROWS; \
    public : \
        TAKErr put(MemBuffer2 &buf, const T a) NOTHROWS; \
        TAKErr put(MemBuffer2 &buf, const T a, const T b) NOTHROWS; \
        TAKErr put(MemBuffer2 &buf, const T a, const T b, const T c) NOTHROWS; \
        TAKErr put(MemBuffer2 &buf, const T a, const T b, const T c, const T d) NOTHROWS; \
        TAKErr get(T *a, MemBuffer2 &buf) NOTHROWS; \
        TAKErr get(T *a, T *b, MemBuffer2 &buf) NOTHROWS; \
        TAKErr get(T *a, T *b, T *c, MemBuffer2 &buf) NOTHROWS; \
        TAKErr get(T *a, T *b, T *c, T *d, MemBuffer2 &buf) NOTHROWS; \
    };

    NORMALIZED_ELEMENT_ACCESS_DECL(uint8_t);
    NORMALIZED_ELEMENT_ACCESS_DECL(int8_t);
    NORMALIZED_ELEMENT_ACCESS_DECL(uint16_t);
    NORMALIZED_ELEMENT_ACCESS_DECL(int16_t);
    NORMALIZED_ELEMENT_ACCESS_DECL(uint32_t);
    NORMALIZED_ELEMENT_ACCESS_DECL(int32_t);
    NORMALIZED_ELEMENT_ACCESS_DECL(float);
    NORMALIZED_ELEMENT_ACCESS_DECL(double);

#undef NORMALIZED_ELEMENT_ACCESS_DECL

    class ModelImplBase : public Mesh
    {
    public :
        ModelImplBase(const DrawMode &mode, const VertexDataLayout &layout) NOTHROWS;
        ModelImplBase(const DrawMode &mode, const VertexDataLayout &layout, const DataType &indexType) NOTHROWS;
    private :
        void initAttributeAccess() NOTHROWS;
    public : // ModelImplBase abstract interface
        virtual TAKErr addVertex(const double posx, const double posy, const double posz,
                                 const float texu, const float texv,
                                 const float nx, const float ny, const float nz,
                                 const float r, const float g, const float b, const float a) NOTHROWS = 0;
        virtual TAKErr addVertex(const double posx, const double posy, const double posz,
                                 const float *texCoords,
                                 const float nx, const float ny, const float nz,
                                 const float r, const float g, const float b, const float a) NOTHROWS = 0;
        virtual TAKErr reserveVertices(const std::size_t count) NOTHROWS = 0;
    public : // ModelImplBase interface
        TAKErr reserveIndices(const std::size_t count) NOTHROWS;
        TAKErr addIndex(const std::size_t index) NOTHROWS;
        TAKErr addIndices(const uint32_t *indices, const std::size_t count) NOTHROWS;
        TAKErr addIndices(const uint16_t *indices, const std::size_t count) NOTHROWS;
        TAKErr addIndices(const uint8_t *indices, const std::size_t count) NOTHROWS;
    public : // Mesh abstract interface
        virtual TAKErr getVertices(const void **value, const std::size_t attr) const NOTHROWS = 0;
    public : // Mesh implementation
        std::size_t getNumMaterials() const NOTHROWS;
        TAKErr getMaterial(Material *value, const std::size_t index) const NOTHROWS;
        std::size_t getNumFaces() const NOTHROWS;
        bool isIndexed() const NOTHROWS;
        std::size_t getNumVertices() const NOTHROWS;
        TAKErr getPosition(Point2<double> *value, const std::size_t index) const NOTHROWS;
        TAKErr getTextureCoordinate(Point2<float> *value, const VertexAttribute texCoordIdx, const std::size_t index) const NOTHROWS;
        TAKErr getNormal(Point2<float> *value, const std::size_t index) const NOTHROWS;
        TAKErr getColor(unsigned int *value, const std::size_t index) const NOTHROWS;
        TAKErr getVertexAttributeType(DataType *value, const unsigned int attr) const NOTHROWS;
        TAKErr getIndexType(DataType *value) const NOTHROWS;
        TAKErr getIndex(size_t *value, const std::size_t index) const NOTHROWS;
        const void *getIndices() const NOTHROWS;
        std::size_t getIndexOffset() const NOTHROWS;
        std::size_t getNumIndices() const NOTHROWS;
        WindingOrder getFaceWindingOrder() const NOTHROWS;
        DrawMode getDrawMode() const NOTHROWS;
        const Envelope2 &getAABB() const NOTHROWS;
        const VertexDataLayout &getVertexDataLayout() const NOTHROWS;
    public:
        std::unique_ptr<MemBuffer2> indices;
        WindingOrder windingOrder;
        std::vector<Material> materials;
        std::unique_ptr<ElementAccess<std::size_t>> indexAccess;
    protected :
        std::unique_ptr<ElementAccess<double>> positionAccess;
        std::map<VertexAttribute, std::unique_ptr<ElementAccess<float>>> texCoordAccess;
        std::unique_ptr<ElementAccess<float>> normalAccess;
        std::unique_ptr<ElementAccess<float>> colorAccess;
        std::size_t vertexCount;
        Envelope2 aabb;
    private :
        bool indexed;
        DataType indexType;
        DrawMode drawMode;
        VertexDataLayout layout;
    };

    class InterleavedModelBase : public ModelImplBase
    {
    public :
        InterleavedModelBase(const DrawMode &mode, const VertexDataLayout &layout) NOTHROWS;
        InterleavedModelBase(const DrawMode &mode, const VertexDataLayout &layout, const DataType &indexType) NOTHROWS;
    public :
        virtual TAKErr addVertex(const double posx, const double posy, const double posz,
                                 const float texu, const float texv,
                                 const float nx, const float ny, const float nz,
                                 const float r, const float g, const float b, const float a) NOTHROWS = 0;
        virtual TAKErr addVertex(const double posx, const double posy, const double posz,
                                 const float *texCoords,
                                 const float nx, const float ny, const float nz,
                                 const float r, const float g, const float b, const float a) NOTHROWS = 0;
        TAKErr reserveVertices(const std::size_t count) NOTHROWS;
    public :
        TAKErr getVertices(const void **value, const std::size_t attr) const NOTHROWS;
    protected:
        std::unique_ptr<MemBuffer2> vertices;

        friend TAKErr TAK::Engine::Model::MeshBuilder_buildInterleavedMesh(MeshPtr &, const DrawMode, const WindingOrder, const VertexDataLayout &, const std::size_t, const Material *, const Envelope2 &, const std::size_t, const void *) NOTHROWS;
        friend TAKErr TAK::Engine::Model::MeshBuilder_buildInterleavedMesh(MeshPtr &, const DrawMode, const WindingOrder, const VertexDataLayout &, const std::size_t, const Material *, const Envelope2 &, const std::size_t, const void *, const DataType, const std::size_t, const void *) NOTHROWS;
        friend TAKErr TAK::Engine::Model::MeshBuilder_buildInterleavedMesh(MeshPtr &, const DrawMode, const WindingOrder, const VertexDataLayout &, const std::size_t, const Material *, const Envelope2 &, const std::size_t, VertexPtr &&) NOTHROWS;
        friend TAKErr TAK::Engine::Model::MeshBuilder_buildInterleavedMesh(MeshPtr &, const DrawMode, const WindingOrder, const VertexDataLayout &, const std::size_t, const Material *, const Envelope2 &, const std::size_t, VertexPtr &&, const DataType, const std::size_t, VertexPtr &&) NOTHROWS;
    };

    class DefaultInterleavedModel : public InterleavedModelBase
    {
    public :
        DefaultInterleavedModel(const DrawMode &mode, const VertexDataLayout &layout) NOTHROWS;
        DefaultInterleavedModel(const DrawMode &mode, const VertexDataLayout &layout, const DataType &indexType) NOTHROWS;
    public :
        TAKErr addVertex(const double posx, const double posy, const double posz,
                         const float texu, const float texv,
                         const float nx, const float ny, const float nz,
                         const float r, const float g, const float b, const float a) NOTHROWS;
        TAKErr addVertex(const double posx, const double posy, const double posz,
                         const float *texCoords,
                         const float nx, const float ny, const float nz,
                         const float r, const float g, const float b, const float a) NOTHROWS;
        TAKErr reserveVertices(const std::size_t count) NOTHROWS;
    };

    class GenericInterleavedModel : public InterleavedModelBase
    {
    public :
        GenericInterleavedModel(const DrawMode &mode, const VertexDataLayout &layout) NOTHROWS;
        GenericInterleavedModel(const DrawMode &mode, const VertexDataLayout &layout, const DataType &indexType) NOTHROWS;
    public :
        TAKErr addVertex(const double posx, const double posy, const double posz,
                         const float texu, const float texv,
                         const float nx, const float ny, const float nz,
                         const float r, const float g, const float b, const float a) NOTHROWS;
        TAKErr addVertex(const double posx, const double posy, const double posz,
                         const float *texCoords,
                         const float nx, const float ny, const float nz,
                         const float r, const float g, const float b, const float a) NOTHROWS;
    };

    class NonInterleavedModel : public ModelImplBase
    {
    public :
        NonInterleavedModel(const DrawMode &mode, const VertexDataLayout &layout) NOTHROWS;
        NonInterleavedModel(const DrawMode &mode, const VertexDataLayout &layout, const DataType &indexType) NOTHROWS;
    public :
        TAKErr addVertex(const double posx, const double posy, const double posz,
                         const float texu, const float texv,
                         const float nx, const float ny, const float nz,
                         const float r, const float g, const float b, const float a) NOTHROWS;
        TAKErr addVertex(const double posx, const double posy, const double posz,
                         const float *texCoords,
                         const float nx, const float ny, const float nz,
                         const float r, const float g, const float b, const float a) NOTHROWS;
        TAKErr reserveVertices(const std::size_t count) NOTHROWS;
    public :
        TAKErr getVertices(const void **value, const std::size_t attr) const NOTHROWS;
    private :
        std::unique_ptr<MemBuffer2> positions;
        std::map<VertexAttribute, std::unique_ptr<MemBuffer2>> texCoords;
        std::unique_ptr<MemBuffer2> normals;
        std::unique_ptr<MemBuffer2> colors;

        friend TAKErr TAK::Engine::Model::MeshBuilder_buildNonInterleavedMesh(MeshPtr &, const DrawMode, const WindingOrder, const VertexDataLayout &, const std::size_t, const Material *, const Envelope2 &, const std::size_t, const void *, const void **, const void *, const void *) NOTHROWS;
        friend TAKErr TAK::Engine::Model::MeshBuilder_buildNonInterleavedMesh(MeshPtr &, const DrawMode, const WindingOrder, const VertexDataLayout &, const std::size_t, const Material *, const Envelope2 &, const std::size_t, const void *, const void **, const void *, const void *, const DataType, const std::size_t, const void *) NOTHROWS;
        friend TAKErr TAK::Engine::Model::MeshBuilder_buildNonInterleavedMesh(MeshPtr &, const DrawMode, const WindingOrder, const VertexDataLayout &, const std::size_t, const Material *, const Envelope2 &, const std::size_t, VertexPtr &&, VertexPtr&&, VertexPtr &&, VertexPtr&&, VertexPtr &&, VertexPtr&&, VertexPtr &&, VertexPtr&&, VertexPtr &&, VertexPtr&&, VertexPtr&&) NOTHROWS;
        friend TAKErr TAK::Engine::Model::MeshBuilder_buildNonInterleavedMesh(MeshPtr &, const DrawMode, const WindingOrder, const VertexDataLayout &, const std::size_t, const Material *, const Envelope2 &, const std::size_t, VertexPtr &&, VertexPtr&&, VertexPtr &&, VertexPtr&&, VertexPtr &&, VertexPtr&&, VertexPtr &&, VertexPtr&&, VertexPtr &&, VertexPtr&&, VertexPtr &&, const DataType, const std::size_t, VertexPtr &&) NOTHROWS;
    };

    typedef std::unique_ptr<void, void(*)(const void *)> VoidPtr;

    TAKErr checkInitParams(const DrawMode &mode, const VertexDataLayout &layout, const DataType &indexType) NOTHROWS;
    TAKErr resize(std::unique_ptr<MemBuffer2> &buf, const std::size_t newSize) NOTHROWS;
    template<class T>
    TAKErr createElementAccess(std::unique_ptr<ElementAccess<T>> &value, const DataType &type, const bool normalized) NOTHROWS;
    template<class T>
    float normalize(const T value) NOTHROWS;
    template<class T>
    T unnormalize(const float value) NOTHROWS;
    bool isDefaultInterleave(const VertexDataLayout &layout) NOTHROWS;
    VertexDataLayout defaultInterleavedLayout(const unsigned int attrs) NOTHROWS;

    TAKErr allocateV(VoidPtr &value, const std::size_t size) NOTHROWS
    {
        value = VoidPtr(te_alloc_v(size), te_free_v);
        if (!value.get())
            return TE_OutOfMemory;
        return TE_Ok;
    }
}

MeshBuilder::MeshBuilder(const DrawMode &mode, const unsigned int attrs) NOTHROWS :
    impl(MeshPtr(new DefaultInterleavedModel(mode, defaultInterleavedLayout(attrs)), Memory_deleter_const<Mesh, DefaultInterleavedModel>)),
    initErr(checkInitParams(mode, impl->getVertexDataLayout(), TEDT_UInt16))
{}

MeshBuilder::MeshBuilder(const DrawMode &mode, const unsigned int attrs, const DataType &indexType) NOTHROWS :
    impl(MeshPtr(new DefaultInterleavedModel(mode, defaultInterleavedLayout(attrs), indexType), Memory_deleter_const<Mesh, DefaultInterleavedModel>)),
    initErr(checkInitParams(mode, impl->getVertexDataLayout(), indexType))
{}

MeshBuilder::MeshBuilder(const DrawMode &mode, const VertexDataLayout &layout) NOTHROWS :
    impl(NULL, NULL),
    initErr(checkInitParams(mode, layout, TEDT_UInt16))
{
    if(initErr != TE_Ok)
        return;
    if(isDefaultInterleave(layout)) {
        impl = MeshPtr(new DefaultInterleavedModel(mode, layout), Memory_deleter_const<Mesh, DefaultInterleavedModel>);
    } else if(layout.interleaved) {
        impl = MeshPtr(new GenericInterleavedModel(mode, layout), Memory_deleter_const<Mesh, GenericInterleavedModel>);
    } else {
        impl = MeshPtr(new NonInterleavedModel(mode, layout), Memory_deleter_const<Mesh, NonInterleavedModel>);
    }
}

MeshBuilder::MeshBuilder(const DrawMode &mode, const VertexDataLayout &layout, const DataType &indexType) NOTHROWS :
   impl(NULL, NULL),
   initErr(checkInitParams(mode, layout, indexType))
{
    if(initErr != TE_Ok)
        return;
    if(isDefaultInterleave(layout)) {
        impl = MeshPtr(new DefaultInterleavedModel(mode, layout, indexType), Memory_deleter_const<Mesh, DefaultInterleavedModel>);
    } else if(layout.interleaved) {
        impl = MeshPtr(new GenericInterleavedModel(mode, layout, indexType), Memory_deleter_const<Mesh, GenericInterleavedModel>);
    } else {
        impl = MeshPtr(new NonInterleavedModel(mode, layout, indexType), Memory_deleter_const<Mesh, NonInterleavedModel>);
    }
}

MeshBuilder::~MeshBuilder() NOTHROWS
{}

TAKErr MeshBuilder::reserveVertices(const std::size_t count) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    return mimpl.reserveVertices(count);
}

TAKErr MeshBuilder::reserveIndices(const std::size_t count) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    return mimpl.reserveIndices(count);
}

TAKErr MeshBuilder::setWindingOrder(const WindingOrder &windingOrder) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);

    if(!impl.get())
        return TE_IllegalState;
    switch(windingOrder) {
        case TEWO_Clockwise :
        case TEWO_CounterClockwise :
        case TEWO_Undefined :
            break;
        default :
            return TE_InvalidArg;
    }
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    mimpl.windingOrder = windingOrder;
    return TE_Ok;
}
TAKErr MeshBuilder::addMaterial(const Material &material) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);

    TAKErr code(TE_Ok);
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    mimpl.materials.push_back(material);
    return TE_Ok;
}
TAKErr MeshBuilder::addVertex(double posx, double posy, double posz,
                       float texu, float texv,
                       float nx, float ny, float nz,
                       float r, float g, float b, float a) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    return mimpl.addVertex(posx, posy, posz, texu, texv, nx, ny, nz, r, g, b, a);
}
TAKErr MeshBuilder::addVertex(double posx, double posy, double posz,
                       const float *texCoords,
                       float nx, float ny, float nz,
                       float r, float g, float b, float a) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    return mimpl.addVertex(posx, posy, posz, texCoords, nx, ny, nz, r, g, b, a);
}

TAKErr MeshBuilder::addIndex(const std::size_t index) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    return mimpl.addIndex(index);
}
TAKErr MeshBuilder::addIndices(const uint32_t *indices, const std::size_t count) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    return mimpl.addIndices(indices, count);
}
TAKErr MeshBuilder::addIndices(const uint16_t *indices, const std::size_t count) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    return mimpl.addIndices(indices, count);
}
TAKErr MeshBuilder::addIndices(const uint8_t *indices, const std::size_t count) NOTHROWS
{
    TE_CHECKRETURN_CODE(initErr);
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    return mimpl.addIndices(indices, count);
}

TAKErr MeshBuilder::build(MeshPtr &value) NOTHROWS
{
    if(!impl.get())
        return TE_IllegalState;
    ModelImplBase &mimpl = static_cast<ModelImplBase &>(*impl);
    if(mimpl.isIndexed())
        mimpl.indices->flip();
    value = std::move(impl);
    return TE_Ok;
}

TAKErr TAK::Engine::Model::MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Envelope2 &aabb, const std::size_t numVertices, const void *vertices) NOTHROWS
{
    TAKErr code(TE_Ok);
    value = MeshPtr(new GenericInterleavedModel(mode, layout), Memory_deleter_const<Mesh, GenericInterleavedModel>);
    GenericInterleavedModel &impl = static_cast<GenericInterleavedModel &>(*value);

    switch(order) {
        case TEWO_Clockwise :
        case TEWO_CounterClockwise :
        case TEWO_Undefined :
            break;
        default :
            return TE_InvalidArg;
    }
    impl.windingOrder = order;

    code = impl.reserveVertices(numVertices);
    TE_CHECKRETURN_CODE(code);
    code = impl.vertices->put(static_cast<const uint8_t *>(vertices), impl.vertices->size());
    TE_CHECKRETURN_CODE(code);
    impl.vertexCount = numVertices;

    for (std::size_t i = 0; i < numMaterials; i++)
        impl.materials.push_back(materials[i]);

    impl.aabb = aabb;

    return code;
}
TAKErr TAK::Engine::Model::MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Envelope2 &aabb, const std::size_t numVertices, const void *vertices, const Port::DataType indexType, const std::size_t numIndices, const void *indices) NOTHROWS
{
    TAKErr code(TE_Ok);
    value = MeshPtr(new GenericInterleavedModel(mode, layout, indexType), Memory_deleter_const<Mesh, GenericInterleavedModel>);
    GenericInterleavedModel &impl = static_cast<GenericInterleavedModel &>(*value);

    switch(order) {
        case TEWO_Clockwise :
        case TEWO_CounterClockwise :
        case TEWO_Undefined :
            break;
        default :
            return TE_InvalidArg;
    }
    impl.windingOrder = order;

    code = impl.reserveVertices(numVertices);
    TE_CHECKRETURN_CODE(code);
    code = impl.vertices->put(static_cast<const uint8_t *>(vertices), impl.vertices->size());
    TE_CHECKRETURN_CODE(code);
    impl.vertexCount = numVertices;

    code = impl.reserveIndices(numIndices);
    TE_CHECKRETURN_CODE(code);
    code = impl.indices->put(static_cast<const uint8_t *>(indices), impl.indices->size());
    TE_CHECKRETURN_CODE(code);

    for (std::size_t i = 0; i < numMaterials; i++)
        impl.materials.push_back(materials[i]);

    impl.aabb = aabb;

    return code;
}
TAKErr TAK::Engine::Model::MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Envelope2 &aabb, const std::size_t numVertices, const void *positions, const void **texCoords, const void *normals, const void *colors) NOTHROWS
{
    TAKErr code(TE_Ok);
    value = MeshPtr(new NonInterleavedModel(mode, layout), Memory_deleter_const<Mesh, NonInterleavedModel>);
    NonInterleavedModel &impl = static_cast<NonInterleavedModel &>(*value);

    switch(order) {
        case TEWO_Clockwise :
        case TEWO_CounterClockwise :
        case TEWO_Undefined :
            break;
        default :
            return TE_InvalidArg;
    }
    impl.windingOrder = order;

    code = impl.reserveVertices(numVertices);
    TE_CHECKRETURN_CODE(code);

#define COPY_VERTICES(teva, srcVa, dstVa) \
    if(layout.attributes&teva) { \
        std::size_t size; \
        code = VertexDataLayout_requiredDataSize(&size, layout, teva, numVertices); \
        TE_CHECKRETURN_CODE(code); \
        code = impl.dstVa->put(static_cast<const uint8_t *>(srcVa), size); \
        TE_CHECKRETURN_CODE(code); \
    }

    COPY_VERTICES(TEVA_Position, positions, positions);
    COPY_VERTICES(TEVA_TexCoord0, *texCoords++, texCoords[TEVA_TexCoord0]);
    COPY_VERTICES(TEVA_TexCoord1, *texCoords++, texCoords[TEVA_TexCoord1]);
    COPY_VERTICES(TEVA_TexCoord2, *texCoords++, texCoords[TEVA_TexCoord2]);
    COPY_VERTICES(TEVA_TexCoord3, *texCoords++, texCoords[TEVA_TexCoord3]);
    COPY_VERTICES(TEVA_TexCoord4, *texCoords++, texCoords[TEVA_TexCoord4]);
    COPY_VERTICES(TEVA_TexCoord5, *texCoords++, texCoords[TEVA_TexCoord5]);
    COPY_VERTICES(TEVA_TexCoord6, *texCoords++, texCoords[TEVA_TexCoord6]);
    COPY_VERTICES(TEVA_TexCoord7, *texCoords++, texCoords[TEVA_TexCoord7]);
    COPY_VERTICES(TEVA_Position, normals, normals);
    COPY_VERTICES(TEVA_Position, colors, colors);
#undef COPY_VERTICES

    impl.vertexCount = numVertices;

    for (std::size_t i = 0; i < numMaterials; i++)
        impl.materials.push_back(materials[i]);

    impl.aabb = aabb;

    return code;
}
TAKErr TAK::Engine::Model::MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Envelope2 &aabb, const std::size_t numVertices, const void *positions, const void **texCoords, const void *normals, const void *colors, const Port::DataType indexType, const std::size_t numIndices, const void *indices) NOTHROWS
{
    TAKErr code(TE_Ok);
    value = MeshPtr(new NonInterleavedModel(mode, layout, indexType), Memory_deleter_const<Mesh, NonInterleavedModel>);
    NonInterleavedModel &impl = static_cast<NonInterleavedModel &>(*value);

    switch(order) {
        case TEWO_Clockwise :
        case TEWO_CounterClockwise :
        case TEWO_Undefined :
            break;
        default :
            return TE_InvalidArg;
    }
    impl.windingOrder = order;

    code = impl.reserveVertices(numVertices);
    TE_CHECKRETURN_CODE(code);

#define COPY_VERTICES(teva, srcVa, dstVa) \
    if(layout.attributes&teva) { \
        std::size_t size; \
        code = VertexDataLayout_requiredDataSize(&size, layout, teva, numVertices); \
        TE_CHECKRETURN_CODE(code); \
        code = impl.dstVa->put(static_cast<const uint8_t *>(srcVa), size); \
        TE_CHECKRETURN_CODE(code); \
    }

    COPY_VERTICES(TEVA_Position, positions, positions);
    COPY_VERTICES(TEVA_TexCoord0, *texCoords++, texCoords[TEVA_TexCoord0]);
    COPY_VERTICES(TEVA_TexCoord1, *texCoords++, texCoords[TEVA_TexCoord1]);
    COPY_VERTICES(TEVA_TexCoord2, *texCoords++, texCoords[TEVA_TexCoord2]);
    COPY_VERTICES(TEVA_TexCoord3, *texCoords++, texCoords[TEVA_TexCoord3]);
    COPY_VERTICES(TEVA_TexCoord4, *texCoords++, texCoords[TEVA_TexCoord4]);
    COPY_VERTICES(TEVA_TexCoord5, *texCoords++, texCoords[TEVA_TexCoord5]);
    COPY_VERTICES(TEVA_TexCoord6, *texCoords++, texCoords[TEVA_TexCoord6]);
    COPY_VERTICES(TEVA_TexCoord7, *texCoords++, texCoords[TEVA_TexCoord7]);
    COPY_VERTICES(TEVA_Position, normals, normals);
    COPY_VERTICES(TEVA_Position, colors, colors);
#undef COPY_VERTICES

    impl.vertexCount = numVertices;

    code = impl.reserveIndices(numIndices);
    TE_CHECKRETURN_CODE(code);

    code = impl.indices->put(static_cast<const uint8_t *>(indices), impl.indices->size());
    TE_CHECKRETURN_CODE(code);

    for (std::size_t i = 0; i < numMaterials; i++)
        impl.materials.push_back(materials[i]);

    impl.aabb = aabb;

    return code;
}

TAKErr TAK::Engine::Model::MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, VertexArrayPtr &&vertices) NOTHROWS
{
    TAKErr code(TE_Ok);
    value = MeshPtr(new GenericInterleavedModel(mode, layout), Memory_deleter_const<Mesh, GenericInterleavedModel>);
    GenericInterleavedModel &impl = static_cast<GenericInterleavedModel &>(*value);

    switch(order) {
        case TEWO_Clockwise :
        case TEWO_CounterClockwise :
        case TEWO_Undefined :
            break;
        default :
            return TE_InvalidArg;
    }
    impl.windingOrder = order;

    std::size_t size;
    code = VertexDataLayout_requiredInterleavedDataSize(&size, layout, numVertices);
    TE_CHECKRETURN_CODE(code);

    impl.vertices.reset(new MemBuffer2(std::move(vertices), size));
    impl.vertexCount = numVertices;

    for (std::size_t i = 0; i < numMaterials; i++)
        impl.materials.push_back(materials[i]);

    impl.aabb = aabb;

    return code;
}
TAKErr TAK::Engine::Model::MeshBuilder_buildInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, VertexArrayPtr &&vertices, const Port::DataType indexType, const std::size_t numIndices, VertexArrayPtr &&indices) NOTHROWS
{
    TAKErr code(TE_Ok);
    value = MeshPtr(new GenericInterleavedModel(mode, layout, indexType), Memory_deleter_const<Mesh, GenericInterleavedModel>);
    GenericInterleavedModel &impl = static_cast<GenericInterleavedModel &>(*value);

    switch(order) {
        case TEWO_Clockwise :
        case TEWO_CounterClockwise :
        case TEWO_Undefined :
            break;
        default :
            return TE_InvalidArg;
    }
    impl.windingOrder = order;

    std::size_t size;
    code = VertexDataLayout_requiredInterleavedDataSize(&size, layout, numVertices);
    TE_CHECKRETURN_CODE(code);

    impl.vertices.reset(new MemBuffer2(std::move(vertices), size));
    impl.vertexCount = numVertices;

    impl.indices.reset(new MemBuffer2(std::move(indices), impl.indexAccess->transferSize(numIndices)));

    for (std::size_t i = 0; i < numMaterials; i++)
        impl.materials.push_back(materials[i]);

    impl.aabb = aabb;

    return code;
}
TAKErr TAK::Engine::Model::MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, VertexArrayPtr &&positions, VertexArrayPtr &&texCoords0, VertexArrayPtr &&texCoords1, VertexArrayPtr &&texCoords2, VertexArrayPtr &&texCoords3, VertexArrayPtr &&texCoords4, VertexArrayPtr &&texCoords5, VertexArrayPtr &&texCoords6, VertexArrayPtr &&texCoords7, VertexArrayPtr &&normals, VertexArrayPtr &&colors) NOTHROWS
{
    TAKErr code(TE_Ok);
    value = MeshPtr(new NonInterleavedModel(mode, layout), Memory_deleter_const<Mesh, NonInterleavedModel>);
    NonInterleavedModel &impl = static_cast<NonInterleavedModel &>(*value);
    TE_CHECKRETURN_CODE(code);

    switch(order) {
        case TEWO_Clockwise :
        case TEWO_CounterClockwise :
        case TEWO_Undefined :
            break;
        default :
            return TE_InvalidArg;
    }
    impl.windingOrder = order;

#define COPY_VERTICES(teva, srcVa, dstVa) \
    if(layout.attributes&teva) { \
        std::size_t size; \
        code = VertexDataLayout_requiredDataSize(&size, layout, teva, numVertices); \
        TE_CHECKRETURN_CODE(code); \
        impl.dstVa.reset(new MemBuffer2(std::move(srcVa), size)); \
    }

    COPY_VERTICES(TEVA_Position, positions, positions);
    COPY_VERTICES(TEVA_TexCoord0, texCoords0, texCoords[TEVA_TexCoord0]);
    COPY_VERTICES(TEVA_TexCoord1, texCoords1, texCoords[TEVA_TexCoord1]);
    COPY_VERTICES(TEVA_TexCoord2, texCoords2, texCoords[TEVA_TexCoord2]);
    COPY_VERTICES(TEVA_TexCoord3, texCoords3, texCoords[TEVA_TexCoord3]);
    COPY_VERTICES(TEVA_TexCoord4, texCoords4, texCoords[TEVA_TexCoord4]);
    COPY_VERTICES(TEVA_TexCoord5, texCoords5, texCoords[TEVA_TexCoord5]);
    COPY_VERTICES(TEVA_TexCoord6, texCoords6, texCoords[TEVA_TexCoord6]);
    COPY_VERTICES(TEVA_TexCoord7, texCoords7, texCoords[TEVA_TexCoord7]);
    COPY_VERTICES(TEVA_Position, normals, normals);
    COPY_VERTICES(TEVA_Position, colors, colors);
#undef COPY_VERTICES

    impl.vertexCount = numVertices;

    for (std::size_t i = 0; i < numMaterials; i++)
        impl.materials.push_back(materials[i]);

    impl.aabb = aabb;

    return code;
}
TAKErr TAK::Engine::Model::MeshBuilder_buildNonInterleavedMesh(MeshPtr &value, const DrawMode mode, const WindingOrder order, const VertexDataLayout &layout, const std::size_t numMaterials, const Material *materials, const Feature::Envelope2 &aabb, const std::size_t numVertices, VertexArrayPtr &&positions, VertexArrayPtr &&texCoords0, VertexArrayPtr &&texCoords1, VertexArrayPtr &&texCoords2, VertexArrayPtr &&texCoords3, VertexArrayPtr &&texCoords4, VertexArrayPtr &&texCoords5, VertexArrayPtr &&texCoords6, VertexArrayPtr &&texCoords7, VertexArrayPtr &&normals, VertexArrayPtr &&colors, const Port::DataType indexType, const std::size_t numIndices, VertexArrayPtr &&indices) NOTHROWS
{
    TAKErr code(TE_Ok);
    value = MeshPtr(new NonInterleavedModel(mode, layout, indexType), Memory_deleter_const<Mesh, NonInterleavedModel>);
    NonInterleavedModel &impl = static_cast<NonInterleavedModel &>(*value);
    TE_CHECKRETURN_CODE(code);

    switch(order) {
        case TEWO_Clockwise :
        case TEWO_CounterClockwise :
        case TEWO_Undefined :
            break;
        default :
            return TE_InvalidArg;
    }
    impl.windingOrder = order;
    
#define COPY_VERTICES(teva, srcVa, dstVa) \
    if(layout.attributes&teva) { \
        std::size_t size; \
        code = VertexDataLayout_requiredDataSize(&size, layout, teva, numVertices); \
        TE_CHECKRETURN_CODE(code); \
        impl.dstVa.reset(new MemBuffer2(std::move(srcVa), size)); \
    }

    COPY_VERTICES(TEVA_Position, positions, positions);
    COPY_VERTICES(TEVA_TexCoord0, texCoords0, texCoords[TEVA_TexCoord0]);
    COPY_VERTICES(TEVA_TexCoord1, texCoords1, texCoords[TEVA_TexCoord1]);
    COPY_VERTICES(TEVA_TexCoord2, texCoords2, texCoords[TEVA_TexCoord2]);
    COPY_VERTICES(TEVA_TexCoord3, texCoords3, texCoords[TEVA_TexCoord3]);
    COPY_VERTICES(TEVA_TexCoord4, texCoords4, texCoords[TEVA_TexCoord4]);
    COPY_VERTICES(TEVA_TexCoord5, texCoords5, texCoords[TEVA_TexCoord5]);
    COPY_VERTICES(TEVA_TexCoord6, texCoords6, texCoords[TEVA_TexCoord6]);
    COPY_VERTICES(TEVA_TexCoord7, texCoords7, texCoords[TEVA_TexCoord7]);
    COPY_VERTICES(TEVA_Position, normals, normals);
    COPY_VERTICES(TEVA_Position, colors, colors);
#undef COPY_VERTICES

    impl.vertexCount = numVertices;

    impl.indices.reset(new MemBuffer2(std::move(indices), impl.indexAccess->transferSize(numIndices)));

    for (std::size_t i = 0; i < numMaterials; i++)
        impl.materials.push_back(materials[i]);

    impl.aabb = aabb;

    return code;
}

namespace
{
    template<class T>
    ElementAccess<T>::ElementAccess(const std::size_t elementSize_) NOTHROWS :
        elementSize(elementSize_)
    {}
    template<class T>
    const std::size_t ElementAccess<T>::transferSize(const std::size_t count) NOTHROWS
    {
        return elementSize*count;
    }

#define ELEMENT_ACCESS_DEFN(t) \
    template<class T> \
    t##_ElementAccess<T>::t##_ElementAccess() NOTHROWS : \
        ElementAccess<T>(sizeof(t)) \
    {} \
    template<class T> \
    inline TAKErr t##_ElementAccess<T>::get(T *a, MemBuffer2 &buf) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        t v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *a = v; \
        return code; \
    } \
    template<class T> \
    inline TAKErr t##_ElementAccess<T>::get(T *a, T *b, MemBuffer2 &buf) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        t v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *a = v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *b = v; \
        return code; \
    } \
    template<class T> \
    inline TAKErr t##_ElementAccess<T>::get(T *a, T *b, T *c, MemBuffer2 &buf) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        t v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *a = v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *b = v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *c = v; \
        return code; \
    } \
    template<class T> \
    inline TAKErr t##_ElementAccess<T>::get(T *a, T *b, T *c, T *d, MemBuffer2 &buf) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        t v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *a = v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *b = v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *c = v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *d = v; \
        return code; \
    } \
    template<class T> \
    inline TAKErr t##_ElementAccess<T>::put(MemBuffer2 &buf, const T a) NOTHROWS \
    { \
        return buf.put(static_cast<t>(a)); \
    } \
    template<class T> \
    inline TAKErr t##_ElementAccess<T>::put(MemBuffer2 &buf, const T a, const T b) NOTHROWS \
    { \
        t arr[2]; \
        arr[0] = a; \
        arr[1] = b; \
        return buf.put(arr, 2); \
    } \
    template<class T> \
    inline TAKErr t##_ElementAccess<T>::put(MemBuffer2 &buf, const T a, const T b, const T c) NOTHROWS \
    { \
        t arr[3]; \
        arr[0] = a; \
        arr[1] = b; \
        arr[2] = c; \
        return buf.put(arr, 3); \
    } \
    template<class T> \
    inline TAKErr t##_ElementAccess<T>::put(MemBuffer2 &buf, const T a, const T b, const T c, const T d) NOTHROWS \
    { \
        t arr[4]; \
        arr[0] = a; \
        arr[1] = b; \
        arr[2] = c; \
        arr[3] = d; \
        return buf.put(arr, 4); \
    }

    ELEMENT_ACCESS_DEFN(uint8_t);
    ELEMENT_ACCESS_DEFN(int8_t);
    ELEMENT_ACCESS_DEFN(uint16_t);
    ELEMENT_ACCESS_DEFN(int16_t);
    ELEMENT_ACCESS_DEFN(uint32_t);
    ELEMENT_ACCESS_DEFN(int32_t);
    ELEMENT_ACCESS_DEFN(float);
    ELEMENT_ACCESS_DEFN(double);

#undef ELEMENT_ACCESS_DEFN

#define NORMALIZED_ELEMENT_ACCESS_DEFN(t) \
    template<class T> \
    t##_NormalizedElementAccess<T>::t##_NormalizedElementAccess() NOTHROWS : \
        ElementAccess<T>(sizeof(t)) \
    {} \
    template<class T> \
    inline TAKErr t##_NormalizedElementAccess<T>::get(T *a, MemBuffer2 &buf) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        t v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *a = normalize<t>(v); \
        return code; \
    } \
    template<class T> \
    inline TAKErr t##_NormalizedElementAccess<T>::get(T *a, T *b, MemBuffer2 &buf) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        t v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *a = normalize<t>(v); \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *b = normalize<t>(v); \
        return code; \
    } \
    template<class T> \
    inline TAKErr t##_NormalizedElementAccess<T>::get(T *a, T *b, T *c, MemBuffer2 &buf) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        t v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *a = normalize<t>(v); \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *b = normalize<t>(v); \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *c = normalize<t>(v); \
        return code; \
    } \
    template<class T> \
    inline TAKErr t##_NormalizedElementAccess<T>::get(T *a, T *b, T *c, T *d, MemBuffer2 &buf) NOTHROWS \
    { \
        TAKErr code(TE_Ok); \
        t v; \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *a = normalize<t>(v); \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *b = normalize<t>(v); \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *c = normalize<t>(v); \
        code = buf.get(&v); \
        TE_CHECKRETURN_CODE(code); \
        *d = normalize<t>(v); \
        return code; \
    } \
    template<class T> \
    inline TAKErr t##_NormalizedElementAccess<T>::put(MemBuffer2 &buf, const T a) NOTHROWS \
    { \
        return buf.put(unnormalize<t>(a)); \
    } \
    template<class T> \
    inline TAKErr t##_NormalizedElementAccess<T>::put(MemBuffer2 &buf, const T a, const T b) NOTHROWS \
    { \
        t arr[2]; \
        arr[0] = unnormalize<t>(a); \
        arr[1] = unnormalize<t>(b); \
        return buf.put(arr, 2); \
    } \
    template<class T> \
    inline TAKErr t##_NormalizedElementAccess<T>::put(MemBuffer2 &buf, const T a, const T b, const T c) NOTHROWS \
    { \
        t arr[3]; \
        arr[0] = unnormalize<t>(a); \
        arr[1] = unnormalize<t>(b); \
        arr[2] = unnormalize<t>(c); \
        return buf.put(arr, 3); \
    } \
    template<class T> \
    inline TAKErr t##_NormalizedElementAccess<T>::put(MemBuffer2 &buf, const T a, const T b, const T c, const T d) NOTHROWS \
    { \
        t arr[4]; \
        arr[0] = unnormalize<t>(a); \
        arr[1] = unnormalize<t>(b); \
        arr[2] = unnormalize<t>(c); \
        arr[3] = unnormalize<t>(d); \
        return buf.put(arr, 4); \
    }

    NORMALIZED_ELEMENT_ACCESS_DEFN(uint8_t);
    NORMALIZED_ELEMENT_ACCESS_DEFN(int8_t);
    NORMALIZED_ELEMENT_ACCESS_DEFN(uint16_t);
    NORMALIZED_ELEMENT_ACCESS_DEFN(int16_t);
    NORMALIZED_ELEMENT_ACCESS_DEFN(uint32_t);
    NORMALIZED_ELEMENT_ACCESS_DEFN(int32_t);
    NORMALIZED_ELEMENT_ACCESS_DEFN(float);
    NORMALIZED_ELEMENT_ACCESS_DEFN(double);

#undef NORMALIZED_ELEMENT_ACCESS_DEFN

    //************************************************************************//
    // ModelImplBase

    ModelImplBase::ModelImplBase(const DrawMode &mode_, const VertexDataLayout &layout_) NOTHROWS :
        drawMode(mode_),
        layout(layout_),
        indexed(false),
        indexType(TEDT_UInt16),
        vertexCount(0u),
        windingOrder(TEWO_Undefined)
    {
        initAttributeAccess();
    }
    ModelImplBase::ModelImplBase(const DrawMode &mode_, const VertexDataLayout &layout_, const DataType &indexType_) NOTHROWS :
        drawMode(mode_),
        layout(layout_),
        indexed(true),
        indexType(indexType_),
        vertexCount(0u),
        windingOrder(TEWO_Undefined)
    {
        createElementAccess<std::size_t>(indexAccess, indexType, false);

        initAttributeAccess();
    }

    void ModelImplBase::initAttributeAccess() NOTHROWS
    {
        if(layout.attributes&TEVA_Position)
            createElementAccess(positionAccess, layout.position.type, false);
        if(layout.attributes&TEVA_TexCoord0) {
            std::unique_ptr<ElementAccess<float>> access;
            createElementAccess(access, layout.texCoord0.type, false);
            texCoordAccess[TEVA_TexCoord0] = std::move(access);
        }
        if(layout.attributes&TEVA_TexCoord1) {
            std::unique_ptr<ElementAccess<float>> access;
            createElementAccess(access, layout.texCoord1.type, false);
            texCoordAccess[TEVA_TexCoord1] = std::move(access);
        }
        if(layout.attributes&TEVA_TexCoord2) {
            std::unique_ptr<ElementAccess<float>> access;
            createElementAccess(access, layout.texCoord2.type, false);
            texCoordAccess[TEVA_TexCoord2] = std::move(access);
        }
        if(layout.attributes&TEVA_TexCoord3) {
            std::unique_ptr<ElementAccess<float>> access;
            createElementAccess(access, layout.texCoord3.type, false);
            texCoordAccess[TEVA_TexCoord3] = std::move(access);
        }
        if(layout.attributes&TEVA_TexCoord4) {
            std::unique_ptr<ElementAccess<float>> access;
            createElementAccess(access, layout.texCoord4.type, false);
            texCoordAccess[TEVA_TexCoord4] = std::move(access);
        }
        if(layout.attributes&TEVA_TexCoord5) {
            std::unique_ptr<ElementAccess<float>> access;
            createElementAccess(access, layout.texCoord5.type, false);
            texCoordAccess[TEVA_TexCoord5] = std::move(access);
        }
        if(layout.attributes&TEVA_TexCoord6) {
            std::unique_ptr<ElementAccess<float>> access;
            createElementAccess(access, layout.texCoord6.type, false);
            texCoordAccess[TEVA_TexCoord6] = std::move(access);
        }
        if(layout.attributes&TEVA_TexCoord7) {
            std::unique_ptr<ElementAccess<float>> access;
            createElementAccess(access, layout.texCoord7.type, false);
            texCoordAccess[TEVA_TexCoord7] = std::move(access);
        }
        if(layout.attributes&TEVA_Normal)
            createElementAccess(normalAccess, layout.normal.type, false);
        if(layout.attributes&TEVA_Color) {
            // do some special handling for normalization
            switch(layout.color.type) {
                case TEDT_UInt8 :
                case TEDT_UInt16 :
                case TEDT_UInt32 :
                    createElementAccess(colorAccess, layout.color.type, true);
                    break;
                case TEDT_Float32 :
                case TEDT_Float64 :
                    createElementAccess(colorAccess, layout.color.type, false);
                    break;
                default :
                    createElementAccess(colorAccess, layout.color.type, true);
                    break;
            }
        }
    }
    TAKErr ModelImplBase::reserveIndices(const std::size_t count) NOTHROWS
    {
        if(!indexed)
            return TE_IllegalState;
        if(indices.get())
            return TE_IllegalState;
        if(!count)
            return TE_Ok;
        if(!indexAccess.get())
            return TE_IllegalState;
        return resize(indices, indexAccess->transferSize(count));
    }
    TAKErr ModelImplBase::addIndex(const std::size_t index) NOTHROWS
    {
        if(!indexed)
            return TE_IllegalState;
        if(!indices.get() || indices->remaining() < indexAccess->transferSize(1u)) {
            std::size_t newSize = indexAccess->transferSize(1024u);
            if(indices.get())
                newSize += indices->size();
            resize(indices, newSize);
        }
        return indexAccess->put(*indices, index);
    }
    TAKErr ModelImplBase::addIndices(const uint32_t *indices, const std::size_t count) NOTHROWS
    {
        if(!indexed) return TE_IllegalState;

        TAKErr code(TE_Ok);
        if(!this->indices.get() || this->indices->remaining() < indexAccess->transferSize(count)) {
            std::size_t newSize = indexAccess->transferSize(count);
            if(this->indices.get())
                newSize += this->indices->size();
            code = resize(this->indices, newSize);
            TE_CHECKRETURN_CODE(code);
        }
        for(std::size_t i = 0u; i < count; i++) {
            code = indexAccess->put(*this->indices, indices[i]);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return TE_Ok;
    }
    TAKErr ModelImplBase::addIndices(const uint16_t *indices, const std::size_t count) NOTHROWS
    {
        if(!indexed) return TE_IllegalState;

        TAKErr code(TE_Ok);
        if(!this->indices.get() || this->indices->remaining() < indexAccess->transferSize(count)) {
            std::size_t newSize = indexAccess->transferSize(count);
            if(this->indices.get())
                newSize += this->indices->size();
            code = resize(this->indices, newSize);
            TE_CHECKRETURN_CODE(code);
        }
        for(std::size_t i = 0u; i < count; i++) {
            code = indexAccess->put(*this->indices, indices[i]);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return TE_Ok;
    }
    TAKErr ModelImplBase::addIndices(const uint8_t *indices, const std::size_t count) NOTHROWS
    {
        if(!indexed)
            return TE_IllegalState;
        if(!this->indices.get() || this->indices->remaining() < indexAccess->transferSize(count)) {
            resize(this->indices, this->indices->size()+indexAccess->transferSize(count));
        }
        TAKErr code(TE_Ok);
        for(std::size_t i = 0u; i < count; i++) {
            code = indexAccess->put(*this->indices, indices[i]);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return TE_Ok;
    }
    std::size_t ModelImplBase::getNumMaterials() const NOTHROWS
    {
        return materials.size();
    }
    TAKErr ModelImplBase::getMaterial(Material *value, const std::size_t index) const NOTHROWS
    {
        if (index >= materials.size())
            return TE_BadIndex;
        *value = materials[index];
        return TE_Ok;
    }
    std::size_t ModelImplBase::getNumFaces() const NOTHROWS
    {
        const std::size_t vertexCount = isIndexed() ? getNumIndices() : getNumVertices();
        switch(drawMode) {
            case TEDM_Points :
                return vertexCount;
            case TEDM_Triangles :
                return vertexCount / 3u;
            case TEDM_TriangleStrip :
                return vertexCount - 2u;
            default :
                return 0u;
        }
    }
    bool ModelImplBase::isIndexed() const NOTHROWS
    {
        return indexed;
    }
    std::size_t ModelImplBase::getNumVertices() const NOTHROWS
    {
        return vertexCount;
    }
    TAKErr ModelImplBase::getPosition(Point2<double> *value, const std::size_t index) const NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!positionAccess.get())
            return TE_IllegalState;
        const void *vertices;
        code = getVertices(&vertices, TEVA_Position);
        TE_CHECKRETURN_CODE(code);
        MemBuffer2 view(reinterpret_cast<const uint8_t *>(vertices)+layout.position.offset, (index+1u)*layout.position.stride);
        code = view.position(index*layout.position.stride);
        TE_CHECKRETURN_CODE(code);
        code = positionAccess->get(&value->x, &value->y, &value->z, view);
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr ModelImplBase::getTextureCoordinate(Point2<float> *value, const VertexAttribute texAttr, const std::size_t index) const NOTHROWS
    {
        TAKErr code(TE_Ok);
        const void *vertices;
        code = getVertices(&vertices, texAttr);
        TE_CHECKRETURN_CODE(code);
        std::map<VertexAttribute, std::unique_ptr<ElementAccess<float>>>::const_iterator entry;
        entry = texCoordAccess.find(texAttr);
        if(entry == texCoordAccess.end())
            return TE_InvalidArg;
        VertexArray texCoord;
        if(texAttr == TEVA_TexCoord0)
            texCoord = layout.texCoord0;
        else if(texAttr == TEVA_TexCoord1)
            texCoord = layout.texCoord1;
        else if(texAttr == TEVA_TexCoord2)
            texCoord = layout.texCoord2;
        else if(texAttr == TEVA_TexCoord3)
            texCoord = layout.texCoord3;
        else if(texAttr == TEVA_TexCoord4)
            texCoord = layout.texCoord4;
        else if(texAttr == TEVA_TexCoord5)
            texCoord = layout.texCoord5;
        else if(texAttr == TEVA_TexCoord6)
            texCoord = layout.texCoord6;
        else if(texAttr == TEVA_TexCoord7)
            texCoord = layout.texCoord7;
        else
            return TE_InvalidArg;
        MemBuffer2 view(reinterpret_cast<const uint8_t *>(vertices)+texCoord.offset, (index+1u)*texCoord.stride);
        code = view.position(index*texCoord.stride);
        TE_CHECKRETURN_CODE(code);
        code = (*entry->second).get(&value->x, &value->y, view);
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr ModelImplBase::getNormal(Point2<float> *value, const std::size_t index) const NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!normalAccess.get())
            return TE_IllegalState;
        const void *vertices;
        code = getVertices(&vertices, TEVA_Normal);
        TE_CHECKRETURN_CODE(code);
        MemBuffer2 view(reinterpret_cast<const uint8_t *>(vertices)+layout.normal.offset, (index+1u)*layout.normal.stride);
        code = view.position(index*layout.normal.stride);
        TE_CHECKRETURN_CODE(code);
        code = normalAccess->get(&value->x, &value->y, &value->z, view);
        TE_CHECKRETURN_CODE(code);

        return code;
    }
    TAKErr ModelImplBase::getColor(unsigned int *value, const std::size_t index) const NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!positionAccess.get())
            return TE_IllegalState;
        const void *vertices;
        code = getVertices(&vertices, TEVA_Color);
        TE_CHECKRETURN_CODE(code);
        MemBuffer2 view(reinterpret_cast<const uint8_t *>(vertices)+layout.color.offset, (index+1u)*layout.color.stride);
        code = view.position(index*layout.color.stride);
        TE_CHECKRETURN_CODE(code);
        float r, g, b, a;
        code = colorAccess->get(&r, &g, &b, &a, view);
        TE_CHECKRETURN_CODE(code);

        *value =
            unnormalize<uint8_t>(a) << 24 |
            unnormalize<uint8_t>(r) << 16 |
            unnormalize<uint8_t>(g) << 8 |
            unnormalize<uint8_t>(b);

        return code;
    }
    TAKErr ModelImplBase::getVertexAttributeType(DataType *value, const unsigned int attr) const NOTHROWS
    {
        switch(attr) {
            case TEVA_Color :
                *value = layout.color.type;
                break;
            case TEVA_Normal :
                *value = layout.normal.type;
                break;
            case TEVA_Position :
                *value = layout.position.type;
                break;
            case TEVA_TexCoord0 :
                *value = layout.texCoord0.type;
                break;
            case TEVA_TexCoord1 :
                *value = layout.texCoord1.type;
                break;
            case TEVA_TexCoord2 :
                *value = layout.texCoord2.type;
                break;
            case TEVA_TexCoord3 :
                *value = layout.texCoord3.type;
                break;
            case TEVA_TexCoord4 :
                *value = layout.texCoord4.type;
                break;
            case TEVA_TexCoord5 :
                *value = layout.texCoord5.type;
                break;
            case TEVA_TexCoord6 :
                *value = layout.texCoord6.type;
                break;
            case TEVA_TexCoord7 :
                *value = layout.texCoord7.type;
                break;
            default :
                return TE_InvalidArg;
        }
        return TE_Ok;
    }
    TAKErr ModelImplBase::getIndexType(DataType *value) const NOTHROWS
    {
        if(!isIndexed())
            return TE_IllegalState;
        *value = indexType;
        return TE_Ok;
    }
    TAKErr ModelImplBase::getIndex(std::size_t *value, const std::size_t index) const NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!indexed)
            return TE_IllegalState;
        MemBuffer2 view(indices->get(), indexAccess->transferSize(getNumIndices()));
        code = view.position(indexAccess->transferSize(index));
        TE_CHECKRETURN_CODE(code);
        return indexAccess->get(value, view);
    }
    const void *ModelImplBase::getIndices() const NOTHROWS
    {
        if(!indices.get())
            return NULL;
        return (*indices).get();
    }
    std::size_t ModelImplBase::getIndexOffset() const NOTHROWS
    {
        return 0u;
    }
    std::size_t ModelImplBase::getNumIndices() const NOTHROWS
    {
        if(!indices.get())
            return 0u;
        return indices->limit() / indexAccess->transferSize(1u);
    }
    WindingOrder ModelImplBase::getFaceWindingOrder() const NOTHROWS
    {
        return windingOrder;
    }
    DrawMode ModelImplBase::getDrawMode() const NOTHROWS
    {
        return drawMode;
    }
    const Envelope2 &ModelImplBase::getAABB() const NOTHROWS
    {
        return aabb;
    }
    const VertexDataLayout &ModelImplBase::getVertexDataLayout() const NOTHROWS
    {
        return layout;
    }

    //************************************************************************//
    // InterleavedModelImplBase

    InterleavedModelBase::InterleavedModelBase(const DrawMode &mode_, const VertexDataLayout &layout_) NOTHROWS :
        ModelImplBase(mode_, layout_)
    {}
    InterleavedModelBase::InterleavedModelBase(const DrawMode &mode_, const VertexDataLayout &layout_, const DataType &indexType_) NOTHROWS :
        ModelImplBase(mode_, layout_, indexType_)
    {}

    TAKErr InterleavedModelBase::getVertices(const void **value, const std::size_t attr) const NOTHROWS
    {
        if(!vertices.get())
            return TE_IllegalState;
        switch(attr) {
            case TEVA_Color :
            case TEVA_Normal :
            case TEVA_Position :
            case TEVA_TexCoord0 :
            case TEVA_TexCoord1 :
            case TEVA_TexCoord2 :
            case TEVA_TexCoord3 :
            case TEVA_TexCoord4 :
            case TEVA_TexCoord5 :
            case TEVA_TexCoord6 :
            case TEVA_TexCoord7 :
                break;
            default :
                return TE_InvalidArg;
        }
        if(!(getVertexDataLayout().attributes&attr))
            return TE_InvalidArg;
        *value = (*vertices).get();
        return TE_Ok;
    }
    TAKErr InterleavedModelBase::reserveVertices(const std::size_t count) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(vertices.get())
            return TE_IllegalState;
        const VertexDataLayout layout = getVertexDataLayout();
        std::size_t size;
        code = VertexDataLayout_requiredInterleavedDataSize(&size, layout, count);
        TE_CHECKRETURN_CODE(code);

        return resize(vertices, size);
    }

    //************************************************************************//
    // DefaultInterleavedModel

    DefaultInterleavedModel::DefaultInterleavedModel(const DrawMode &mode_, const VertexDataLayout &layout_) NOTHROWS :
        InterleavedModelBase(mode_, layout_)
    {}
    DefaultInterleavedModel::DefaultInterleavedModel(const DrawMode &mode_, const VertexDataLayout &layout_, const DataType &indexType_) NOTHROWS :
        InterleavedModelBase(mode_, layout_, indexType_)
    {}

    TAKErr DefaultInterleavedModel::addVertex(const double posx, const double posy, const double posz,
                                              const float texu, const float texv,
                                              const float nx, const float ny, const float nz,
                                              const float r, const float g, const float b, const float a) NOTHROWS
    {
        TAKErr code(TE_Ok);
#define GROW_SIZE 1024u
        if(!vertices.get()) {
            code = reserveVertices(GROW_SIZE);
            TE_CHECKRETURN_CODE(code);
        }
        const VertexDataLayout layout = getVertexDataLayout();
        if(vertices->remaining() < layout.position.stride) {
            code = resize(vertices, vertices->position() + (layout.position.stride*GROW_SIZE));
            TE_CHECKRETURN_CODE(code);
            code = vertices->position(layout.position.offset+(layout.position.stride*vertexCount));
            TE_CHECKRETURN_CODE(code);
        }
#undef GROW_SIZE

        // write the vertex data
        if(layout.attributes&TEVA_Position) {
            code = positionAccess->put(*vertices, posx, posy, posz);
            TE_CHECKRETURN_CODE(code);
        }
#define ADD_VERTEX_TEXCOORD(teva) \
    if(layout.attributes&teva) { \
        code = texCoordAccess[teva]->put(*vertices, texu, texv); \
        TE_CHECKRETURN_CODE(code); \
    }

        ADD_VERTEX_TEXCOORD(TEVA_TexCoord0);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord1);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord2);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord3);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord4);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord5);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord6);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord7);
#undef ADD_VERTEX_TEXCOORD

        if(layout.attributes&TEVA_Normal) {
            code = normalAccess->put(*vertices, nx, ny, nz);
            TE_CHECKRETURN_CODE(code);
        }
        if(layout.attributes&TEVA_Color) {
            code = colorAccess->put(*vertices, r, g, b, a);
            TE_CHECKRETURN_CODE(code);
        }

        if(!vertexCount) {
            aabb.minX = posx;
            aabb.minY = posy;
            aabb.minZ = posz;
            aabb.maxX = posx;
            aabb.maxY = posy;
            aabb.maxZ = posz;
        } else {
            if(posx < aabb.minX)        aabb.minX = posx;
            else if(posx > aabb.maxX)   aabb.maxX = posx;
            if(posy < aabb.minY)        aabb.minY = posy;
            else if(posy > aabb.maxY)   aabb.maxY = posy;
            if(posz < aabb.minZ)        aabb.minZ = posz;
            else if(posz > aabb.maxZ)   aabb.maxZ = posz;
        }
        vertexCount++;
        return code;
    }
    TAKErr DefaultInterleavedModel::addVertex(const double posx, const double posy, const double posz,
                                              const float *texCoords,
                                              const float nx, const float ny, const float nz,
                                              const float r, const float g, const float b, const float a) NOTHROWS
    {
        TAKErr code(TE_Ok);
#define GROW_SIZE 1024u
        if(!vertices.get()) {
            code = reserveVertices(GROW_SIZE);
            TE_CHECKRETURN_CODE(code);
        }
        const VertexDataLayout layout = getVertexDataLayout();
        if(vertices->remaining() < layout.position.stride) {
            code = resize(vertices, vertices->position() + (layout.position.stride*GROW_SIZE));
            TE_CHECKRETURN_CODE(code);
            code = vertices->position(layout.position.offset+(layout.position.stride*vertexCount));
            TE_CHECKRETURN_CODE(code);
        }
#undef GROW_SIZE

        // write the vertex data
        if(layout.attributes&TEVA_Position) {
            code = positionAccess->put(*vertices, posx, posy, posz);
            TE_CHECKRETURN_CODE(code);
        }
#define ADD_VERTEX_TEXCOORD(teva) \
    if(layout.attributes&teva) { \
        const float texu = *texCoords++; \
        const float texv = *texCoords++; \
        code = texCoordAccess[teva]->put(*vertices, texu, texv); \
        TE_CHECKRETURN_CODE(code); \
    }

        ADD_VERTEX_TEXCOORD(TEVA_TexCoord0);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord1);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord2);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord3);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord4);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord5);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord6);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord7);
#undef ADD_VERTEX_TEXCOORD

        if(layout.attributes&TEVA_Normal) {
            code = normalAccess->put(*vertices, nx, ny, nz);
            TE_CHECKRETURN_CODE(code);
        }
        if(layout.attributes&TEVA_Color) {
            code = colorAccess->put(*vertices, r, g, b, a);
            TE_CHECKRETURN_CODE(code);
        }

        if(!vertexCount) {
            aabb.minX = posx;
            aabb.minY = posy;
            aabb.minZ = posz;
            aabb.maxX = posx;
            aabb.maxY = posy;
            aabb.maxZ = posz;
        } else {
            if(posx < aabb.minX)        aabb.minX = posx;
            else if(posx > aabb.maxX)   aabb.maxX = posx;
            if(posy < aabb.minY)        aabb.minY = posy;
            else if(posy > aabb.maxY)   aabb.maxY = posy;
            if(posz < aabb.minZ)        aabb.minZ = posz;
            else if(posz > aabb.maxZ)   aabb.maxZ = posz;
        }
        vertexCount++;

        return code;
    }
    TAKErr DefaultInterleavedModel::reserveVertices(const std::size_t count) NOTHROWS
    {
        TAKErr code(TE_Ok);
        code = InterleavedModelBase::reserveVertices(count);
        TE_CHECKRETURN_CODE(code);
        code = vertices->position(getVertexDataLayout().position.offset);
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    //************************************************************************//
    // GenericInterleavedModel

    GenericInterleavedModel::GenericInterleavedModel(const DrawMode &mode_, const VertexDataLayout &layout_) NOTHROWS :
        InterleavedModelBase(mode_, layout_)
    {}
    GenericInterleavedModel::GenericInterleavedModel(const DrawMode &mode_, const VertexDataLayout &layout_, const DataType &indexType_) NOTHROWS :
        InterleavedModelBase(mode_, layout_, indexType_)
    {}

    TAKErr GenericInterleavedModel::addVertex(const double posx, const double posy, const double posz,
                                              const float texu, const float texv,
                                              const float nx, const float ny, const float nz,
                                              const float r, const float g, const float b, const float a) NOTHROWS
    {
        TAKErr code(TE_Ok);
#define GROW_SIZE 1024u
        if(!vertices.get()) {
            code = reserveVertices(GROW_SIZE);
            TE_CHECKRETURN_CODE(code);
        }
        const VertexDataLayout layout = getVertexDataLayout();
        if(layout.attributes&TEVA_Position) {
            const std::size_t required = layout.position.offset + (layout.position.stride*(vertexCount+1u));
            if(vertices->limit() < required) {
                code = resize(vertices, layout.position.offset + (layout.position.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
            }
            code = vertices->position(layout.position.offset + (layout.position.stride*vertexCount));
            TE_CHECKRETURN_CODE(code);
            code = positionAccess->put(*vertices, posx, posy, posz);
            TE_CHECKRETURN_CODE(code);
        }
#define ADD_VERTEX_TEXCOORD(teva, vao) \
    if(layout.attributes&teva) { \
        VertexArray texCoord = layout.vao; \
        const std::size_t required = texCoord.offset + (texCoord.stride*(vertexCount+1u)); \
        if(vertices->limit() < required) { \
            code = resize(vertices, texCoord.offset + (texCoord.stride*(vertexCount+GROW_SIZE))); \
            TE_CHECKRETURN_CODE(code); \
        } \
        code = vertices->position(texCoord.offset + (texCoord.stride*vertexCount)); \
        TE_CHECKRETURN_CODE(code); \
        code = texCoordAccess[teva]->put(*vertices, texu, texv); \
        TE_CHECKRETURN_CODE(code); \
    }

        ADD_VERTEX_TEXCOORD(TEVA_TexCoord0, texCoord0);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord1, texCoord1);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord2, texCoord2);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord3, texCoord3);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord4, texCoord4);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord5, texCoord5);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord6, texCoord6);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord7, texCoord7);
#undef ADD_VERTEX_TEXCOORD
        if(layout.attributes&TEVA_Normal) {
            const std::size_t required = layout.normal.offset + (layout.normal.stride*(vertexCount+1u));
            if(vertices->limit() < required) {
                code = resize(vertices, layout.normal.offset + (layout.normal.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
            }
            code = vertices->position(layout.normal.offset + (layout.normal.stride*vertexCount));
            TE_CHECKRETURN_CODE(code);
            code = normalAccess->put(*vertices, nx, ny, nz);
            TE_CHECKRETURN_CODE(code);
        }
        if(layout.attributes&TEVA_Color) {
            const std::size_t required = layout.color.offset + (layout.color.stride*(vertexCount+1u));
            if(vertices->limit() < required) {
                code = resize(vertices, layout.color.offset + (layout.color.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
            }
            code = vertices->position(layout.color.offset + (layout.color.stride*vertexCount));
            TE_CHECKRETURN_CODE(code);
            code = colorAccess->put(*vertices, r, g, b, a);
            TE_CHECKRETURN_CODE(code);
        }
#undef GROW_SIZE
        if(!vertexCount) {
            aabb.minX = posx;
            aabb.minY = posy;
            aabb.minZ = posz;
            aabb.maxX = posx;
            aabb.maxY = posy;
            aabb.maxZ = posz;
        } else {
            if(posx < aabb.minX)        aabb.minX = posx;
            else if(posx > aabb.maxX)   aabb.maxX = posx;
            if(posy < aabb.minY)        aabb.minY = posy;
            else if(posy > aabb.maxY)   aabb.maxY = posy;
            if(posz < aabb.minZ)        aabb.minZ = posz;
            else if(posz > aabb.maxZ)   aabb.maxZ = posz;
        }
        vertexCount++;
        return code;
    }
    TAKErr GenericInterleavedModel::addVertex(const double posx, const double posy, const double posz,
                                              const float *texCoords,
                                              const float nx, const float ny, const float nz,
                                              const float r, const float g, const float b, const float a) NOTHROWS
    {
        TAKErr code(TE_Ok);
#define GROW_SIZE 1024u
        if(!vertices.get()) {
            code = reserveVertices(GROW_SIZE);
            TE_CHECKRETURN_CODE(code);
        }
        const VertexDataLayout layout = getVertexDataLayout();
        if(layout.attributes&TEVA_Position) {
            const std::size_t required = layout.position.offset + (layout.position.stride*(vertexCount+1u));
            if(vertices->limit() < required) {
                code = resize(vertices, layout.position.offset + (layout.position.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
            }
            code = vertices->position(layout.position.offset + (layout.position.stride*vertexCount));
            TE_CHECKRETURN_CODE(code);
            code = positionAccess->put(*vertices, posx, posy, posz);
            TE_CHECKRETURN_CODE(code);
        }
#define ADD_VERTEX_TEXCOORD(teva, vao) \
    if(layout.attributes&teva) { \
        VertexArray texCoord = layout.vao; \
        const std::size_t required = texCoord.offset + (texCoord.stride*(vertexCount+1u)); \
        if(vertices->limit() < required) { \
            code = resize(vertices, texCoord.offset + (texCoord.stride*(vertexCount+GROW_SIZE))); \
            TE_CHECKRETURN_CODE(code); \
        } \
        code = vertices->position(texCoord.offset + (texCoord.stride*vertexCount)); \
        TE_CHECKRETURN_CODE(code); \
        const float texu = *texCoords++; \
        const float texv = *texCoords++; \
        code = texCoordAccess[teva]->put(*vertices, texu, texv); \
        TE_CHECKRETURN_CODE(code); \
    }
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord0, texCoord0);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord1, texCoord1);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord2, texCoord2);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord3, texCoord3);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord4, texCoord4);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord5, texCoord5);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord6, texCoord6);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord7, texCoord7);
#undef ADD_VERTEX_TEXCOORD

        if(layout.attributes&TEVA_Normal) {
            const std::size_t required = layout.normal.offset + (layout.normal.stride*(vertexCount+1u));
            if(vertices->limit() < required) {
                code = resize(vertices, layout.normal.offset + (layout.normal.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
            }
            code = vertices->position(layout.normal.offset + (layout.normal.stride*vertexCount));
            TE_CHECKRETURN_CODE(code);
            code = normalAccess->put(*vertices, nx, ny, nz);
            TE_CHECKRETURN_CODE(code);
        }
        if(layout.attributes&TEVA_Color) {
            const std::size_t required = layout.color.offset + (layout.color.stride*(vertexCount+1u));
            if(vertices->limit() < required) {
                code = resize(vertices, layout.color.offset + (layout.color.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
            }
            code = vertices->position(layout.color.offset + (layout.color.stride*vertexCount));
            TE_CHECKRETURN_CODE(code);
            code = colorAccess->put(*vertices, r, g, b, a);
            TE_CHECKRETURN_CODE(code);
        }
#undef GROW_SIZE
        if(!vertexCount) {
            aabb.minX = posx;
            aabb.minY = posy;
            aabb.minZ = posz;
            aabb.maxX = posx;
            aabb.maxY = posy;
            aabb.maxZ = posz;
        } else {
            if(posx < aabb.minX)        aabb.minX = posx;
            else if(posx > aabb.maxX)   aabb.maxX = posx;
            if(posy < aabb.minY)        aabb.minY = posy;
            else if(posy > aabb.maxY)   aabb.maxY = posy;
            if(posz < aabb.minZ)        aabb.minZ = posz;
            else if(posz > aabb.maxZ)   aabb.maxZ = posz;
        }
        vertexCount++;
        return code;
    }

    NonInterleavedModel::NonInterleavedModel(const DrawMode &mode_, const VertexDataLayout &layout_) NOTHROWS :
        ModelImplBase(mode_, layout_)
    {}
    NonInterleavedModel::NonInterleavedModel(const DrawMode &mode_, const VertexDataLayout &layout_, const DataType &indexType_) NOTHROWS :
        ModelImplBase(mode_, layout_, indexType_)
    {}

    TAKErr NonInterleavedModel::addVertex(const double posx, const double posy, const double posz,
                                          const float texu, const float texv,
                                          const float nx, const float ny, const float nz,
                                          const float r, const float g, const float b, const float a) NOTHROWS
    {
        TAKErr code(TE_Ok);
#define GROW_SIZE 1024u
        if(!positions.get()) {
            code = reserveVertices(GROW_SIZE);
            TE_CHECKRETURN_CODE(code);
        }
        const VertexDataLayout layout = getVertexDataLayout();
        if(layout.attributes&TEVA_Position) {
            const std::size_t required = layout.position.offset + (layout.position.stride*(vertexCount+1u));
            if(positions->limit() < required) {
                code = resize(positions, layout.position.offset + (layout.position.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
                code = positions->position(layout.position.offset + (layout.position.stride*vertexCount));
                TE_CHECKRETURN_CODE(code);
            }
            code = positionAccess->put(*positions, posx, posy, posz);
            TE_CHECKRETURN_CODE(code);
            // position the 'positions' buffer at the next position write pos
            if(layout.position.stride > positionAccess->transferSize(3u)) {
                code = positions->skip(layout.position.stride-positionAccess->transferSize(3u));
                TE_CHECKRETURN_CODE(code);
            }
        }
#define ADD_VERTEX_TEXCOORD(teva, vao) \
    if(layout.attributes&teva) { \
        VertexArray texCoord = layout.vao; \
        const std::size_t required = texCoord.offset + (texCoord.stride*(vertexCount+1u)); \
        std::unique_ptr<MemBuffer2> &texCoordsBuf = texCoords[teva]; \
        if(texCoordsBuf->limit() < required) { \
            code = resize(texCoordsBuf, texCoord.offset + (texCoord.stride*(vertexCount+GROW_SIZE))); \
            TE_CHECKRETURN_CODE(code); \
            code = texCoordsBuf->position(texCoord.offset + (texCoord.stride*vertexCount)); \
            TE_CHECKRETURN_CODE(code); \
        } \
        ElementAccess<float> &access = *texCoordAccess[teva]; \
        code = access.put(*texCoordsBuf, texu, texv); \
        TE_CHECKRETURN_CODE(code); \
        if(texCoord.stride > access.transferSize(2u)) { \
            code = texCoordsBuf->skip(texCoord.stride-access.transferSize(2u)); \
            TE_CHECKRETURN_CODE(code); \
        } \
    }
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord0, texCoord0);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord1, texCoord1);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord2, texCoord2);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord3, texCoord3);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord4, texCoord4);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord5, texCoord5);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord6, texCoord6);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord7, texCoord7);
#undef ADD_VERTEX_TEXCOORD

        if(layout.attributes&TEVA_Normal) {
            const std::size_t required = layout.normal.offset + (layout.normal.stride*(vertexCount+1u));
            if(normals->limit() < required) {
                code = resize(normals, layout.normal.offset + (layout.normal.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
                code = normals->position(layout.normal.offset + (layout.normal.stride*vertexCount));
                TE_CHECKRETURN_CODE(code);
            }
            code = normalAccess->put(*normals, nx, ny, nz);
            TE_CHECKRETURN_CODE(code);
            // position the 'normals' buffer at the next normal write position
            if(layout.normal.stride > normalAccess->transferSize(3u)) {
                code = normals->skip(layout.normal.stride-normalAccess->transferSize(3u));
                TE_CHECKRETURN_CODE(code);
            }
        }
        if(layout.attributes&TEVA_Color) {
            const std::size_t required = layout.color.offset + (layout.color.stride*(vertexCount+1u));
            if(colors->limit() < required) {
                code = resize(colors, layout.color.offset + (layout.color.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
                code = colors->position(layout.color.offset + (layout.color.stride*vertexCount));
                TE_CHECKRETURN_CODE(code);
            }
            code = colorAccess->put(*colors, r, g, b, a);
            TE_CHECKRETURN_CODE(code);
            if(layout.color.stride > colorAccess->transferSize(4u)) {
                code = colors->skip(layout.color.stride-colorAccess->transferSize(4u));
                TE_CHECKRETURN_CODE(code);
            }
        }
#undef GROW_SIZE

        if(!vertexCount) {
            aabb.minX = posx;
            aabb.minY = posy;
            aabb.minZ = posz;
            aabb.maxX = posx;
            aabb.maxY = posy;
            aabb.maxZ = posz;
        } else {
            if(posx < aabb.minX)        aabb.minX = posx;
            else if(posx > aabb.maxX)   aabb.maxX = posx;
            if(posy < aabb.minY)        aabb.minY = posy;
            else if(posy > aabb.maxY)   aabb.maxY = posy;
            if(posz < aabb.minZ)        aabb.minZ = posz;
            else if(posz > aabb.maxZ)   aabb.maxZ = posz;
        }
        vertexCount++;
        return code;
    }
    TAKErr NonInterleavedModel::addVertex(const double posx, const double posy, const double posz,
                                          const float *texuv,
                                          const float nx, const float ny, const float nz,
                                          const float r, const float g, const float b, const float a) NOTHROWS
    {
        TAKErr code(TE_Ok);
#define GROW_SIZE 1024u
        if(!positions.get()) {
            code = reserveVertices(GROW_SIZE);
            TE_CHECKRETURN_CODE(code);
        }
        const VertexDataLayout layout = getVertexDataLayout();
        if(layout.attributes&TEVA_Position) {
            const std::size_t required = layout.position.offset + (layout.position.stride*(vertexCount+1u));
            if(positions->limit() < required) {
                code = resize(positions, layout.position.offset + (layout.position.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
                code = positions->position(layout.position.offset + (layout.position.stride*vertexCount));
                TE_CHECKRETURN_CODE(code);
            }
            code = positionAccess->put(*positions, posx, posy, posz);
            TE_CHECKRETURN_CODE(code);
            // position the 'positions' buffer at the next position write pos
            if(layout.position.stride > positionAccess->transferSize(3u)) {
                code = positions->skip(layout.position.stride-positionAccess->transferSize(3u));
                TE_CHECKRETURN_CODE(code);
            }
        }
#define ADD_VERTEX_TEXCOORD(teva, vao) \
    if(layout.attributes&teva) { \
        VertexArray texCoord = layout.vao; \
        const std::size_t required = texCoord.offset + (texCoord.stride*(vertexCount+1u)); \
        std::unique_ptr<MemBuffer2> &texCoordsBuf = texCoords[teva]; \
        if(texCoordsBuf->limit() < required) { \
            code = resize(texCoordsBuf, texCoord.offset + (texCoord.stride*(vertexCount+GROW_SIZE))); \
            TE_CHECKRETURN_CODE(code); \
            code = texCoordsBuf->position(texCoord.offset + (texCoord.stride*vertexCount)); \
            TE_CHECKRETURN_CODE(code); \
        } \
        ElementAccess<float> &access = *texCoordAccess[teva]; \
        const float texu = *texuv++; \
        const float texv = *texuv++; \
        code = access.put(*texCoordsBuf, texu, texv); \
        TE_CHECKRETURN_CODE(code); \
        if(texCoord.stride > access.transferSize(2u)) { \
            code = texCoordsBuf->skip(texCoord.stride-access.transferSize(2u)); \
            TE_CHECKRETURN_CODE(code); \
        } \
    }
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord0, texCoord0);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord1, texCoord1);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord2, texCoord2);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord3, texCoord3);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord4, texCoord4);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord5, texCoord5);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord6, texCoord6);
        ADD_VERTEX_TEXCOORD(TEVA_TexCoord7, texCoord7);
#undef ADD_VERTEX_TEXCOORD

        if(layout.attributes&TEVA_Normal) {
            const std::size_t required = layout.normal.offset + (layout.normal.stride*(vertexCount+1u));
            if(normals->limit() < required) {
                code = resize(normals, layout.normal.offset + (layout.normal.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
                code = normals->position(layout.normal.offset + (layout.normal.stride*vertexCount));
                TE_CHECKRETURN_CODE(code);
            }
            code = normalAccess->put(*normals, nx, ny, nz);
            TE_CHECKRETURN_CODE(code);
            // position the 'normals' buffer at the next normal write position
            if(layout.normal.stride > normalAccess->transferSize(3u)) {
                code = normals->skip(layout.normal.stride-normalAccess->transferSize(3u));
                TE_CHECKRETURN_CODE(code);
            }
        }
        if(layout.attributes&TEVA_Color) {
            const std::size_t required = layout.color.offset + (layout.color.stride*(vertexCount+1u));
            if(colors->limit() < required) {
                code = resize(colors, layout.color.offset + (layout.color.stride*(vertexCount+GROW_SIZE)));
                TE_CHECKRETURN_CODE(code);
                code = colors->position(layout.color.offset + (layout.color.stride*vertexCount));
                TE_CHECKRETURN_CODE(code);
            }
            code = colorAccess->put(*colors, r, g, b, a);
            TE_CHECKRETURN_CODE(code);
            if(layout.color.stride > colorAccess->transferSize(4u)) {
                code = colors->skip(layout.color.stride-colorAccess->transferSize(4u));
                TE_CHECKRETURN_CODE(code);
            }
        }
#undef GROW_SIZE

        if(!vertexCount) {
            aabb.minX = posx;
            aabb.minY = posy;
            aabb.minZ = posz;
            aabb.maxX = posx;
            aabb.maxY = posy;
            aabb.maxZ = posz;
        } else {
            if(posx < aabb.minX)        aabb.minX = posx;
            else if(posx > aabb.maxX)   aabb.maxX = posx;
            if(posy < aabb.minY)        aabb.minY = posy;
            else if(posy > aabb.maxY)   aabb.maxY = posy;
            if(posz < aabb.minZ)        aabb.minZ = posz;
            else if(posz > aabb.maxZ)   aabb.maxZ = posz;
        }
        vertexCount++;
        return code;
    }
    TAKErr NonInterleavedModel::reserveVertices(const std::size_t count) NOTHROWS
    {
        TAKErr code(TE_Ok);
        const VertexDataLayout layout = getVertexDataLayout();
        if(layout.attributes&TEVA_Position) {
            if(positions.get())
                return TE_IllegalState;
            code = resize(positions, layout.position.offset + (layout.position.stride*(count+1u)));
            TE_CHECKRETURN_CODE(code);
            code = positions->position(layout.position.offset);
            TE_CHECKRETURN_CODE(code);
        }
#define RESERVE_VERTICES_TEXCOORD(teva, vao) \
    if(layout.attributes&teva) { \
        if(texCoords.find(teva) != texCoords.end()) \
            return TE_IllegalState; \
        VertexArray texCoord = layout.vao; \
        std::unique_ptr<MemBuffer2> texCoordData; \
        code = resize(texCoordData, texCoord.offset + (texCoord.stride*(count+1u))); \
        TE_CHECKRETURN_CODE(code); \
        code = texCoordData->position(texCoord.offset); \
        TE_CHECKRETURN_CODE(code); \
        texCoords[teva] = std::move(texCoordData); \
    }

        RESERVE_VERTICES_TEXCOORD(TEVA_TexCoord0, texCoord0);
        RESERVE_VERTICES_TEXCOORD(TEVA_TexCoord1, texCoord1);
        RESERVE_VERTICES_TEXCOORD(TEVA_TexCoord2, texCoord2);
        RESERVE_VERTICES_TEXCOORD(TEVA_TexCoord3, texCoord3);
        RESERVE_VERTICES_TEXCOORD(TEVA_TexCoord4, texCoord4);
        RESERVE_VERTICES_TEXCOORD(TEVA_TexCoord5, texCoord5);
        RESERVE_VERTICES_TEXCOORD(TEVA_TexCoord6, texCoord6);
        RESERVE_VERTICES_TEXCOORD(TEVA_TexCoord7, texCoord7);
#undef RESERVE_VERTICES_TEXCOORD
        if(layout.attributes&TEVA_Normal) {
            if(normals.get())
                return TE_IllegalState;
            code = resize(normals, layout.normal.offset + (layout.normal.stride*(count+1u)));
            TE_CHECKRETURN_CODE(code);
            code = normals->position(layout.normal.offset);
            TE_CHECKRETURN_CODE(code);
        }
        if(layout.attributes&TEVA_Color) {
            if(colors.get())
                return TE_IllegalState;
            code = resize(colors, layout.color.offset + (layout.color.stride*(count+1u)));
            TE_CHECKRETURN_CODE(code);
            code = colors->position(layout.color.offset);
            TE_CHECKRETURN_CODE(code);
        }
        return code;
    }
    TAKErr NonInterleavedModel::getVertices(const void **vertices, const std::size_t attrs) const NOTHROWS
    {
        if(!(attrs&getVertexDataLayout().attributes))
            return TE_InvalidArg;
        switch(attrs) {
            case TEVA_Position :
                if(!positions.get())
                    return TE_IllegalState;
                *vertices = (*positions).get();
                break;
            case TEVA_Normal :
                if(!normals.get())
                    return TE_IllegalState;
                *vertices = (*normals).get();
                break;
#define CASE_TEXCOORD(teva) \
    case teva : \
    { \
        std::map<VertexAttribute, std::unique_ptr<MemBuffer2>>::const_iterator entry; \
        entry = texCoords.find(teva); \
        if(entry == texCoords.end()) \
            return TE_IllegalState; \
        *vertices = (*entry->second).get(); \
        break; \
    }
            CASE_TEXCOORD(TEVA_TexCoord0)
            CASE_TEXCOORD(TEVA_TexCoord1)
            CASE_TEXCOORD(TEVA_TexCoord2)
            CASE_TEXCOORD(TEVA_TexCoord3)
            CASE_TEXCOORD(TEVA_TexCoord4)
            CASE_TEXCOORD(TEVA_TexCoord5)
            CASE_TEXCOORD(TEVA_TexCoord6)
            CASE_TEXCOORD(TEVA_TexCoord7)
#undef CASE_TEXCOORD
            case TEVA_Color :
                if(!colors.get())
                    return TE_IllegalState;
                *vertices = (*colors).get();
                break;
            default :
                return TE_InvalidArg;
        }
        return TE_Ok;
    }

    TAKErr checkInitParams(const DrawMode &mode, const VertexDataLayout &layout, const DataType &indexType) NOTHROWS
    {
        switch(mode) {
            case TEDM_Points :
            case TEDM_Triangles :
            case TEDM_TriangleStrip :
                break;
            default :
                return TE_InvalidArg;
        }
        if(!layout.attributes)
            return TE_InvalidArg;
        if(!(layout.attributes&TEVA_Position))
            return TE_InvalidArg;

        // XXX - verify interleave and stride/offset configuration is sane

        switch(indexType) {
            case TEDT_UInt8 :
            case TEDT_UInt16 :
            case TEDT_UInt32 :
                break;
            default :
                return TE_InvalidArg;
        }

        return TE_Ok;
    }

    TAKErr resize(std::unique_ptr<MemBuffer2> &buf, const std::size_t newSize) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if(!buf.get() || buf->size() < newSize) {
            std::unique_ptr<void, void(*)(const void *)> membuf(NULL, NULL);
            code = allocateV(membuf, newSize);
            TE_CHECKRETURN_CODE(code);
            std::unique_ptr<MemBuffer2> resized(new MemBuffer2(std::move(membuf), newSize));
            if(buf.get()) {
                buf->flip();
                code = resized->put(buf->get(), buf->limit());
                TE_CHECKRETURN_CODE(code);
            }
            buf = std::move(resized);
        }
        return code;
    }
    template<class T>
    TAKErr createElementAccess(std::unique_ptr<ElementAccess<T>> &value, const DataType &type, const bool normalized) NOTHROWS
    {
        switch(type) {
#define CASE_STMT(l, t) \
    case l : \
        if(normalized) \
            value = std::unique_ptr<ElementAccess<T>>(new t##_NormalizedElementAccess<T>()); \
        else \
            value = std::unique_ptr<ElementAccess<T>>(new t##_ElementAccess<T>()); \
        break;

            CASE_STMT(TEDT_UInt8, uint8_t)
            CASE_STMT(TEDT_Int8, int8_t)
            CASE_STMT(TEDT_UInt16, uint16_t)
            CASE_STMT(TEDT_Int16, int16_t)
            CASE_STMT(TEDT_UInt32, uint32_t)
            CASE_STMT(TEDT_Int32, int32_t)
            CASE_STMT(TEDT_Float32, float)
            CASE_STMT(TEDT_Float64, double)
#undef CASE_STMT
            default :
                return TE_Unsupported;
        }
        return TE_Ok;
    }
    template<class T>
    float normalize(const T value) NOTHROWS
    {
        return (float)((double)value / (double)std::numeric_limits<T>::max());
    }
    template<class T>
    T unnormalize(const float value) NOTHROWS
    {
        const T max = std::numeric_limits<T>::max();
        return (T)std::min(value * (double)max, (double)max);
    }
    bool isDefaultInterleave(const VertexDataLayout &layout) NOTHROWS
    {
        if(!layout.interleaved)
            return false;
        if(!(layout.attributes&TEVA_Position))
            return false;
        std::size_t vertexSize = 0u;

        std::unique_ptr<ElementAccess<double>> posAccess;
        if(createElementAccess(posAccess, layout.position.type, false) != TE_Ok)
            return false;
        vertexSize += posAccess->transferSize(3u);
        posAccess.reset();

#define CHECK_ATTR(teva, vao, t, elems, n) \
    if(layout.attributes&teva) { \
        if(layout.vao.offset != (layout.position.offset+vertexSize)) \
            return false; \
        if(layout.vao.stride != layout.position.stride) \
            return false; \
        std::unique_ptr<ElementAccess<t>> access; \
        if(createElementAccess(access, layout.vao.type, n) != TE_Ok) \
            return false; \
        vertexSize += access->transferSize(elems); \
        access.reset(); \
    }
        CHECK_ATTR(TEVA_TexCoord0, texCoord0, float, 2u, false);
        CHECK_ATTR(TEVA_TexCoord1, texCoord1, float, 2u, false);
        CHECK_ATTR(TEVA_TexCoord2, texCoord2, float, 2u, false);
        CHECK_ATTR(TEVA_TexCoord3, texCoord3, float, 2u, false);
        CHECK_ATTR(TEVA_TexCoord4, texCoord4, float, 2u, false);
        CHECK_ATTR(TEVA_TexCoord5, texCoord5, float, 2u, false);
        CHECK_ATTR(TEVA_TexCoord6, texCoord6, float, 2u, false);
        CHECK_ATTR(TEVA_TexCoord7, texCoord7, float, 2u, false);
        CHECK_ATTR(TEVA_Normal, normal, float, 3u, false);
        CHECK_ATTR(TEVA_Color, color, float, 4u, true);

#undef CHECK_ATTR

        // verify the position stride is equal to the vertex size
        if(layout.position.stride != vertexSize)
            return false;

        return true;
    }
    VertexDataLayout defaultInterleavedLayout(unsigned int attrs) NOTHROWS
    {
        VertexDataLayout layout;
        layout.attributes = attrs;
        layout.interleaved = true;

        std::size_t vertexSize = 0u;

#define CONFIGURE_ATTR(teva, vao, eat, dt, elems, n) \
    if(layout.attributes&teva) { \
        layout.vao.offset = vertexSize; \
        layout.vao.type = dt; \
        std::unique_ptr<ElementAccess<eat>> access; \
        if(createElementAccess(access, layout.vao.type, n) == TE_Ok) { \
            vertexSize += access->transferSize(elems); \
            access.reset(); \
        } \
    }
        CONFIGURE_ATTR(TEVA_Position, position, double, TEDT_Float32, 3u, false);
        CONFIGURE_ATTR(TEVA_TexCoord0, texCoord0, float, TEDT_Float32, 2u, false);
        CONFIGURE_ATTR(TEVA_TexCoord1, texCoord1, float, TEDT_Float32, 2u, false);
        CONFIGURE_ATTR(TEVA_TexCoord2, texCoord2, float, TEDT_Float32, 2u, false);
        CONFIGURE_ATTR(TEVA_TexCoord3, texCoord3, float, TEDT_Float32, 2u, false);
        CONFIGURE_ATTR(TEVA_TexCoord4, texCoord4, float, TEDT_Float32, 2u, false);
        CONFIGURE_ATTR(TEVA_TexCoord5, texCoord5, float, TEDT_Float32, 2u, false);
        CONFIGURE_ATTR(TEVA_TexCoord6, texCoord6, float, TEDT_Float32, 2u, false);
        CONFIGURE_ATTR(TEVA_TexCoord7, texCoord7, float, TEDT_Float32, 2u, false);
        CONFIGURE_ATTR(TEVA_Normal, normal, float, TEDT_Float32, 3u, false);
        CONFIGURE_ATTR(TEVA_Color, color, float, TEDT_UInt8, 4u, true);
#undef CONFIGURE_ATTR

        layout.position.stride = vertexSize;
        layout.texCoord0.stride = vertexSize;
        layout.texCoord1.stride = vertexSize;
        layout.texCoord2.stride = vertexSize;
        layout.texCoord3.stride = vertexSize;
        layout.texCoord4.stride = vertexSize;
        layout.texCoord5.stride = vertexSize;
        layout.texCoord6.stride = vertexSize;
        layout.texCoord7.stride = vertexSize;
        layout.normal.stride = vertexSize;
        layout.color.stride = vertexSize;

        return layout;
    }
}
