#include "model/SceneBuilder.h"

#include <algorithm>
#include <list>
#include <vector>

#include "model/SceneGraphBuilder.h"
#include "util/Memory.h"

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

namespace
{
    class SceneImpl : public Scene {
    public:
        SceneImpl(const bool direct) NOTHROWS;

        SceneImpl(std::vector<std::shared_ptr<const Mesh>> &&meshes,
            std::unique_ptr<Envelope2> &&aabb,
            SceneNodePtr &&rootNode,
            const bool direct) :
            meshes(std::move(meshes)),
            aabb(std::move(aabb)),
            rootNode(std::move(rootNode)),
            direct(direct) { }

        ~SceneImpl() NOTHROWS override;
    public:
        SceneNode &getRootNode() const NOTHROWS override;
        const Envelope2 &getAABB() const NOTHROWS override;
        unsigned int getProperties() const NOTHROWS override;
        virtual std::size_t getNumMeshes() const NOTHROWS;
    public:
        std::vector<std::shared_ptr<const Mesh>> meshes;
        std::map<std::size_t, std::shared_ptr<const Mesh>> instancedMeshes;
        std::unique_ptr<Envelope2> aabb;
        SceneNodePtr rootNode;
        bool direct;
    };

    struct DeferredNode
    {
        const SceneNode *parent {nullptr};
        bool hasTransform {false};
        Matrix2 transform;
        std::size_t instanceId {0};
    };

    class BuilderSceneImpl : public SceneImpl {
    public:
        BuilderSceneImpl(const bool direct) NOTHROWS
            : SceneImpl(direct), graph(nullptr), node(nullptr), nodeDepth(0) { 
            node = &graph.getRoot();
        }
        BuilderSceneImpl(const TAK::Engine::Math::Matrix2 &rootTransform, const bool direct) NOTHROWS
            : SceneImpl(direct), graph(&rootTransform), node(nullptr), nodeDepth(0) {
            node = &graph.getRoot();
        }
        ~BuilderSceneImpl() NOTHROWS override;
        SceneGraphBuilder graph;
        const SceneNode *node;
        int nodeDepth;
        std::list<DeferredNode> deferredNodes;

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
#undef XFORM_AND_UPDATE

        aabb = xformed;
        return code;
    }
}

SceneBuilder::SceneBuilder(const bool direct) NOTHROWS :
    impl(new BuilderSceneImpl(direct), Memory_deleter_const<Scene, BuilderSceneImpl>)
{}
SceneBuilder::SceneBuilder(const TAK::Engine::Math::Matrix2 &rootTransform, const bool direct) NOTHROWS
    : impl(new BuilderSceneImpl(rootTransform, direct), Memory_deleter_const<Scene, BuilderSceneImpl>)
{}
SceneBuilder::~SceneBuilder() NOTHROWS
{}
TAKErr SceneBuilder::addMesh(MeshPtr_const &&mesh, const Matrix2 *localFrame) NOTHROWS
{
    return addMesh(std::shared_ptr<const Mesh>(std::move(mesh)), localFrame);
}
TAKErr SceneBuilder::addMesh(MeshPtr &&mesh, const Matrix2 *localFrame) NOTHROWS
{
    return addMesh(MeshPtr_const(mesh.release(), mesh.get_deleter()), localFrame);
}
TAKErr SceneBuilder::addMesh(const std::shared_ptr<Mesh> &mesh, const Matrix2 *localFrame) NOTHROWS
{
    return addMesh(std::shared_ptr<const Mesh>(mesh), localFrame);
}
TAKErr SceneBuilder::addMesh(const std::shared_ptr<const Mesh> &mesh, const Matrix2 *localFrame) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!impl)
        return TE_IllegalState;
    if (!mesh)
        return TE_InvalidArg;
    BuilderSceneImpl &scene = *static_cast<BuilderSceneImpl *>(impl.get());
    const size_t meshIdx = scene.meshes.size();
    scene.meshes.push_back(mesh);
    SceneNode *ignored;
    code = scene.graph.addNode(&ignored, *scene.node, localFrame, mesh->getAABB(), mesh);
    TE_CHECKRETURN_CODE(code);

    Envelope2 meshaabb = mesh->getAABB();
    code = transform(meshaabb, localFrame);
    if (!scene.aabb.get()) {
        scene.aabb.reset(new Envelope2(meshaabb));
    } else {
        scene.aabb->minX = std::min(meshaabb.minX, scene.aabb->minX);
        scene.aabb->minY = std::min(meshaabb.minY, scene.aabb->minY);
        scene.aabb->minZ = std::min(meshaabb.minZ, scene.aabb->minZ);
        scene.aabb->maxX = std::max(meshaabb.maxX, scene.aabb->maxX);
        scene.aabb->maxY = std::max(meshaabb.maxY, scene.aabb->maxY);
        scene.aabb->maxZ = std::max(meshaabb.maxZ, scene.aabb->maxZ);
    }
    return code;
}

