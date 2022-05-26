#include "model/SceneGraphBuilder.h"

#include <list>

#include "port/STLListAdapter.h"
#include "util/Memory.h"

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

namespace
{
    class SceneNodeImpl : public SceneNode
    {
    public :
        SceneNodeImpl(SceneNodeImpl *parent, const Matrix2 *rootLocalFrame) NOTHROWS;
        SceneNodeImpl(SceneNodeImpl *parent, const Matrix2 *rootLocalFrame, const Envelope2 &aabb, const std::shared_ptr<const Mesh> &mesh, const std::size_t instanceId) NOTHROWS;
        ~SceneNodeImpl() NOTHROWS override;
    public :
        bool isRoot() const NOTHROWS override;
        TAKErr getParent(const SceneNode **value) const NOTHROWS override;
        const Matrix2 *getLocalFrame() const NOTHROWS override;
        TAKErr getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr &value) const NOTHROWS override;
        bool hasChildren() const NOTHROWS override;
        bool hasMesh() const NOTHROWS override;
        const Envelope2 &getAABB() const NOTHROWS override;
        std::size_t getNumLODs() const NOTHROWS override;
        TAKErr loadMesh(std::shared_ptr<const Mesh> &value, const std::size_t lod = 0u, ProcessingCallback *callback = nullptr) NOTHROWS override;
        TAKErr getLevelOfDetail(std::size_t *value, const std::size_t lodIdx) const NOTHROWS override;
        TAKErr getLODIndex(std::size_t *value, const double clod, const int round = 0) const NOTHROWS override;
        TAKErr getInstanceID(std::size_t *value, const std::size_t lodIdx) const NOTHROWS override;
        bool hasSubscene() const NOTHROWS override;
        TAKErr getSubsceneInfo(const SceneInfo** result) NOTHROWS override;
        bool hasLODNode() const NOTHROWS override;
        TAKErr getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS override;

    public :
        std::list<std::shared_ptr<SceneNode>> children;
        std::unique_ptr<Envelope2> aabb;
        SceneNodeImpl *parent;
        std::size_t instanceId;
    private :
        std::shared_ptr<const Mesh> mesh;
        std::unique_ptr<Matrix2> localFrame;
        Matrix2 accummulatedTransform;
    };

    TAKErr transform(Envelope2 &aabb, const Matrix2 *xform) NOTHROWS
    {
        TAKErr code(TE_Ok);
        if (!xform)  
            return code;

        Point2<double> p;
        code = xform->transform(&p, Point2<double>(aabb.minX, aabb.minY, aabb.minZ));
        TE_CHECKRETURN_CODE(code);
        Envelope2 xformed(p.x, p.y, p.z, p.x, p.y, p.z);

#define XFORM_AND_UPDATE(a, b, c) \
        code = xform->transform(&p, Point2<double>(aabb.a, aabb.b, aabb.c)); \
        TE_CHECKRETURN_CODE(code); \
        if (p.x < xformed.minX)         xformed.minX = p.x; \
        else if (p.x > xformed.maxX)    xformed.maxX = p.x; \
        if (p.y < xformed.minY)         xformed.minY = p.y; \
        else if (p.y > xformed.maxY)    xformed.maxY = p.y; \
        if (p.z < xformed.minZ)         xformed.minZ = p.z; \
        else if (p.z > xformed.maxZ)    xformed.maxZ = p.z; 

        XFORM_AND_UPDATE(maxX, minY, minZ);
        XFORM_AND_UPDATE(maxX, maxY, minZ);
        XFORM_AND_UPDATE(minX, maxY, minZ);
        XFORM_AND_UPDATE(minX, minY, maxZ);
        XFORM_AND_UPDATE(maxX, minY, maxZ);
        XFORM_AND_UPDATE(maxX, maxY, maxZ);
        XFORM_AND_UPDATE(minX, maxY, maxZ);

        aabb = xformed;
        return code;
    }

    void updateBounds(SceneNodeImpl &node, const Envelope2 &aabb) NOTHROWS
    {
        if (node.aabb.get()) {
            node.aabb->minX = std::min(node.aabb->minX, aabb.minX);
            node.aabb->minY = std::min(node.aabb->minY, aabb.minY);
            node.aabb->minZ = std::min(node.aabb->minZ, aabb.minZ);
            node.aabb->maxX = std::max(node.aabb->maxX, aabb.maxX);
            node.aabb->maxY = std::max(node.aabb->maxY, aabb.maxY);
            node.aabb->maxZ = std::max(node.aabb->maxZ, aabb.maxZ);
        } else {
            node.aabb.reset(new Envelope2(aabb));
        }

        if (node.parent) {
            Envelope2 recurseAabb(aabb);
            transform(recurseAabb, node.parent->getLocalFrame());
            updateBounds(*node.parent, recurseAabb);
        }
    }
}

