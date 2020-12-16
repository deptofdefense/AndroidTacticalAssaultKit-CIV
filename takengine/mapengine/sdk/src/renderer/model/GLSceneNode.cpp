#include "renderer/model/GLSceneNode.h"

#include "core/ProjectionFactory3.h"
#include "math/Utils.h"
#include "model/MeshTransformer.h"
#include "renderer/model/GLMesh.h"
#include "thread/Lock.h"

#define SUPPORT_ECEF_RENDER 1

using namespace TAK::Engine::Renderer::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

namespace
{
    int selectLodIndex(const MapSceneModel2 &scene, const double gsd, const SceneNode &node, const SceneInfo &info, const Envelope2 &mbb, const bool checkPrefetch) NOTHROWS
    {
        // XXX - waiting on perspective
        //view.scratch.pointD.x = (mbb.minX+mbb.maxX)/2d;
        //view.scratch.pointD.y = (mbb.minY+mbb.maxY)/2d;
        //view.scratch.pointD.z = (mbb.minZ+mbb.maxZ)/2d;
        //final double radius = 0d; // XXX - compute radius in meters
        //final double gsd = GLMapView.estimateResolution(view, view.scratch.pointD, radius, null);

        if (gsd > info.minDisplayResolution || gsd < info.maxDisplayResolution)
            return -1;

        int targetLod;
        if (node.getNumLODs() > 1u) {
            const double scale = scene.gsd / info.resolution;
            double lod = log(scale) / log(2);

            // XXX - remove hack here once perspective is implemented and better
            //       estimated GSD is computed. if prefetching, halve the LOD

            if (checkPrefetch) {
                bool intersects;
                if (MapSceneModel2_intersects(&intersects, scene, mbb.minX, mbb.minY, mbb.minZ, mbb.maxX, mbb.maxY, mbb.maxZ) == TE_Ok && !intersects)
                    lod = lod * 2;
            }

            std::size_t idx;
            if (node.getLODIndex(&idx, lod, 0) == TE_Ok)
                targetLod = static_cast<int>(idx);
            else
                targetLod = 0;
        } else {
            targetLod = 0;
        }

        return targetLod;
    }

    int selectLodIndex(const GLMapView2 &view, const SceneNode &node, const SceneInfo &info, const Envelope2 &mbb, const bool checkPrefetch) NOTHROWS
    {
        return selectLodIndex(view.scene, view.drawMapResolution, node, info, mbb, checkPrefetch);
    }

    void utm2lla(SceneInfo &info, const Envelope2 &aabbLCS) NOTHROWS
    {
        // obtain the AABB in the WCS
        Envelope2 aabb(aabbLCS);
        {
            MeshTransformOptions srcOpts;
            srcOpts.srid = info.srid;
            srcOpts.localFrame = Matrix2Ptr(const_cast<Matrix2 *>(info.localFrame.get()), Memory_leaker_const<Matrix2>);

            MeshTransformOptions dstOpts;
            dstOpts.srid = info.srid;

            Mesh_transform(&aabb, aabb, srcOpts, dstOpts);
        }

        // get center of AABB
        const TAK::Engine::Math::Point2<double> cxyz((aabb.minX + aabb.maxX) / 2.0, (aabb.minY + aabb.maxY) / 2.0, (aabb.minZ + aabb.maxZ) / 2.0);
        // compute radius 
        const double radius = atakmap::math::max((aabb.maxX - aabb.minX) / 2.0, (aabb.maxY - aabb.minY) / 2.0, (aabb.maxZ - aabb.minZ) / 2.0);

        // compute convergence angle from center to +y at the radius
        Projection2Ptr proj(nullptr, nullptr);
        ProjectionFactory3_create(proj, info.srid);

        GeoPoint2 cgeo;
        proj->inverse(&cgeo, cxyz);
        GeoPoint2 cup;
        proj->inverse(&cup, TAK::Engine::Math::Point2<double>(cxyz.x, cxyz.y + radius, cxyz.z));

        const double bearing = GeoPoint2_bearing(cgeo, cup, false);

        // reconfigure info
        info.srid = 4326;

        // construct new local frame
        Matrix2 localFrame;
        // translate to location
        localFrame.translate(cgeo.longitude, cgeo.latitude, cgeo.altitude);
        // scale 
        const double metersPerDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(cgeo.latitude);
        const double metersPerDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(cgeo.latitude);
        localFrame.scale(1.0/metersPerDegLng, 1.0/metersPerDegLat, 1.0);
        // rotate by convergence angle
        localFrame.rotate(-bearing*M_PI/180.0, 0.0, 0.0, 1.0);
        // translate center of AABB to origin (new LCS)
        localFrame.translate(-cxyz.x, -cxyz.y, -cxyz.z);
        // apply the original LCS to get coordinates into the WCS
        if (info.localFrame.get())
            localFrame.concatenate(*info.localFrame);
        info.localFrame = Matrix2Ptr(new Matrix2(localFrame), Memory_deleter_const<Matrix2>);
    }

