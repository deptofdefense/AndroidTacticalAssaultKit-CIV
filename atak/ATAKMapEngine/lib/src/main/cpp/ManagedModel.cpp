#include "ManagedModel.h"

#include "common.h"
#include "interop/JNIStringUTF.h"

using namespace TAKEngine::Model::Impl;

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

using namespace TAKEngineJNI::Interop;

namespace
{
    class JModelInterface
    {
    public :
        JModelInterface(JNIEnv *env);
    public :
        jclass clazz;
        jmethodID getIndex;
        jmethodID getPosition;
        jmethodID getTextureCoordinate;
        jmethodID getNormal;
        jmethodID getColor;

        jclass PointD_clazz;
        jmethodID PointD_ctor__DDD;

        bool valid;
    };

    JModelInterface &java_interface(JNIEnv *env);
}
/*
jobject jmodel;
private :
TAK::Engine::Port::String textureUri;
std::size_t numVertices;
std::size_t numFaces;
bool indexed;
TAK::Engine::Port::DataType indexType;
const void *indices;
std::size_t numIndices;
const void *positionVertices;
const void *texCoordVertices;
const void *normalVertices;
const void *colorVertices;
TAK::Engine::Model::WindingOrder windingOrder;
TAK::Engine::Model::DrawMode drawMode;
TAK::Engine::Feature::Envelope2 aabb;
TAK::Engine::Model::VertexDataLayout layout;
*/

ManagedModel::ManagedModel(JNIEnv *env,
                           jobject jmodel_,
                           const char *textureUri_,
                           const std::size_t numVertices_,
                           const std::size_t numFaces_,
                           const void *positions_,
                           const void *texCoords_,
                           const void *normals_,
                           const void *colors_,
                           const WindingOrder &windingOrder_,
                           const DrawMode &drawMode_,
                           const Envelope2 &aabb_,
                           const VertexDataLayout &layout_) NOTHROWS :
   jmodel(env->NewGlobalRef(jmodel_)),
   numVertices(numVertices_),
   numFaces(numFaces_),
   positionVertices(positions_),
   texCoord0Vertices(texCoords_),
   normalVertices(normals_),
   colorVertices(colors_),
   windingOrder(windingOrder_),
   drawMode(drawMode_),
   aabb(aabb_),
   layout(layout_),
   indexed(false),
   indexOffset(0u),
   indices(NULL),
   indexType(TEDT_UInt16)
{
    Material material;
    material.propertyType = Material::TEPT_Diffuse;
    material.color = 0xFFFFFFFFu;
    material.textureUri = textureUri_;
    materials.push_back(material);

}
ManagedModel::ManagedModel(JNIEnv *env,
                           jobject jmodel_,
                           const char *textureUri_,
                           const std::size_t numVertices_,
                           const std::size_t numFaces_,
                           const void *positions_,
                           const void *texCoords_,
                           const void *normals_,
                           const void *colors_,
                           const TAK::Engine::Model::WindingOrder &windingOrder_,
                           const TAK::Engine::Model::DrawMode &drawMode_,
                           const TAK::Engine::Feature::Envelope2 &aabb_,
                           const TAK::Engine::Model::VertexDataLayout &layout_,
                           const TAK::Engine::Port::DataType &indexType_,
                           const std::size_t numIndices_,
                           const void *indices_,
                           const std::size_t indexOffset_) NOTHROWS :
    jmodel(env->NewGlobalRef(jmodel_)),
    numVertices(numVertices_),
    numFaces(numFaces_),
    positionVertices(positions_),
    texCoord0Vertices(texCoords_),
    normalVertices(normals_),
    colorVertices(colors_),
    windingOrder(windingOrder_),
    drawMode(drawMode_),
    aabb(aabb_),
    layout(layout_),
    indexed(true),
    indexOffset(indexOffset_),
    indices(indices_),
    indexType(indexType_)
{
    Material material;
    material.propertyType = Material::TEPT_Diffuse;
    material.color = 0xFFFFFFFFu;
    material.textureUri = textureUri_;
    materials.push_back(material);
}

ManagedModel::~ManagedModel() NOTHROWS
{
    if(jmodel) {
        LocalJNIEnv env;
        if(env.valid()) {
            env->DeleteGlobalRef(jmodel);
        }
        jmodel = NULL;
    }
}