TAKErr SceneBuilder::addMesh(const std::size_t instanceId, const Matrix2 *localFrame) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!impl)
        return TE_IllegalState;
    if (instanceId == SceneNode::InstanceID_None)
        return TE_InvalidArg;
    BuilderSceneImpl &scene = *static_cast<BuilderSceneImpl *>(impl.get());
    auto entry = scene.instancedMeshes.find(instanceId);
    if (entry != scene.instancedMeshes.end()) {
        // the instance mesh data is already specified, add immediately
        return addMesh(entry->second, localFrame);
    } else {
        // the mesh data is not specified, defer adding to graph until build complete
        DeferredNode spec;
        spec.instanceId = instanceId;
        spec.hasTransform = !!localFrame;
        if (localFrame)
            spec.transform.set(*localFrame);
        spec.parent = scene.node;
        scene.deferredNodes.push_back(spec);
    }

    return code;
}
TAKErr SceneBuilder::addMesh(MeshPtr_const &&mesh, const std::size_t instanceId, const Matrix2 *localFrame) NOTHROWS
{
    return addMesh(std::shared_ptr<const Mesh>(std::move(mesh)), instanceId, localFrame);
}
TAKErr SceneBuilder::addMesh(MeshPtr &&mesh, const std::size_t instanceId, const Matrix2 *localFrame) NOTHROWS
{
    return addMesh(MeshPtr_const(mesh.release(), mesh.get_deleter()), instanceId, localFrame);
}
TAKErr SceneBuilder::addMesh(const std::shared_ptr<Mesh> &mesh, const std::size_t instanceId, const Matrix2 *localFrame) NOTHROWS
{
    return addMesh(std::shared_ptr<const Mesh>(mesh), instanceId, localFrame);
}
TAKErr SceneBuilder::addMesh(const std::shared_ptr<const Mesh> &mesh, const std::size_t instanceId, const Matrix2 *localFrame) NOTHROWS
{
    TAKErr code(TE_Ok);
    if (!impl)
        return TE_IllegalState;
    if (!mesh)
        return TE_InvalidArg;

    BuilderSceneImpl &scene = *static_cast<BuilderSceneImpl *>(impl.get());
    if (instanceId != SceneNode::InstanceID_None) {
        // check if mesh data for instance ID already specified
        auto existing = scene.instancedMeshes.find(instanceId);
        if (existing != scene.instancedMeshes.end())
            return TE_IllegalState;

        scene.instancedMeshes[instanceId] = mesh;
    }
    
    scene.meshes.push_back(mesh);
    SceneNode *ignored;
    code = scene.graph.addNode(&ignored, *scene.node, localFrame, mesh->getAABB(), mesh);
    TE_CHECKRETURN_CODE(code);

    Envelope2 meshaabb = mesh->getAABB();
    code = transform(meshaabb, localFrame);
    if (!scene.aabb.get()) {
        scene.aabb.reset(new Envelope2(meshaabb));
    } else {
        scene.aabb->minX = std::min(meshaabb.minX, scene.aabb->minX);
        scene.aabb->minY = std::min(meshaabb.minY, scene.aabb->minY);
        scene.aabb->minZ = std::min(meshaabb.minZ, scene.aabb->minZ);
        scene.aabb->maxX = std::max(meshaabb.maxX, scene.aabb->maxX);
        scene.aabb->maxY = std::max(meshaabb.maxY, scene.aabb->maxY);
        scene.aabb->maxZ = std::max(meshaabb.maxZ, scene.aabb->maxZ);
    }
    return code;
}