    /**
     * Fetches the meshes for the node.
     * @return TE_Ok if the meshes for the node were successfully fetched
     */
    TAKErr fetchImpl(std::vector<std::unique_ptr<GLMesh>> &value, RenderContext &ctx, SceneNode &node, SceneInfo &info, const std::size_t lod, const std::shared_ptr<MaterialManager> &matmgr, bool *cancelToken)
    {
        TAKErr code(TE_Ok);

        // XXX - quadtree/octree

        //final Thread currentThread = Thread.currentThread();
        ProcessingCallback cb_load;
        cb_load.cancelToken = cancelToken;

        std::shared_ptr<const Mesh> mesh;
        code = node.loadMesh(mesh, lod, &cb_load);
        TE_CHECKRETURN_CODE(code);

        // transform if necessary
        if ((info.srid / 200) == 163) { // UTM 326xx/327xx
            utm2lla(info, mesh->getAABB());
        } 
#if SUPPORT_ECEF_RENDER
        else if (info.srid == 4978) {
           // Do nothing
        }
#endif
        else if (info.srid != 4326) {
            // generic reproject
            MeshPtr xformed(nullptr, nullptr);
            MeshTransformOptions srcOpts;
            srcOpts.srid = info.srid;
            srcOpts.localFrame = Matrix2Ptr(const_cast<Matrix2 *>(info.localFrame.get()), Memory_leaker_const<Matrix2>);

            MeshTransformOptions dstOpts;
            dstOpts.srid = 4326;

            MeshTransformOptions xformedOpts;

            // transform the mesh
            ProcessingCallback cb_transform;
            cb_transform.cancelToken = cancelToken;
            code = Mesh_transform(xformed, &xformedOpts, *mesh, srcOpts, dstOpts, &cb_transform);
            TE_CHECKRETURN_CODE(code);

            // update the info
            info.srid = xformedOpts.srid;
            if (xformedOpts.localFrame.get())
                info.localFrame = Matrix2Ptr(new Matrix2(*xformedOpts.localFrame), Memory_deleter_const<Matrix2>);
            else
                info.localFrame.reset();
            mesh = std::move(xformed);
        }

        Matrix2Ptr nodeTransform(nullptr, nullptr);
        if (info.localFrame.get()) {
            nodeTransform = Matrix2Ptr(new Matrix2(*info.localFrame), Memory_deleter_const<Matrix2>);
        }
        if (node.getLocalFrame()) {
            if (!nodeTransform.get())
                nodeTransform = Matrix2Ptr(new Matrix2(*node.getLocalFrame()), Memory_deleter_const<Matrix2>);
            else
                nodeTransform->concatenate(*node.getLocalFrame());
        }

        value.reserve(1u);
        value.push_back(std::move(std::unique_ptr<GLMesh>(new GLMesh(ctx, std::move(nodeTransform), info.altitudeMode, mesh, TAK::Engine::Math::Point2<double>(), matmgr))));

        return code;
    }

    std::size_t loadContextIdGenerator()
    {
        static std::size_t i = 0u;
        static Mutex m;

        m.lock();
        std::size_t r = ++i;
        m.unlock();
        return r;
    }
}

class GLSceneNode::LODMeshes
{
public :
    void release() NOTHROWS;
    void draw(const GLMapView2 &view, RenderState &state, const int renderPass, const ColorControl::Mode colorMode, const unsigned int color) NOTHROWS;
    bool isMaterialResolved() NOTHROWS;
    void setData(std::vector<std::unique_ptr<GLMesh>> &mesh_data) NOTHROWS;
public :
    static void glRelease(void *opaque) NOTHROWS;
public :
    std::shared_ptr<MaterialManager> matmgr;
    std::size_t locks{ 0 };
    std::vector<std::shared_ptr<GLMesh>> data;
    std::list<GLMaterial *> prefetch;
};