bool ManagedModel::valid() NOTHROWS
{
    return !initErr;
}
std::size_t ManagedModel::getNumMaterials() const NOTHROWS
{
    return materials.size();
}
TAKErr ManagedModel::getMaterial(Material *value, const std::size_t index) const NOTHROWS
{
    if(index >= materials.size())
        return TE_BadIndex;
    *value = materials[index];
    return TE_Ok;
}
std::size_t ManagedModel::getNumVertices() const NOTHROWS
{
    return numVertices;
}
std::size_t ManagedModel::getNumFaces() const NOTHROWS
{
    return numFaces;
}
bool ManagedModel::isIndexed() const NOTHROWS
{
    return indexed;
}
TAKErr ManagedModel::getPosition(Point2<double> *value, const std::size_t index) const NOTHROWS
{
    LocalJNIEnv env;
    if(!env.valid())
        return TE_Err;
    JModelInterface &jiface = java_interface(env);
    if(!jiface.valid)
        return TE_Err;
    jobject jxyz = env->NewObject(jiface.PointD_clazz, jiface.PointD_ctor__DDD, 0, 0, 0);
    if(env->ExceptionCheck())
        return TE_Err;
    if(!jxyz)
        return TE_Err;
    env->CallVoidMethod(jmodel, jiface.getPosition, (jint)index, jxyz);
    if(env->ExceptionCheck())
        return TE_Err;
    value->x = env->GetDoubleField(jxyz, pointD_x);
    value->y = env->GetDoubleField(jxyz, pointD_y);
    value->z = env->GetDoubleField(jxyz, pointD_z);
    return TE_Ok;
}
TAKErr ManagedModel::getTextureCoordinate(Point2<float> *value, const VertexAttribute texCoord, const std::size_t index) const NOTHROWS
{
    LocalJNIEnv env;
    if(!env.valid())
        return TE_Err;
    // XXX -
    if(!(layout.attributes&texCoord))
        return TE_InvalidArg;
    JModelInterface &jiface = java_interface(env);
    if(!jiface.valid)
        return TE_Err;
    jobject jxyz = env->NewObject(jiface.PointD_clazz, jiface.PointD_ctor__DDD, 0, 0, 0);
    if(env->ExceptionCheck())
        return TE_Err;
    if(!jxyz)
        return TE_Err;
    env->CallVoidMethod(jmodel, jiface.getTextureCoordinate, 1, (jint)index, jxyz);
    if(env->ExceptionCheck())
        return TE_Err;
    value->x = env->GetDoubleField(jxyz, pointD_x);
    value->y = env->GetDoubleField(jxyz, pointD_y);
    return TE_Ok;
}
TAKErr ManagedModel::getNormal(Point2<float> *value, const std::size_t index) const NOTHROWS
{
    LocalJNIEnv env;
    if(!env.valid())
        return TE_Err;
    JModelInterface &jiface = java_interface(env);
    if(!jiface.valid)
        return TE_Err;
    jobject jxyz = env->NewObject(jiface.PointD_clazz, jiface.PointD_ctor__DDD, 0, 0, 0);
    if(env->ExceptionCheck())
        return TE_Err;
    if(!jxyz)
        return TE_Err;
    env->CallVoidMethod(jmodel, jiface.getNormal, (jint)index, jxyz);
    if(env->ExceptionCheck())
        return TE_Err;
    value->x = env->GetDoubleField(jxyz, pointD_x);
    value->y = env->GetDoubleField(jxyz, pointD_y);
    value->z = env->GetDoubleField(jxyz, pointD_z);
    return TE_Ok;
}
TAKErr ManagedModel::getColor(unsigned int *value, const std::size_t index) const NOTHROWS
{
    LocalJNIEnv env;
    if(!env.valid())
        return TE_Err;
    JModelInterface &jiface = java_interface(env);
    if(!jiface.valid)
        return TE_Err;
    *value = env->CallIntMethod(jmodel, jiface.getColor, (jint)index);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}