SceneGraphBuilder::SceneGraphBuilder(const Matrix2 *rootLocalFrame) NOTHROWS :
    root(new SceneNodeImpl(nullptr, rootLocalFrame), Memory_deleter_const<SceneNode, SceneNodeImpl>)
{
    nodes.insert(root.get());
}
SceneGraphBuilder::SceneGraphBuilder(const Envelope2 &aabb, const Matrix2 *rootLocalFrame, const std::shared_ptr<const Mesh> &mesh) NOTHROWS :
    root(new SceneNodeImpl(nullptr, rootLocalFrame, aabb, mesh, SceneNode::InstanceID_None), Memory_deleter_const<SceneNode, SceneNodeImpl>)
{
    nodes.insert(root.get());
}
SceneGraphBuilder::SceneGraphBuilder(const Envelope2 &aabb, const Matrix2 *rootLocalFrame, MeshPtr_const &&mesh) NOTHROWS :
    root(new SceneNodeImpl(nullptr, rootLocalFrame, aabb, std::move(mesh), SceneNode::InstanceID_None), Memory_deleter_const<SceneNode, SceneNodeImpl>)
{
    nodes.insert(root.get());
}
SceneGraphBuilder::~SceneGraphBuilder() NOTHROWS
{}
SceneNode &SceneGraphBuilder::getRoot() const NOTHROWS
{
    return *root;
}
TAKErr SceneGraphBuilder::addNode(SceneNode **value, const SceneNode &parent, const Matrix2 *localFrame) NOTHROWS
{
    TAKErr code(TE_Ok);
    // make sure the graph has not yet been built
    if (!root.get())
        return TE_IllegalState;
    // ensure the specified parent is in the build graph
    std::set<const SceneNode *>::iterator entry;
    entry = nodes.find(&parent);
    if (entry == nodes.end())
        return TE_InvalidArg;
    // cast the parent
    auto &parentImpl = const_cast<SceneNodeImpl &>(static_cast<const SceneNodeImpl &>(parent));
    // create the new child
    SceneNodePtr child(new SceneNodeImpl(&parentImpl, localFrame), Memory_deleter_const<SceneNode, SceneNodeImpl>);
    SceneNode *valueResult = child.get();
    // add the child to the parent
    nodes.insert(child.get());
    parentImpl.children.push_back(std::move(child));
    if (value)
        *value = valueResult;
    return code;
}
TAKErr SceneGraphBuilder::addNode(SceneNode **value, const SceneNode &parent, const Matrix2 *localFrame, const Envelope2 &aabb, MeshPtr_const &&mesh) NOTHROWS
{
    return addNode(value, parent, localFrame, aabb, std::move(mesh), SceneNode::InstanceID_None);
}
TAKErr SceneGraphBuilder::addNode(SceneNode **value, const SceneNode &parent, const Matrix2 *localFrame, const Envelope2 &aabb, const std::shared_ptr<const Mesh> &mesh) NOTHROWS
{
    return addNode(value, parent, localFrame, aabb, mesh, SceneNode::InstanceID_None);
}
TAKErr SceneGraphBuilder::addNode(SceneNode **value, const SceneNode &parent, const Matrix2 *localFrame, const Envelope2 &aabb, const std::shared_ptr<const Mesh> &mesh, const std::size_t instanceId) NOTHROWS
{
    TAKErr code(TE_Ok);
    // make sure the graph has not yet been built
    if (!root.get())
        return TE_IllegalState;
    // ensure the specified parent is in the build graph
    std::set<const SceneNode *>::iterator entry;
    entry = nodes.find(&parent);
    if (entry == nodes.end())
        return TE_InvalidArg;
    // cast the parent
    auto &parentImpl = const_cast<SceneNodeImpl &>(static_cast<const SceneNodeImpl &>(parent));
    // create the new child
    SceneNodePtr child(new SceneNodeImpl(&parentImpl, localFrame, aabb, mesh, instanceId), Memory_deleter_const<SceneNode, SceneNodeImpl>);
    SceneNode *valueResult = child.get();
    // add the child to the parent
    nodes.insert(child.get());
    parentImpl.children.push_back(std::move(child));
    if (value)
        *value = valueResult;
    return code;
}
TAKErr SceneGraphBuilder::build(SceneNodePtr &value) NOTHROWS
{
    if (!root.get())
        return TE_IllegalState;
    value = std::move(root);
    nodes.clear();
    return TE_Ok;
}