GLSceneNode::GLSceneNode(RenderContext &ctx_, SceneNodePtr &&subject_, const TAK::Engine::Model::SceneInfo &info_) NOTHROWS :
    GLSceneNode(ctx_, std::shared_ptr<SceneNode>(std::move(subject_)), info_, std::shared_ptr<MaterialManager>(new MaterialManager(ctx_)))
{}
GLSceneNode::GLSceneNode(RenderContext &ctx_, const std::shared_ptr<TAK::Engine::Model::SceneNode> &subject_, const TAK::Engine::Model::SceneInfo &info_) NOTHROWS :
    GLSceneNode(ctx_, subject_, info_, std::shared_ptr<MaterialManager>(new MaterialManager(ctx_)))
{}
GLSceneNode::GLSceneNode(RenderContext &ctx_, SceneNodePtr &&subject_, const SceneInfo &info_, MaterialManagerPtr &&matmgr_) NOTHROWS :
    GLSceneNode(ctx_, std::shared_ptr<SceneNode>(std::move(subject_)), info_, std::shared_ptr<MaterialManager>(std::move(matmgr_)))
{}
GLSceneNode::GLSceneNode(RenderContext &ctx_, const std::shared_ptr<SceneNode> &subject_, const SceneInfo &info_, MaterialManagerPtr &&matmgr_) NOTHROWS :
    GLSceneNode(ctx_, subject_, info_, std::shared_ptr<MaterialManager>(std::move(matmgr_)))
{}
GLSceneNode::GLSceneNode(RenderContext &ctx_, SceneNodePtr &&subject_, const SceneInfo &info_, const std::shared_ptr<MaterialManager> &matmgr_) NOTHROWS :
    GLSceneNode(ctx_, std::shared_ptr<SceneNode>(std::move(subject_)), info_, matmgr_)
{}
GLSceneNode::GLSceneNode(RenderContext &ctx_, const std::shared_ptr<SceneNode> &subject_, const SceneInfo &info_, const std::shared_ptr<MaterialManager> &matmgr_) NOTHROWS :
    ctx(ctx_),
    subject(subject_),
    info(info_),
    lastRenderIdx(-1),
    matmgr(matmgr_),
    colorMode(ColorControl::Modulate),
    color(0xFFFFFFFFu)
{
    refreshAABB(subject->getAABB());
}