TAKErr ManagedModel::getVertexAttributeType(DataType *value, const unsigned int attr) const NOTHROWS
{
    switch(attr) {
#define VERTEX_ATTR_CASE(teva, vao) \
    case teva : \
        *value = layout.vao.type; \
        break;
        VERTEX_ATTR_CASE(TEVA_Position, position)
        VERTEX_ATTR_CASE(TEVA_TexCoord0, texCoord0)
        VERTEX_ATTR_CASE(TEVA_TexCoord1, texCoord1)
        VERTEX_ATTR_CASE(TEVA_TexCoord2, texCoord2)
        VERTEX_ATTR_CASE(TEVA_TexCoord3, texCoord3)
        VERTEX_ATTR_CASE(TEVA_TexCoord4, texCoord4)
        VERTEX_ATTR_CASE(TEVA_TexCoord5, texCoord5)
        VERTEX_ATTR_CASE(TEVA_TexCoord6, texCoord6)
        VERTEX_ATTR_CASE(TEVA_TexCoord7, texCoord7)
        VERTEX_ATTR_CASE(TEVA_Normal, normal)
        VERTEX_ATTR_CASE(TEVA_Color, color)
#undef VERTEX_ATTR_CASE
        default :
            return TE_InvalidArg;
    }
    if(!(attr&layout.attributes))
        return TE_InvalidArg;
    return TE_Ok;
}
TAKErr ManagedModel::getIndexType(DataType *value) const NOTHROWS
{
    if(!indexed)
        return TE_IllegalState;
    *value = indexType;
    return TE_Ok;
}
TAKErr ManagedModel::getIndex(std::size_t *value, const std::size_t index) const NOTHROWS
{
    LocalJNIEnv env;
    if(!env.valid())
        return TE_Err;
    JModelInterface &jiface = java_interface(env);
    if(!jiface.valid)
        return TE_Err;
    *value = env->CallIntMethod(jmodel, jiface.getIndex, (jint)index);
    if(env->ExceptionCheck())
        return TE_Err;
    return TE_Ok;
}
const void *ManagedModel::getIndices() const NOTHROWS
{
    return indices;
}
std::size_t ManagedModel::getIndexOffset() const NOTHROWS
{
    return indexOffset;
}
std::size_t ManagedModel::getNumIndices() const NOTHROWS
{
    return numIndices;
}
TAKErr ManagedModel::getVertices(const void **value, const std::size_t attr) const NOTHROWS
{
    switch(attr) {
        case TEVA_Position :
            *value = positionVertices;
            break;
        case TEVA_TexCoord0 :
            *value = texCoord0Vertices;
            break;
        case TEVA_TexCoord1 :
            *value = texCoord1Vertices;
            break;
        case TEVA_TexCoord2 :
            *value = texCoord2Vertices;
            break;
        case TEVA_TexCoord3 :
            *value = texCoord3Vertices;
            break;
        case TEVA_TexCoord4 :
            *value = texCoord4Vertices;
            break;
        case TEVA_TexCoord5 :
            *value = texCoord5Vertices;
            break;
        case TEVA_TexCoord6 :
            *value = texCoord6Vertices;
            break;
        case TEVA_TexCoord7 :
            *value = texCoord7Vertices;
            break;
        case TEVA_Normal :
            *value = normalVertices;
            break;
        case TEVA_Color :
            *value = colorVertices;
            break;
        default :
            return TE_InvalidArg;
    }
    if(!(attr&layout.attributes))
        return TE_InvalidArg;
    return TE_Ok;
}
WindingOrder ManagedModel::getFaceWindingOrder() const NOTHROWS
{
    return windingOrder;
}
DrawMode ManagedModel::getDrawMode() const NOTHROWS
{
    return drawMode;
}
const Envelope2 &ManagedModel::getAABB() const NOTHROWS
{
    return aabb;
}
const VertexDataLayout &ManagedModel::getVertexDataLayout() const NOTHROWS
{
    return layout;
}
std::size_t ManagedModel::getNumBuffers() const NOTHROWS
{
    return 0u;
}
TAKErr ManagedModel::getBuffer(const MemBuffer2 **value, const std::size_t idx) const NOTHROWS
{
    return TE_InvalidArg;
}


namespace
{
    JModelInterface::JModelInterface(JNIEnv *env) :
        clazz(NULL),
        getIndex(0),
        getPosition(0),
        getTextureCoordinate(0),
        getNormal(0),
        getColor(0),
        PointD_clazz(NULL),
        PointD_ctor__DDD(0),
        valid(false)
    {
        do {
            clazz = ATAKMapEngineJNI_findClass(env, "com/atakmap/map/layer/model/Mesh");
            if(!clazz)
                break;
            getIndex = env->GetMethodID(clazz, "getIndex", "(I)I");
            if(!getIndex)
                break;
            getPosition = env->GetMethodID(clazz, "getPosition", "(ILcom/atakmap/math/PointD;)V");
            if(!getPosition)
                break;
            getTextureCoordinate = env->GetMethodID(clazz, "getTextureCoordinate", "(IILcom/atakmap/math/PointD;)V");
            if(!getTextureCoordinate)
                break;
            getNormal = env->GetMethodID(clazz, "getNormal", "(ILcom/atakmap/math/PointD;)V");
            if(!getNormal)
                break;
            getColor = env->GetMethodID(clazz, "getColor", "(I)I");
            if(!getColor)
                break;

            PointD_clazz = ATAKMapEngineJNI_findClass(env, "com/atakmap/math/PointD");
            if(!PointD_clazz)
                break;
            PointD_ctor__DDD = env->GetMethodID(PointD_clazz, "<init>", "(DDD)V");
            if(!PointD_ctor__DDD)
                break;

            valid = true;
        } while(false);
    }

    JModelInterface &java_interface(JNIEnv *env)
    {
        static JModelInterface iface(env);
        return iface;
    }
}