TAKErr SceneBuilder::push(const Matrix2 *localFrame) NOTHROWS
{
    if (!impl)
        return TE_IllegalState;
    
    BuilderSceneImpl &scene = *static_cast<BuilderSceneImpl *>(impl.get());
    
    SceneNode *newNode;
    TAKErr code = scene.graph.addNode(&newNode, *scene.node, localFrame);
    TE_CHECKRETURN_CODE(code);

    scene.node = newNode;
    scene.nodeDepth++;

    return code;
}

TAKErr SceneBuilder::pop() NOTHROWS
{
    if (!impl)
        return TE_IllegalState;

    BuilderSceneImpl &scene = *static_cast<BuilderSceneImpl *>(impl.get());

    if (scene.node->isRoot())
        return TE_IllegalState;

    TAKErr code = scene.node->getParent(&scene.node);
    TE_CHECKRETURN_CODE(code);
    if (!scene.nodeDepth)
        return TE_IllegalState;
    scene.nodeDepth--;
    return code;
}

int SceneBuilder::nodeDepth() const NOTHROWS {
    return 0;
}

TAKErr SceneBuilder::build(ScenePtr &value) NOTHROWS
{
    if (!impl.get())
        return TE_IllegalState;

    BuilderSceneImpl &scene = *static_cast<BuilderSceneImpl *>(impl.get());
    if (!scene.getNumMeshes())
        return TE_IllegalState;

    // insert all deferred nodes
    TAKErr code(TE_Ok);
    for (auto deferred = scene.deferredNodes.begin(); deferred != scene.deferredNodes.end(); deferred++) {
        DeferredNode node = *deferred;
        auto mesh = scene.instancedMeshes.find(node.instanceId);
        if (mesh == scene.instancedMeshes.end())
            return TE_IllegalState;
        SceneNode *ignored;
        code = scene.graph.addNode(&ignored, *node.parent, node.hasTransform ? &node.transform : nullptr, mesh->second->getAABB(), mesh->second, node.instanceId);
        TE_CHECKBREAK_CODE(code);
    }
    TE_CHECKRETURN_CODE(code);
    scene.deferredNodes.clear();

    SceneNodePtr rootNode(nullptr, nullptr);
    code = scene.graph.build(rootNode);
    TE_CHECKRETURN_CODE(code);

    // move to new instance dropping builder specifics
    value = ScenePtr(new(std::nothrow) SceneImpl(std::move(scene.meshes),
                                   std::move(scene.aabb),
                                   std::move(rootNode),
                                   scene.direct), 
        Memory_deleter_const<Scene, SceneImpl>);

    if (!value)
        return TE_OutOfMemory;

    impl.reset();
    return TE_Ok;
}

ENGINE_API TAKErr TAK::Engine::Model::SceneBuilder_build(ScenePtr &value, SceneNodePtr &&root, const bool direct) NOTHROWS
{
    if (!root.get())
        return TE_InvalidArg;

    std::unique_ptr<SceneImpl> retval(new SceneImpl(true));
    retval->rootNode = std::move(root);
    retval->aabb.reset(new Envelope2(retval->rootNode->getAABB()));
    value = ScenePtr(retval.release(), Memory_deleter_const<Scene, SceneImpl>);
    return TE_Ok;
}

namespace
{
    SceneImpl::SceneImpl(const bool direct) NOTHROWS
        : rootNode(nullptr, nullptr),
        direct(direct)
    {}
    SceneImpl::~SceneImpl() NOTHROWS
    {}
    SceneNode &SceneImpl::getRootNode() const NOTHROWS
    {
        return *rootNode;
    }
    const Envelope2 &SceneImpl::getAABB() const NOTHROWS
    {
        return *aabb;
    }
    unsigned int SceneImpl::getProperties() const NOTHROWS
    {
        return direct ? Properties::DirectMesh|Properties::DirectSceneGraph : 0u;
    }
    std::size_t SceneImpl::getNumMeshes() const NOTHROWS
    {
        return meshes.size();
    }

    BuilderSceneImpl::~BuilderSceneImpl() NOTHROWS
    {}
}