TAKErr GLSceneNode::asyncLoad(GLSceneNode::LoadContext &loadContext, bool *cancelToken) NOTHROWS
{
    TAKErr code(TE_Ok);
    const std::size_t lodIdx = ((std::size_t)(intptr_t)loadContext.opaque.get());

    if(*cancelToken)
        return TE_Done;

    // load the meshes
    std::vector<std::unique_ptr<GLMesh>> fetchedMeshes;
    code = fetchImpl(fetchedMeshes, this->ctx, *subject, info, lodIdx, matmgr, cancelToken);
    if (code != TE_Ok) {
        // we failed to fetch the meshes. we'll populate an empty LODMeshes to indicate failure so we don't continue trying to load
        {
                Lock lock(mutex);
                code = lock.status;
                TE_CHECKRETURN_CODE(code);

                if (this->lodMeshes.empty()) {
                    this->lodMeshes.reserve(subject->getNumLODs());
                    for (std::size_t i = 0u; i < subject->getNumLODs(); i++)
                        this->lodMeshes.push_back(std::move(std::unique_ptr<LODMeshes>((LODMeshes *)nullptr)));
                }
                if(this->lodMeshes[lodIdx].get()) {
                    // dispose existing entry if not locked
                    if (!this->lodMeshes[lodIdx]->locks) {
                        std::unique_ptr<void, void(*)(const void *)> opaque(this->lodMeshes[lodIdx].release(), Memory_void_deleter_const<LODMeshes>);
                        this->ctx.queueEvent(LODMeshes::glRelease, std::move(opaque));
                    }
                }
                this->lodMeshes[lodIdx] = std::unique_ptr<LODMeshes>(new LODMeshes());

                this->ctx.requestRefresh();
            }
    } else if (!fetchedMeshes.empty()) {
        std::unique_ptr<LODMeshes> fetched(new LODMeshes());
        fetched->setData(fetchedMeshes);
        fetched->matmgr = matmgr;

        // prefetch the materials
        for(std::size_t i = 0u; i < fetched->data.size(); i++) {
            GLMesh &glmesh = *fetched->data[i];
            const Mesh &mesh = glmesh.getSubject();
            const std::size_t numMats = mesh.getNumMaterials();
            for(std::size_t j = 0u; j < numMats; j++) {
                Material mat;
                if (mesh.getMaterial(&mat, j) != TE_Ok)
                    continue;
                GLMaterial *glmat;
                if (fetched->matmgr->load(&glmat, mat) != TE_Ok)
                    continue;
                fetched->prefetch.push_back(glmat);
            }
        }

        if(!*cancelToken) {
            std::unique_ptr<Envelope2> aabb;
            if(!fetched->data.empty() && lodIdx == 0u) {
                aabb.reset(new Envelope2(fetched->data[0]->getSubject().getAABB()));
                for(std::size_t i = 1u; i < fetched->data.size(); i++) {
                    const Envelope2 &tmbb = fetched->data[i]->getSubject().getAABB();
                    if(tmbb.minX < aabb->minX)
                        aabb->minX = tmbb.minX;
                    if(tmbb.maxX > aabb->maxX)
                        aabb->maxX = tmbb.maxX;
                    if(tmbb.minY < aabb->minY)
                        aabb->minY = tmbb.minY;
                    if(tmbb.maxY > aabb->maxY)
                        aabb->maxY = tmbb.maxY;
                    if(tmbb.minZ < aabb->minZ)
                        aabb->minZ = tmbb.minZ;
                    if(tmbb.maxZ > aabb->maxZ)
                        aabb->maxZ = tmbb.maxZ;
                }
                MeshTransformOptions opts;
                opts.srid = info.srid;
                if (info.localFrame.get())
                    opts.localFrame = Matrix2Ptr(new Matrix2(*info.localFrame), Memory_deleter_const<Matrix2>);
                Mesh_transform(aabb.get(), *aabb, opts, MeshTransformOptions(4326));
            }

            {
                Lock lock(mutex);
                code = lock.status;
                TE_CHECKRETURN_CODE(code);

                if (this->lodMeshes.empty()) {
                    this->lodMeshes.reserve(subject->getNumLODs());
                    for (std::size_t i = 0u; i < subject->getNumLODs(); i++)
                        this->lodMeshes.push_back(std::move(std::unique_ptr<LODMeshes>((LODMeshes *)nullptr)));
                }
                if(this->lodMeshes[lodIdx].get()) {
                    // dispose existing entry if not locked
                    if (!this->lodMeshes[lodIdx]->locks) {
                        std::unique_ptr<void, void(*)(const void *)> opaque(this->lodMeshes[lodIdx].release(), Memory_void_deleter_const<LODMeshes>);
                        this->ctx.queueEvent(LODMeshes::glRelease, std::move(opaque));
                    }
                }
                this->lodMeshes[lodIdx] = std::move(fetched);

                // update mbb
                if (aabb.get()) {
                    mbb = *aabb;
                }
                this->ctx.requestRefresh();
            }
        } else {
            // canceled, dispose
            std::unique_ptr<void, void(*)(const void *)> opaque(fetched.release(), Memory_void_deleter_const<LODMeshes>);
            this->ctx.queueEvent(LODMeshes::glRelease, std::move(opaque));
        }
    }

    return code;
}
bool GLSceneNode::isLoaded(const GLMapView2 &view) const NOTHROWS
{
    if(lodMeshes.empty())
        return false;

    // choose LOD for view
    const int lodIdx = selectLodIndex(view, *subject, info, mbb, true);

    const auto clampedLod = atakmap::math::clamp<std::size_t>(lodIdx+1u, 0u, subject->getNumLODs()-1u);

    // if LOD not populated, fetch. always need to fetch minimum LOD
    return (lodMeshes[subject->getNumLODs()-1u].get() && lodMeshes[clampedLod].get());
}
TAKErr GLSceneNode::prepareLoadContext(GLSceneNode::LoadContext* value, const GLMapView2& view) const NOTHROWS
{
    return prepareLoadContext(value, view.scene, view.drawMapResolution);
}