namespace
{
    SceneNodeImpl::SceneNodeImpl(SceneNodeImpl *parent_, const Matrix2 *localFrame_) NOTHROWS :
    parent(parent_),
        localFrame(localFrame_ ? new Matrix2(*localFrame_) : nullptr),
        instanceId(SceneNode::InstanceID_None)
    {}
    SceneNodeImpl::SceneNodeImpl(SceneNodeImpl *parent_, const Matrix2 *localFrame_, const Envelope2 &aabb_,  const std::shared_ptr<const Mesh> &mesh_, const std::size_t instanceId_) NOTHROWS :
        parent(parent_),
        localFrame(localFrame_ ? new Matrix2(*localFrame_) : nullptr),
        mesh(mesh_),
        instanceId(instanceId_)
    {
        updateBounds(*this, aabb_);
    }
    SceneNodeImpl::~SceneNodeImpl() NOTHROWS
    {
    }
    bool SceneNodeImpl::isRoot() const NOTHROWS
    {
        return !!parent;
    }
    TAKErr SceneNodeImpl::getParent(const SceneNode **value) const NOTHROWS
    {
        if (!parent)
            return TE_Ok;
        *value = parent;
        return TE_Ok;
    }
    const Matrix2 *SceneNodeImpl::getLocalFrame() const NOTHROWS
    {
        return localFrame.get();
    }
    TAKErr SceneNodeImpl::getChildren(Collection<std::shared_ptr<SceneNode>>::IteratorPtr &value) const NOTHROWS
    {
        STLListAdapter<std::shared_ptr<SceneNode>> adapter(const_cast<std::list<std::shared_ptr<SceneNode>> &>(children));
        return adapter.iterator(value);
    }
    bool SceneNodeImpl::hasChildren() const NOTHROWS
    {
        return !children.empty();
    }
    bool SceneNodeImpl::hasMesh() const NOTHROWS
    {
        return !!mesh.get();
    }
    const Envelope2 &SceneNodeImpl::getAABB() const NOTHROWS
    {
        return *aabb;
    }
    std::size_t SceneNodeImpl::getNumLODs() const NOTHROWS
    {
        return 1u;
    }
    TAKErr SceneNodeImpl::loadMesh(std::shared_ptr<const Mesh> &value, const std::size_t lod, ProcessingCallback *callback) NOTHROWS
    {
        if (lod != 0u)
            return TE_InvalidArg;
        if (!mesh.get())
            return TE_IllegalState;
        value = mesh;
        return TE_Ok;
    }
    TAKErr SceneNodeImpl::getLevelOfDetail(std::size_t *value, const std::size_t lodIdx) const NOTHROWS
    {
        if (lodIdx != 0u)
            return TE_InvalidArg;
        *value = 0u;
        return TE_Ok;
    }
    TAKErr SceneNodeImpl::getLODIndex(std::size_t *value, const double clod, const int round) const NOTHROWS
    {
        // only single LOD
        *value = 0u;
        return TE_Ok;
    }
    TAKErr SceneNodeImpl::getInstanceID(std::size_t *value, const std::size_t lodIdx) const NOTHROWS
    {
        if (lodIdx != 0u)
            return TE_InvalidArg;
        *value = instanceId;
        return TE_Ok;
    }
    bool SceneNodeImpl::hasSubscene() const NOTHROWS
    {
        return false;
    }
    TAKErr SceneNodeImpl::getSubsceneInfo(const SceneInfo** result) NOTHROWS
    {
        return TE_IllegalState;
    }
    bool SceneNodeImpl::hasLODNode() const NOTHROWS {
        return false;
    }

    TAKErr SceneNodeImpl::getLODNode(std::shared_ptr<SceneNode>& value, const std::size_t lodIdx) NOTHROWS {
        return TE_Unsupported;
    }
}