TAKErr GLSceneNode::prepareLoadContext(GLSceneNode::LoadContext* value, const TAK::Engine::Core::MapSceneModel2& scene, double drawMapResolution) const NOTHROWS {

    TAKErr code(TE_Ok);

    if (!value)
        return TE_InvalidArg;

    // choose LOD for view
    const int lod = selectLodIndex(scene, drawMapResolution, *subject, info, mbb, true);
    const auto clampedLod = atakmap::math::clamp<std::size_t>(lod + 1u, 0u, subject->getNumLODs() - 1u);

    value->centroid = GeoPoint2((mbb.minY+mbb.maxY)/2.0, (mbb.minX+mbb.maxX)/2.0, (mbb.minZ+mbb.maxZ)/2.0, (info.altitudeMode==TEAM_Absolute) ? AltitudeReference::HAE: AltitudeReference::AGL);

    const Envelope2 &aabb = subject->getAABB();
    value->boundingSphereRadius = atakmap::math::max<double>(
            (aabb.maxX-aabb.minX)/2.0,
            (aabb.maxY-aabb.minY)/2.0,
            (aabb.maxZ-aabb.minZ)/2.0
        );

    if (lodMeshes.empty() || !lodMeshes[subject->getNumLODs()-1u].get())
        value->opaque = std::unique_ptr<void, void(*)(void*)>((void *)(intptr_t)(subject->getNumLODs()-1u), Memory_leaker<void>);
    else if (!lodMeshes[clampedLod].get())
        value->opaque = std::unique_ptr<void, void(*)(void*)>((void *)(intptr_t)clampedLod, Memory_leaker<void>);
    else
        return TE_Done;

    value->gsd = NAN;

    std::size_t reqlod;
    code = subject->getLevelOfDetail(&reqlod, (value->opaque.get()) ? (std::size_t)(intptr_t)value->opaque.get() : 0u);
    TE_CHECKRETURN_CODE(code);

    value->gsd = info.resolution*(1u << reqlod);
    if (isnan(value->gsd))
        value->gsd = info.resolution;

    return TE_Ok;
}
GLSceneNode::RenderVisibility GLSceneNode::isRenderable(const GLMapView2 &view) const NOTHROWS
{
    if(view.drawMapResolution > (info.minDisplayResolution*2.0))
        return RenderVisibility::None;

    const int lodIdx = selectLodIndex(view, *subject, info, mbb, false);
    if(lodIdx < -1)
        return RenderVisibility::None;

    double zOff = 0.0;
    if (info.altitudeMode != TEAM_Absolute) {
        // XXX - if clamp-to-ground, create some bounding sphere estimation as anchor point is unknown
        view.getTerrainMeshElevation(&zOff, (mbb.maxY + mbb.minY) / 2.0, (mbb.maxX + mbb.minX) / 2.0);
    }

    bool intersects;
    if (MapSceneModel2_intersects(&intersects, view.scene, mbb.minX, mbb.minY, mbb.minZ+zOff, mbb.maxX, mbb.maxY, mbb.maxZ+zOff) != TE_Ok)
        return RenderVisibility::None; // no failover here....
    if(intersects)
        return RenderVisibility::Draw;

    const double wx = (mbb.maxX-mbb.minX);
    const double wy = (mbb.maxY-mbb.minY);
    const double wz = (mbb.maxZ-mbb.minZ);

    if (MapSceneModel2_intersects(&intersects, view.scene, mbb.minX - wx, mbb.minY - wy, mbb.minZ - wz + zOff, mbb.maxX + wx, mbb.maxY + wy, mbb.maxZ + wz + zOff) != TE_Ok)
        return RenderVisibility::None;
    return intersects ? RenderVisibility::Prefetch : RenderVisibility::None;
}
TAKErr GLSceneNode::unloadLODs() NOTHROWS
{
    TAKErr code(TE_Ok);
    {
        Lock lock(mutex);
        code = lock.status;
        TE_CHECKRETURN_CODE(code);
        if(this->lodMeshes.empty())
            return code;
        for(std::size_t i = 1u; i < this->lodMeshes.size(); i++)
            if(this->lodMeshes[i].get()) {
                this->lodMeshes[i]->release();
                this->lodMeshes[i].reset();
            }
    }
    return code;
}
bool GLSceneNode::hasLODs() const NOTHROWS
{
    return subject->getNumLODs() > 1u;
}
TAKErr GLSceneNode::hitTest(TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapSceneModel2 &sceneModel, const float x, const float y) NOTHROWS
{
    TAKErr code(TE_Done);
    const int lodIdx = selectLodIndex(sceneModel, sceneModel.gsd, *subject, info, mbb, false);

    const std::size_t numLods = subject->getNumLODs();
    if (!numLods)
        return code;

    const auto clampedLod = atakmap::math::clamp<std::size_t>(lodIdx + 1u, 0u, numLods - 1);

    std::vector<std::shared_ptr<GLMesh>> hitTestMeshes;
    {
        Lock lock(mutex);
        TAKErr lockCode = lock.status;
        TE_CHECKRETURN_CODE(lockCode);

        if (this->lodMeshes.empty())
            return code;
        
        // select best LOD mesh same as draw
        std::size_t renderIdx = clampedLod;
        for (std::size_t i = renderIdx+1u; i > 0u; i--) {
            const std::size_t idx = i-1u;
            if (lodMeshes[idx].get() && !lodMeshes[idx]->data.empty()) {
                renderIdx = idx;
                break;
            }
        }
        // look at lower res meshes -- note if we hit above, we will break immediately
        for (std::size_t i = renderIdx; i < lodMeshes.size(); i++) {
            if (lodMeshes[i].get() && !lodMeshes[i]->data.empty()) {
                renderIdx = i;
                break;
            }
        }

        if (lodMeshes[renderIdx].get()) {
            std::vector<std::shared_ptr<GLMesh>> &src = lodMeshes[renderIdx]->data;
            hitTestMeshes.insert(hitTestMeshes.end(), src.begin(), src.end());
        }
    }

    auto it = hitTestMeshes.rbegin();
    auto end = hitTestMeshes.rend();
    while (it != end) {
        code = (*it)->hitTest(value, sceneModel, x, y);
        if (code != TE_Done)
            break;
        ++it;
    }

    return code;
}

TAKErr GLSceneNode::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {
    
    TAKErr code(TE_Ok);

    const int lodIdx = selectLodIndex(sceneModel, sceneModel.gsd, *subject, info, mbb, false);

    const std::size_t numLods = subject->getNumLODs();
    if (!numLods)
        return code;

    const auto clampedLod = atakmap::math::clamp<std::size_t>(lodIdx + 1u, 0u, numLods - 1);

    std::vector<std::shared_ptr<GLMesh>> hitTestMeshes;
    {
        Lock lock(mutex);
        TAKErr lockCode = lock.status;
        TE_CHECKRETURN_CODE(lockCode);

        if (this->lodMeshes.empty())
            return code;

        // select best LOD mesh same as draw
        std::size_t renderIdx = clampedLod;
        for (std::size_t i = renderIdx + 1u; i > 0u; i--) {
            const std::size_t idx = i - 1u;
            if (lodMeshes[idx].get() && !lodMeshes[idx]->data.empty()) {
                renderIdx = idx;
                break;
            }
        }
        // look at lower res meshes -- note if we hit above, we will break immediately
        for (std::size_t i = renderIdx; i < lodMeshes.size(); i++) {
            if (lodMeshes[i].get() && !lodMeshes[i]->data.empty()) {
                renderIdx = i;
                break;
            }
        }

        if (lodMeshes[renderIdx].get()) {
            std::vector<std::shared_ptr<GLMesh>>& src = lodMeshes[renderIdx]->data;
            hitTestMeshes.insert(hitTestMeshes.end(), src.begin(), src.end());
        }
    }

    auto it = hitTestMeshes.rbegin();
    auto end = hitTestMeshes.rend();

    // doesn't matter level depth, we know GLMesh is leaf level
    while (it != end) {
        result.push_back(it->get());
        ++it;
    }

    return code;
}

void GLSceneNode::depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {
    //TODO--
}

TAKErr GLSceneNode::setLocation(const GeoPoint2 &location, const Matrix2 *localFrame, const int srid, const AltitudeMode altitudeMode) NOTHROWS
{
    TAKErr code(TE_Ok);
    Lock lock(mutex);
    code = lock.status;
    TE_CHECKRETURN_CODE(code);

    *info.location = location;
    if (localFrame)
        info.localFrame = Matrix2Ptr(new Matrix2(*localFrame), Memory_deleter_const<Matrix2>);
    else
        info.localFrame.reset();
    info.srid = srid;
    info.altitudeMode = altitudeMode;

    bool correctForUTM = ((info.srid / 200) == 163);

    if(!lodMeshes.empty()) {
        std::unique_ptr<Envelope2> aabb;

        const std::size_t numLods = subject->getNumLODs();
        for (std::size_t i = 0u; i < numLods; i++) {
            if (lodMeshes[i].get()) {
                auto it = lodMeshes[i]->data.begin();
                auto end = lodMeshes[i]->data.end();
                while (it != end) {
                    if (!(*it).get())
                        continue;
                    if (correctForUTM) {
                        utm2lla(info, (*it)->getSubject().getAABB());
                        correctForUTM = false;
                    }

                    // compute node AABB
                    {
                        if(!aabb.get())
                            aabb.reset(new Envelope2((*it)->getSubject().getAABB()));
                        else {
                            const Envelope2 &tmbb = (*it)->getSubject().getAABB();
                            if (tmbb.minX < aabb->minX)
                                aabb->minX = tmbb.minX;
                            if (tmbb.maxX > aabb->maxX)
                                aabb->maxX = tmbb.maxX;
                            if (tmbb.minY < aabb->minY)
                                aabb->minY = tmbb.minY;
                            if (tmbb.maxY > aabb->maxY)
                                aabb->maxY = tmbb.maxY;
                            if (tmbb.minZ < aabb->minZ)
                                aabb->minZ = tmbb.minZ;
                            if (tmbb.maxZ > aabb->maxZ)
                                aabb->maxZ = tmbb.maxZ;
                        }
                    }

                    Matrix2Ptr nodeTransform(nullptr, nullptr);
                    if (info.localFrame.get()) {
                        nodeTransform = Matrix2Ptr(new Matrix2(*info.localFrame), Memory_deleter_const<Matrix2>);
                    }
                    if (subject->getLocalFrame()) {
                        if (!nodeTransform.get())
                            nodeTransform = Matrix2Ptr(new Matrix2(*subject->getLocalFrame()), Memory_deleter_const<Matrix2>);
                        else
                            nodeTransform->concatenate(*subject->getLocalFrame());
                    }
                    code = (*it)->setLocation(*info.location, nodeTransform.get(), info.srid, altitudeMode);
                    if (code != TE_Done)
                        break;
                    ++it;
                }
            }


        }

        // update the MBB based on actual meshes
        if (aabb.get()) {
            code = refreshAABB(*aabb);
            TE_CHECKRETURN_CODE(code);
        }
    } else {
        // update the MBB based on node
        code = refreshAABB(subject->getAABB());
        TE_CHECKRETURN_CODE(code);
    }

    return code;
}
TAKErr GLSceneNode::setColor(const ColorControl::Mode mode, const unsigned int argb) NOTHROWS
{
    switch (mode) {
    case ColorControl::Modulate :
    case ColorControl::Colorize :
    case ColorControl::Replace :
        colorMode = mode;
        break;
    default :
        return TE_InvalidArg;
    }
    color = argb;
    return TE_Ok;
}
TAKErr GLSceneNode::refreshAABB(const Envelope2 &aabb_mesh_local) NOTHROWS
{
    TAKErr code(TE_Ok);

    MeshTransformOptions opts(info.srid);
    opts.localFrame = Matrix2Ptr(new Matrix2(), Memory_deleter_const<Matrix2>);
    if (info.localFrame.get())
        opts.localFrame->concatenate(*info.localFrame);
    if (subject->getLocalFrame())
        opts.localFrame->concatenate(*subject->getLocalFrame());
    code = Mesh_transform(&mbb, aabb_mesh_local, opts, MeshTransformOptions(4326));
    TE_CHECKRETURN_CODE(code);

    return code;
}
void GLSceneNode::draw(const GLMapView2 &view, const int renderPass) NOTHROWS
{
    RenderState restore = RenderState_getCurrent();
    RenderState state(restore);
    draw(view, state, renderPass);
    if (state.shader.get()) {
        for (std::size_t i = state.shader->numAttribs; i > 0u; i--)
            glDisableVertexAttribArray(static_cast<GLuint>(i - 1u));
    }
    RenderState_makeCurrent(restore);
}
void GLSceneNode::draw(const GLMapView2 &view, RenderState &state, const int renderPass) NOTHROWS
{
    // choose LOD for view
    const int lodIdx = selectLodIndex(view, *subject, info, mbb, false);
    if(lodIdx < -1) {
        // drop levels not being used
        {
            Lock lock(mutex);
            if(!lodMeshes.empty()) {
                const std::size_t numLods = subject->getNumLODs();
                for (std::size_t i = 0u; i < numLods; i++) {
                    if (lodMeshes[i].get()) {
                        if (lodMeshes[i]->locks == 0)
                            lodMeshes[i]->release();
                        lodMeshes[i].reset();
                    }
                }
            }
        }
        return;
    }

    const std::size_t numLods = subject->getNumLODs();
    if (!numLods)
        return;

    const auto clampedLod = atakmap::math::clamp<std::size_t>(lodIdx + 1u, 0u, numLods - 1);
    {
        Lock lock(mutex);
        if(lodMeshes.empty())
            return;

        // render "best" populated LOD, first minimum >= target, second maximum <= target
        std::size_t renderIdx = clampedLod;
        for(std::size_t i = renderIdx+1u; i > 0; i--) {
            const std::size_t idx = i - 1u;
            if(lodMeshes[idx].get() && !lodMeshes[idx]->data.empty() && (numLods == 1u || lodMeshes[idx]->isMaterialResolved())) {
                renderIdx = idx;
                break;
            }
        }
        for(std::size_t i = renderIdx; i < numLods; i++) {
            if(lodMeshes[i].get() && !lodMeshes[i]->data.empty() && (numLods == 1u || lodMeshes[i]->isMaterialResolved())) {
                renderIdx = i;
                break;
            }
        }

        //if(renderIdx != lastRenderIdx && lastRenderIdx != -1 && fadeIdx != lastRenderIdx) {
        //    fadeCountdown = 10;
        //    fadeIdx = lastRenderIdx;
        //}

        //float fade = fadeCountdown/10.0;
        //if(lastRenderIdx != -1 && lastRenderIdx != renderIdx) {
        //    lodMeshes[lastRenderIdx].setAlpha(fade);
        //    lodMeshes[lastRenderIdx].draw(view, renderPass);
        //}
        if(lodMeshes[renderIdx].get()) {
    //            if(lodMeshes[renderIdx].ctrl != null) {
    //                int color = (((tileX+tileY)%2) == 0) ? 0xFFFFFF00 : 0xFF00FFFF;
    //                for(ColorControl ctrl : lodMeshes[renderIdx].ctrl)
    //                    ctrl.setColor(color);
    //            }
            //lodMeshes[renderIdx].setAlpha(1f-fade);
            lodMeshes[renderIdx]->draw(view, state, renderPass, colorMode, color);
            //if(fadeCountdown > 0) {
            //    fadeCountdown--;
            //} else {
            //    lastRenderIdx = renderIdx;
            //}
        }

        // drop levels not being used
        this->lastRenderIdx = static_cast<int>(renderIdx);

        for(std::size_t i = 0u; i < (numLods-1u); i++) {
            if(i == clampedLod)
                continue;
            if(i == renderIdx)
                continue;
            //if(i == lastRenderIdx)
            //    continue;
            if(lodMeshes[i].get()) {
                if(lodMeshes[i]->locks == 0)
                    lodMeshes[i]->release();
                lodMeshes[i].reset();
            }
        }
    }
}
void GLSceneNode::release() NOTHROWS
{
    {
        Lock lock(mutex);
        if (!lodMeshes.empty()) {
            for (std::size_t i = 0u; i < lodMeshes.size(); i++) {
                LODMeshes *lod = lodMeshes[i].get();
                if (!lod)
                    continue;
                if(!lod->locks)
                    lod->release();
                lodMeshes[i].reset();
            }
            lodMeshes.clear();
        }
    }
}
int GLSceneNode::getRenderPass() NOTHROWS
{
    return GLMapView2::Sprites;
}
void GLSceneNode::start() NOTHROWS
{
}
void GLSceneNode::stop() NOTHROWS
{
}

GLSceneNode::LoadContext::LoadContext() NOTHROWS :
    boundingSphereRadius(0.0),
    gsd(0.0),
    opaque(nullptr)
{
    id = loadContextIdGenerator();
}

GLSceneNode::LoadContext::LoadContext(const LoadContext &other) NOTHROWS = default;
   
void GLSceneNode::LODMeshes::release() NOTHROWS
{
    // release and clear meshes
    for(std::size_t i = 0u; i < data.size(); i++)
        data[i]->release();
    data.clear();

    // unload and clear texture prefetch
    std::list<GLMaterial *>::iterator mat;
    for (mat = prefetch.begin(); mat != prefetch.end(); mat++)
        matmgr->unload(*mat);
    prefetch.clear();

    // release material manager
    matmgr.reset();
}
void GLSceneNode::LODMeshes::draw(const GLMapView2 &view, RenderState &state, const int renderPass, const ColorControl::Mode mode, const unsigned int color) NOTHROWS
{
    for (std::size_t i = 0u; i < data.size(); i++) {
        data[i]->setColor(mode, color);
        data[i]->draw(view, state, renderPass);
    }
}
bool GLSceneNode::LODMeshes::isMaterialResolved() NOTHROWS
{
    std::list<GLMaterial *>::iterator mat;
    for (mat = prefetch.begin(); mat != prefetch.end(); mat++) {
        if(!(*mat)->isTextured())
            continue;
        // XXX - force state update
        (*mat)->getTexture();

        if((*mat)->isLoading())
            return false;
    }
    return true;
}
void GLSceneNode::LODMeshes::setData(std::vector<std::unique_ptr<GLMesh>> &mesh_data) NOTHROWS
{
    this->data.clear();
    this->data.reserve(mesh_data.size());
    for (std::size_t i = 0u; i < mesh_data.size(); i++)
        this->data.push_back(std::move(mesh_data[i]));
    mesh_data.clear();

#if 0
    if(this.data != null) {
        this.ctrl = new ColorControl[this.data.length];
        for(int i = 0; i < this.data.length; i++)
            this.ctrl[i] = this.data[i].getControl(ColorControl.class);
    } else {
        this.ctrl = null;
    }
#endif
}
void GLSceneNode::LODMeshes::glRelease(void *opaque) NOTHROWS
{
    auto *arg = static_cast<LODMeshes *>(opaque);
    arg->release();
}
