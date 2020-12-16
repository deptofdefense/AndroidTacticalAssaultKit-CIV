#include "renderer/model/GLMesh.h"

#include <cmath>

#include "core/ProjectionFactory3.h"
#include "math/Mesh.h"
#include "math/Utils.h"
#include "math/Vector4.h"
#include "renderer/GLES20FixedPipeline.h"
#include "renderer/core/GLMapView2.h"
#include "thread/Mutex.h"
#include "thread/Lock.h"
#include "renderer/GLES20FixedPipeline.h"
#include "util/MathUtils.h"
#include "renderer/GLDepthSampler.h"
#include "renderer/GLWorkers.h"
#include "util/Tasking.h"
#include "renderer/GLES20FixedPipeline.h"

using namespace TAK::Engine::Renderer::Model;

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Math;
using namespace TAK::Engine::Model;
using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Renderer::Core;
using namespace TAK::Engine::Thread;
using namespace TAK::Engine::Util;

using namespace atakmap::renderer;

namespace
{
    double adjustAltitude(double alt, double offset) NOTHROWS
    {
        return alt + offset;
    }

    GeoPoint2 computeDestinationPoint(const GeoPoint2 &p, const double &a, const double &d) NOTHROWS
    {
        GeoPoint2 surface = GeoPoint2_pointAtDistance(p, a, d, false);
        return GeoPoint2(surface.latitude, surface.longitude, p.altitude, p.altitudeRef);
    }

    TAKErr moveTowardsCamera(GeoPoint2 &value, const MapSceneModel2 &scene, const float x, const float y, const double meters) NOTHROWS
    {
        TAKErr code(TE_Ok);
        TAK::Engine::Math::Point2<double> org(x, y, -1.0);
        code = scene.inverseTransform.transform(&org, org);
        TE_CHECKRETURN_CODE(code);
        TAK::Engine::Math::Point2<double> tgt;
        code = scene.projection->forward(&tgt, value);
        TE_CHECKRETURN_CODE(code);

        double dx = org.x - tgt.x;
        double dy = org.y - tgt.y;
        double dz = org.z - tgt.z;
        const double d = sqrt(dx * dx + dy * dy + dz * dz);
        dx /= d;
        dy /= d;
        dz /= d;

        TAK::Engine::Math::Point2<double> off;

        code = scene.projection->forward(&off,
                GeoPoint2(value.latitude,
                        value.longitude,
                        adjustAltitude(value.altitude, meters), AltitudeReference::HAE));
        TE_CHECKRETURN_CODE(code);

        double tz;
        code = Vector2_length(&tz, TAK::Engine::Math::Point2<double>(tgt.x-off.x, tgt.y-off.y, tgt.z-off.z));
        TE_CHECKRETURN_CODE(code);

        code = scene.projection->forward(&off,
                computeDestinationPoint(value, 0.0, meters));
        TE_CHECKRETURN_CODE(code);

        double tx;
        code = Vector2_length(&tx, TAK::Engine::Math::Point2<double>(tgt.x-off.x, tgt.y-off.y, tgt.z-off.z));
        TE_CHECKRETURN_CODE(code);

        code = scene.projection->forward(&off,
                computeDestinationPoint(value, 90.0, meters));
        TE_CHECKRETURN_CODE(code);

        double ty;
                code = Vector2_length(&ty, TAK::Engine::Math::Point2<double>(tgt.x-off.x, tgt.y-off.y, tgt.z-off.z));
        TE_CHECKRETURN_CODE(code);

        tgt.x += dx * tx;
        tgt.y += dy * ty;
        tgt.z += dz * tz;

        code = scene.projection->inverse(&value, tgt);
        TE_CHECKRETURN_CODE(code);
        
        return code;
    } 

    TAKErr lla2ecef_transform(Matrix2Ptr_const &value, const Matrix2 *localFrame) NOTHROWS
    {
        TAKErr code(TE_Ok);

        Matrix2 mx;

        TAK::Engine::Math::Point2<double> pointD(0.0, 0.0, 0.0);
        GeoPoint2 geo;

        // if draw projection is ECEF and source comes in as LLA, we can
        // transform from LLA to ECEF by creating a local ENU CS and
        // chaining the following conversions (all via matrix)
        // 1. LCS -> LLA
        // 2. LLA -> ENU
        // 3. ENU -> ECEF
        // 4. ECEF -> NDC (via MapSceneModel 'forward' matrix)

        // obtain origin as LLA
        pointD.x = 0;
        pointD.y = 0;
        pointD.z = 0;
        if(localFrame)
            localFrame->transform(&pointD, pointD);
        // transform origin to ECEF
        geo.latitude = pointD.y;
        geo.longitude = pointD.x;
        geo.altitude = pointD.z;
        geo.altitudeRef = AltitudeReference::HAE;

        Projection2Ptr ecef(nullptr, nullptr);
        code = ProjectionFactory3_create(ecef, 4978);
        TE_CHECKRETURN_CODE(code);

        code = ecef->forward(&pointD, geo);
        TE_CHECKRETURN_CODE(code);

        // construct ENU -> ECEF
        const double phi = atakmap::math::toRadians(geo.latitude);
        const double lambda = atakmap::math::toRadians(geo.longitude);

        mx.translate(pointD.x, pointD.y, pointD.z);

        Matrix2 enu2ecef(
                -sin(lambda), -sin(phi)*cos(lambda), cos(phi)*cos(lambda), 0.0,
                cos(lambda), -sin(phi)*sin(lambda), cos(phi)*sin(lambda), 0.0,
                0, cos(phi), sin(phi), 0.0,
                0.0, 0.0, 0.0, 1.0
        );

        mx.concatenate(enu2ecef);

        // construct LLA -> ENU
        const double metersPerDegLat = GeoPoint2_approximateMetersPerDegreeLatitude(geo.latitude);
        const double metersPerDegLng = GeoPoint2_approximateMetersPerDegreeLongitude(geo.latitude);

        mx.scale(metersPerDegLng, metersPerDegLat, 1.0);
        mx.translate(-geo.longitude, -geo.latitude, -geo.altitude);

        value = Matrix2Ptr_const(new Matrix2(mx), Memory_deleter_const<Matrix2>);

        return code;
    }

    unsigned Color_red(unsigned argb) NOTHROWS
    {
        return (argb >> 16u) & 0xFFu;
    }
    unsigned Color_green(unsigned argb) NOTHROWS
    {
        return (argb >> 8u) & 0xFFu;
    }
    unsigned Color_blue(unsigned argb) NOTHROWS
    {
        return argb & 0xFFu;
    }
    unsigned Color_alpha(unsigned argb) NOTHROWS
    {
        return (argb >> 24u) & 0xFFu;
    }

	bool hasTexturedMaterial(std::vector<GLMaterial *> &materials) NOTHROWS {
		for (size_t i = 0; i < materials.size(); ++i)
			if (materials[i]->isTextured())
				return true;
		return false;
	}
}

GLMesh::GLMesh(RenderContext &ctx, const Matrix2 *localFrame, const AltitudeMode altitudeMode, const std::shared_ptr<const TAK::Engine::Model::Mesh> &subject, const TAK::Engine::Math::Point2<double> &anchor, const std::shared_ptr<MaterialManager>&matmgr) NOTHROWS :
    GLMesh(ctx,
           std::move(Matrix2Ptr_const(localFrame ? new Matrix2(*localFrame) : nullptr, Memory_deleter_const<Matrix2>)),
           altitudeMode,
            subject,
            anchor,
            matmgr)
{}
GLMesh::GLMesh(RenderContext &ctx, Matrix2Ptr_const &&localFrame, const AltitudeMode altitudeMode, const std::shared_ptr<const TAK::Engine::Model::Mesh> &subject, const TAK::Engine::Math::Point2<double> &anchor, const std::shared_ptr<MaterialManager>&matmgr) NOTHROWS :
    ctx_(ctx),
    subject_(subject),
    local_frame_(std::move(localFrame)),
    altitude_mode_(altitudeMode),
    model_anchor_point_(anchor),
    model_z_offset_(0.0),
    offset_terrain_version_(-1),
    wireframe_(nullptr, nullptr),
    allow_texture_(true),
    vbo_(GL_NONE),
    vbo_dirty_(true),
    use_vbo_(false),
    lla2ecef_(nullptr, nullptr),
    matmgr_(matmgr),
    color_mode_(ColorControl::Modulate),
    r_(1.0),
    g_(1.0),
    b_(1.0),
    a_(1.0),
    srid_(4326)
{
    if (subject_.get()) {
        use_vbo_ = subject_->isIndexed() ? subject_->getNumVertices() <= 0xFFFFu : subject_->getNumVertices() <= (3u * 0xFFFFu);
        use_vbo_ &= subject_->getVertexDataLayout().interleaved;
    }
}

TAKErr GLMesh::initMaterials() NOTHROWS
{
    TAKErr code(TE_Ok);
        //if(this->materials != null)
    const std::size_t numMaterials = subject_->getNumMaterials();
    if (this->materials_.size() == (numMaterials+1u))
        return TE_Ok;

    const VertexDataLayout &vertexDataLayout = this->subject_->getVertexDataLayout();

    this->materials_.reserve(numMaterials+1u);
    this->shader_.reserve(numMaterials+1u);
    this->material_initialized_.reserve(numMaterials+1u);

    // set the first material as opaque white for the replace
    {
        Material mat;
        mat.color = 0xFFFFFFFFu;

        GLMaterial *glmat = nullptr;
        code = this->matmgr_->load(&glmat, mat);
        TE_CHECKRETURN_CODE(code);
        this->materials_.push_back(glmat);
        std::shared_ptr<const Shader> s;
        VertexDataLayout replaceLayout(vertexDataLayout);
        replaceLayout.attributes = TEVA_Position;
        code = getShader(s, ctx_, replaceLayout, *glmat);
        TE_CHECKRETURN_CODE(code);
        this->shader_.push_back(s);

        this->material_initialized_.push_back(true);
    }

    for(std::size_t i = 0; i < numMaterials; i++) {
        Material mat;
        code = subject_->getMaterial(&mat, i);
        TE_CHECKBREAK_CODE(code);
        GLMaterial *glmat = nullptr;
        code = this->matmgr_->load(&glmat, mat);
        TE_CHECKBREAK_CODE(code);
        this->materials_.push_back(glmat);
        std::shared_ptr<const Shader> s;
        code = getShader(s, ctx_, vertexDataLayout, *glmat);
        TE_CHECKBREAK_CODE(code);
        this->shader_.push_back(s);

        this->material_initialized_.push_back(false);
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
const TAK::Engine::Model::Mesh &GLMesh::getSubject() const NOTHROWS
{
    return *subject_;
}
TAKErr GLMesh::resolveMaterials(bool *value) NOTHROWS
{
    if (!value)
        return TE_InvalidArg;
    TAKErr code(TE_Ok);
    *value = true;
    for (std::size_t i = 0u; i < materials_.size(); i++) {
        GLMaterial *material = materials_[i];
        GLTexture2 *texture = material->getTexture();
        if(!texture && material->isTextured()) {
            *value = false;
            continue;
        }

        if(this->material_initialized_[i])
            continue;

        // XXX - may not always be mirrored...

        if(texture) { // XXX - this may require POT
            texture->setWrapS(GL_MIRRORED_REPEAT);
            texture->setWrapT(GL_MIRRORED_REPEAT);
        }

        if(texture && !texture->isCompressed() && TAK::Engine::Util::MathUtils_isPowerOf2(texture->getTexWidth()) && TAK::Engine::Util::MathUtils_isPowerOf2(texture->getTexHeight())) {
            // apply mipmap if texture is power-of-2
            glEnable(GL_TEXTURE_2D);
            texture->setMinFilter(GL_LINEAR_MIPMAP_NEAREST);
            glBindTexture(GL_TEXTURE_2D, texture->getTexId());
            glGenerateMipmap(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D,0);
            glDisable(GL_TEXTURE_2D);
        }

        std::shared_ptr<const Shader> s;
        code = getShader(s, ctx_, this->subject_->getVertexDataLayout(), *this->materials_[i]);
        TE_CHECKBREAK_CODE(code);
        this->shader_[i] = s;

        this->material_initialized_[i] = true;
    }
    TE_CHECKRETURN_CODE(code);

    return code;
}
void GLMesh::setMatrices(const Shader &shader, const bool mv, const bool p, const bool t) const NOTHROWS
{
    if(mv) {
        double mxd[16];
        transform_.modelView.get(mxd, Matrix2::COLUMN_MAJOR);
        float mxf[16];
        for (std::size_t i = 0u; i < 16u; i++)
            mxf[i] = static_cast<float>(mxd[i]);
        glUniformMatrix4fv(shader.uModelView, 1, false, mxf);
    }
    if(p) {
        double mxd[16];
        transform_.projection.get(mxd, Matrix2::COLUMN_MAJOR);
        float mxf[16];
        for (std::size_t i = 0u; i < 16u; i++)
            mxf[i] = static_cast<float>(mxd[i]);
        glUniformMatrix4fv(shader.uProjection, 1, false, mxf);
    }
    if(t) {
        double mxd[16];
        transform_.texture.get(mxd, Matrix2::COLUMN_MAJOR);
        float mxf[16];
        for (std::size_t i = 0u; i < 16u; i++)
            mxf[i] = static_cast<float>(mxd[i]);
        glUniformMatrix4fv(shader.uTextureMx, 1, false, mxf);
    }
}

void GLMesh::setMatrices(const Shader2& shader, const bool mvp, const bool imv) const NOTHROWS 
{
    if (mvp) {
        Matrix2 m = transform_.projection;
        m.concatenate(transform_.modelView);
        double mxd[16];
        m.get(mxd, Matrix2::COLUMN_MAJOR);
        float mxf[16];
        for (std::size_t i = 0u; i < 16u; i++)
            mxf[i] = static_cast<float>(mxd[i]);
        glUniformMatrix4fv(shader.uMVP, 1, false, mxf);
    }
    if (imv) {
        Matrix2 m;
        if (transform_.modelView.createInverse(&m) != TE_Ok)
            return;
        double mxd[16];
        m.get(mxd, Matrix2::COLUMN_MAJOR);
        float mxf[16];
        for (std::size_t i = 0u; i < 16u; i++)
            mxf[i] = static_cast<float>(mxd[i]);
        glUniformMatrix4fv(shader.uInvModelView, 1, false, mxf);
    }
}


TAKErr getViewTerrainMeshElevation(double* value, const double latitude, const double longitude, const void* opaque) NOTHROWS {
    const GLMapView2* view = static_cast<const GLMapView2 *>(opaque);
    return view->getTerrainMeshElevation(value, latitude, longitude);
}

void GLMesh::draw(const GLMapView2 &view, const int renderPass) NOTHROWS
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

void GLMesh::draw(const Core::GLMapView2& view, RenderState& state, const int renderPass) NOTHROWS {
    ViewState_ viewState{
        view.scene,
        view.elevationScaleFactor,
        view.drawSrid,
        view.getTerrainVersion(),
        getViewTerrainMeshElevation,
        &view
    };
    draw(viewState, state, renderPass);
}
void GLMesh::prepareTransform(const ViewState_& viewState/*, Renderer::RenderState& state*/) NOTHROWS
{
    Matrix2 mx;
    mx.set(viewState.scene.forwardTransform);

    if (altitude_mode_ != TEAM_Absolute) {
        TAK::Engine::Math::Point2<double> pointD(model_anchor_point_);
        GeoPoint2 geo;
        if (local_frame_.get())
            local_frame_->transform(&pointD, pointD);
        // XXX - assuming source is 4326
        if (viewState.drawSrid == 4978 && this->srid_ == 4326) {
            // XXX - obtain origin as LLA
            geo.latitude = pointD.y;
            geo.longitude = pointD.x;
            geo.altitude = pointD.z;
            geo.altitudeRef = AltitudeReference::HAE;
        }
        else {
            viewState.scene.projection->inverse(&geo, pointD);
        }

        const int terrainVersion = viewState.terrainVersion;
        if (this->offset_terrain_version_ != terrainVersion || true) {
            double localElevation;
            TAKErr code = viewState.getTerrainMeshElevation(&localElevation, geo.latitude, geo.longitude, viewState.opaque);
            if (code != TE_Ok || isnan(localElevation))
                localElevation = 0.0;

            // adjust the model to the local elevation
            this->model_z_offset_ = localElevation;
            this->offset_terrain_version_ = terrainVersion;
        }
    }
    else {
        this->model_z_offset_ = 0.0;
    }

    // XXX - assuming source is 4326
    if (viewState.drawSrid == 4978 && this->srid_ == 4326) {
        if (!lla2ecef_.get())
            lla2ecef_transform(this->lla2ecef_, this->local_frame_.get());
        mx.concatenate(*this->lla2ecef_);
    }

    mx.scale(1.0, 1.0, viewState.elevationScaleFactor);
    mx.translate(0, 0, model_z_offset_);

    if (local_frame_.get()) {
        // transform from local frame to SR CS
        mx.concatenate(*local_frame_);
    }

    // fill the transformation matrices
    float mxf[16];
    atakmap::renderer::GLES20FixedPipeline::getInstance()->readMatrix(atakmap::renderer::GLES20FixedPipeline::MM_GL_PROJECTION, mxf);
    for (std::size_t i = 0u; i < 16u; i++)
        transform_.projection.set(i % 4u, i / 4u, mxf[i]);
    transform_.modelView.set(mx);
    transform_.texture.setToIdentity();
}

void GLMesh::draw(const Shader2& shader, const ViewState_& viewState, RenderState& state, bool wireframe) NOTHROWS {
    const VertexDataLayout& vertexDataLayout = this->subject_->getVertexDataLayout();
    this->prepareTransform(viewState/*, state*/);
    
    glUseProgram(shader.handle);

    // set the matrices
    setMatrices(shader, true, false);

    glEnableVertexAttribArray(shader.aVertexCoords);

    const void* verts = nullptr;
    subject_->getVertices(&verts, TEVA_Position);
    GLsizei stride = (GLsizei)vertexDataLayout.position.stride;
    GLsizei count = (GLsizei)subject_->getNumVertices();

    glVertexAttribPointer(shader.aVertexCoords, 3, GL_FLOAT, false, stride, verts);

    if (shader.uColor != -1)
        glUniform4f(shader.uColor, 0.6f, 0.0f, 0.0f, 1.0f);

    //TODO-- what is it
    glDrawArrays(GL_TRIANGLES, 0, count);

    glDisableVertexAttribArray(shader.aVertexCoords);
}
void GLMesh::updateBindVbo() NOTHROWS {
    const VertexDataLayout& vertexDataLayout = this->subject_->getVertexDataLayout();
    if (use_vbo_ && (vbo_ == GL_NONE || this->vbo_dirty_)) {
        if (this->vbo_dirty_ && vbo_ != GL_NONE)
            glDeleteBuffers(1u, &vbo_);

        // generate the VBO
        glGenBuffers(1u, &vbo_);

        // bind the VBO
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);

        const void* buf;
        subject_->getVertices(&buf, TEVA_Position);
        size_t bufSize = subject_->getNumVertices() * vertexDataLayout.position.stride;

        if (vertexDataLayout.interleaved)
            VertexDataLayout_requiredInterleavedDataSize(&bufSize, vertexDataLayout, subject_->getNumVertices());

        // upload the buffer data as static
        glBufferData(
            GL_ARRAY_BUFFER,
            bufSize,
            buf,
            GL_STATIC_DRAW);

        // free the vertex array
        //model.dispose();
        this->vbo_dirty_ = false;
    }
    else if (use_vbo_) {
        // bind the VBO
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    }
}
void GLMesh::draw(const ViewState_& viewState, RenderState& state, const int renderPass) NOTHROWS
{
    if (!(renderPass&getRenderPass()))
        return;

    this->initMaterials();

    // check if the materials are loaded
    bool materialsResolved;
    resolveMaterials(&materialsResolved);

            /*GLTexture texture = (materialsResolved && this->materials_.length > 0) ? this->materials_[0].getTexture() : null;
    final boolean isTextured0 = (MathUtils.hasBits(
            vertexDataLayout.attributes, Mesh.VERTEX_ATTR_TEXCOORD_0)
            && texture != null);
    final boolean isTextured = allowTexture && isTextured0;*/
    // Is textured if at least 1 material is textured and all materials are resolved
	const bool isTextured = allow_texture_ &&
		materialsResolved &&
		!this->materials_.empty() &&
		//this->materials_[0]->isTextured();
		hasTexturedMaterial(this->materials_);

    const VertexDataLayout &vertexDataLayout = this->subject_->getVertexDataLayout();

    // draw model
    this->prepareTransform(viewState/*, state*/);
#if 0
   if (!this->wireframe_.get()) {
        const void *verts = nullptr;
        subject_->getVertices(&verts, TEVA_Position);
        this->wireframe_ = MemBuffer2Ptr(new MemBuffer2((const float *)verts, subject_->getNumVertices() * 3), Memory_deleter_const<MemBuffer2>);
    }
#endif

    // if we have a wireframe, draw it before drawing the model
    if (!isTextured && this->wireframe_.get()) {
        if (!this->wireframe_shader_.get()) {
            Shader_get(wireframe_shader_, ctx_, RenderAttributes());
        }

        glUseProgram(this->wireframe_shader_->handle);

        // set the matrices
        setMatrices(*wireframe_shader_, true, true, false);

        glEnableVertexAttribArray(this->wireframe_shader_->aVertexCoords);

        const void* verts = nullptr;
        subject_->getVertices(&verts, TEVA_Position);
        GLsizei stride = (GLsizei)subject_->getVertexDataLayout().position.stride;
        GLsizei count = (GLsizei)subject_->getNumVertices();

        glVertexAttribPointer(this->wireframe_shader_->aVertexCoords, 3, GL_FLOAT, false, stride, /*this->wireframe_->get()*/verts);
        glUniform4f(this->wireframe_shader_->uColor, 0.6f, 0.0f, 0.0f, 1.0f);
        glDrawArrays(GL_LINES, 0, count);
        glDisableVertexAttribArray(this->wireframe_shader_->aVertexCoords);
    }

    if(this->shader_.empty())
        return;

    if (this->subject_->getFaceWindingOrder() != TEWO_Undefined) {
        if (state.cull.face != GL_BACK) {
            state.cull.face = GL_BACK;
            glCullFace(state.cull.face);
        }
        GLint frontFace;
        switch (this->subject_->getFaceWindingOrder()) {
        case TEWO_Clockwise :
            frontFace = GL_CW;
            break;
        case TEWO_CounterClockwise :
            frontFace = GL_CCW;
            break;
        default :
            // XXX - illegal state
            frontFace = GL_CCW;
            break;
        }
        if (state.cull.front != frontFace) {
            state.cull.front = frontFace;
            glFrontFace(state.cull.front);
        }
        if (!state.cull.enabled) {
            state.cull.enabled = true;
            glEnable(GL_CULL_FACE);
        }
    }

    this->updateBindVbo();

    if (!state.blend.enabled) {
        state.blend.enabled = true;
        glEnable(GL_BLEND);
    }
    if (state.blend.src != GL_SRC_ALPHA || state.blend.dst != GL_ONE_MINUS_SRC_ALPHA) {
        state.blend.src = GL_SRC_ALPHA;
        state.blend.dst = GL_ONE_MINUS_SRC_ALPHA;
        glBlendFunc(state.blend.src, state.blend.dst);
    }

    const bool replaceOnly = (color_mode_ == ColorControl::Replace);

    bool reset = true;
    for (std::size_t i = replaceOnly ? 0u : 1u; i < this->shader_.size(); i++) {
#define Shader_numAttribs(s) ((s ? s->numAttribs : 0u))
        const std::size_t a = Shader_numAttribs(state.shader.get());
        const std::size_t b = Shader_numAttribs(shader_[i].get());
#undef Shader_numAttribs

        if (shader_[i].get() != state.shader.get()) {
            state.shader = shader_[i];
            glUseProgram(state.shader->handle);
            reset = true;
        }
        for (std::size_t j = a; j < b; j++)
            glEnableVertexAttribArray(static_cast<GLuint>(j));
        for (std::size_t j = a; j > b; j--)
            glDisableVertexAttribArray(static_cast<GLuint>(j));

        draw(*this->shader_[i], *materials_[i], reset);
        reset = false;
        // if doing replace, only run the first shader
        if (color_mode_ == ColorControl::Replace)
            break;
    }

    if(use_vbo_)
        glBindBuffer(GL_ARRAY_BUFFER, 0);
}
void GLMesh::draw(const Shader &shader, GLMaterial &material, const bool reset)  const NOTHROWS
{
    const VertexDataLayout &vertexDataLayout = this->subject_->getVertexDataLayout();

    if(reset) {
        // XXX - use layout attribute type
        if(use_vbo_) {
            glVertexAttribPointer(shader.aVertexCoords,
                    3u, GL_FLOAT,
                    false,
                    static_cast<GLsizei>(vertexDataLayout.position.stride),
                    (const void *)vertexDataLayout.position.offset);
        } else {
            const void *buf;
            subject_->getVertices(&buf, TEVA_Position);

            glVertexAttribPointer(shader.aVertexCoords,
                    3u, GL_FLOAT,
                    false,
                    static_cast<GLsizei>(vertexDataLayout.position.stride),
                    static_cast<const uint8_t *>(buf)+vertexDataLayout.position.offset);
        }
        if (vertexDataLayout.attributes&TEVA_Color) {
            // XXX - use attribute layout type
            if(use_vbo_) {
                glVertexAttribPointer(shader.aColorPointer,
                        4u, GL_UNSIGNED_BYTE,
                        true,
                        static_cast<GLsizei>(vertexDataLayout.color.stride),
                        (const void *)vertexDataLayout.color.offset);
            }  else {
                const void *buf;
                subject_->getVertices(&buf, TEVA_Color);

                glVertexAttribPointer(shader.aColorPointer,
                        4, GL_UNSIGNED_BYTE,
                        true,
                        static_cast<GLsizei>(vertexDataLayout.color.stride),
                        static_cast<const uint8_t *>(buf)+vertexDataLayout.color.offset);
            }
        }

        if(shader.lighting) {
            if(use_vbo_) {
                glVertexAttribPointer(
                        shader.aNormals,
                        3,
                        GL_FLOAT,
                        false,
                        static_cast<GLsizei>(vertexDataLayout.normal.stride),
                        (const void *)vertexDataLayout.normal.offset);
            } else {
                const void *buf;
                subject_->getVertices(&buf, TEVA_Normal);

                glVertexAttribPointer(shader.aNormals,
                        3, GL_FLOAT,
                        false,
                        static_cast<GLsizei>(vertexDataLayout.normal.stride),
                        static_cast<const uint8_t *>(buf)+vertexDataLayout.normal.offset);
            }
        }
    }

    float red = 1.0;
    float green = 1.0;
    float blue = 1.0;
    float alpha = 1.0;
    if (shader.textured) {
        transform_.texture.setToIdentity();

        if(shader.alphaDiscard)
            glUniform1f(shader.uAlphaDiscard, 0.3f);

        GLTexture2 *texture = material.getTexture();
        if (texture && material.getSubject().textureCoordIndex != Material::InvalidTextureCoordIndex) {
            // XXX - tex coord scaling assumes all material textures have same size
            transform_.texture.scale((float)material.getWidth() / (float)texture->getTexWidth(), (float)material.getHeight() / (float)texture->getTexHeight(), 1.0);

            glActiveTexture(GL_TEXTURE0 + material.getSubject().textureCoordIndex);
            VertexArray layoutArray = vertexDataLayout.texCoord0;
            VertexDataLayout_getTexCoordArray(&layoutArray, vertexDataLayout, material.getSubject().textureCoordIndex);

            // XXX - use attribute layout type
            if(use_vbo_) {
                glVertexAttribPointer(
                        shader.aTextureCoords,
                        2,
                        GL_FLOAT,
                        false,
                        static_cast<GLsizei>(layoutArray.stride),
                        (const void *)layoutArray.offset);
            } else {
                // XXX - broken impl from java reference, should look up actual TEVA
                const void *buf;
                subject_->getVertices(&buf, TEVA_TexCoord0);

                glVertexAttribPointer(shader.aTextureCoords,
                        2, GL_FLOAT,
                        false,
                        static_cast<GLsizei>(layoutArray.stride),
                        static_cast<const uint8_t *>(buf) + layoutArray.offset);
            }

            glBindTexture(GL_TEXTURE_2D, texture->getTexId());

            glUniform1i(shader.uTexture, material.getSubject().textureCoordIndex);

            red = Color_red(material.getSubject().color) / 255.0f;
            green = Color_green(material.getSubject().color) / 255.0f;
            blue = Color_blue(material.getSubject().color) / 255.0f;
            alpha = Color_alpha(material.getSubject().color) / 255.0f;

        }

        // return back to zero
        glActiveTexture(GL_TEXTURE0);
    } else {
        red = Color_red(material.getSubject().color) / 255.0f;
        green = Color_green(material.getSubject().color) / 255.0f;
        blue = Color_blue(material.getSubject().color) / 255.0f;
        alpha = Color_alpha(material.getSubject().color) / 255.0f;
    }

    glUniform4f(shader.uColor, red*r_, green*g_, blue*b_, alpha*a_);

    int mode;
    switch (this->subject_->getDrawMode()) {
        case TEDM_Triangles:
            mode = GL_TRIANGLES;
            break;
        case TEDM_TriangleStrip:
            mode = GL_TRIANGLE_STRIP;
            break;
        case TEDM_Points:
            mode = GL_POINTS;
            break;
        default:
            // XXX -
            return;
    }

    // set the matrices
    if(reset)
        setMatrices(shader, true, true, shader.textured);

    if (subject_->isIndexed()) {
        glDrawElements(mode,
                static_cast<GLsizei>(subject_->getNumIndices()),
                GL_UNSIGNED_SHORT, subject_->getIndices());
    } else {
        const std::size_t vertRenderLimit = (3u * 0xFFFFu);
        for (std::size_t i = 0; i < subject_->getNumVertices(); i += vertRenderLimit) {
            glDrawArrays(mode, static_cast<GLint>(i),
                static_cast<GLsizei>(std::min(vertRenderLimit, subject_->getNumVertices()-i)));
        }
    }
}
void GLMesh::release() NOTHROWS
{
    // XXX - don't think this is applicable for us, will be automatically handled via shared_ptr destruct
#if 0
    if (this->subject_ != null) {
        this->subject_->dispose();
        this->subject_ = null;
    }
#endif
    if (!this->materials_.empty()) {
        for(std::size_t i = 0u; i < this->materials_.size(); i++)
            this->matmgr_->unload(this->materials_[i]);
        this->materials_.clear();
        this->material_initialized_.clear();
        this->shader_.clear();
    }

    if (vbo_ != GL_NONE) {
        glDeleteBuffers(1u, &vbo_);
        vbo_ = GL_NONE;
    }
}
int GLMesh::getRenderPass() NOTHROWS
{
    return GLMapView2::Sprites;
}
void GLMesh::start() NOTHROWS
{}
void GLMesh::stop() NOTHROWS
{}

TAKErr GLMesh::getMeshTerrainMeshElevation(double* value, const double latitude, const double longitude, const void* opaque) NOTHROWS {
    const GLMesh* mesh = static_cast<const GLMesh*>(opaque);
    *value = mesh->model_z_offset_;
    return TE_Ok; 
}

TAKErr GLMesh::gatherDepthSamplerDrawables(std::vector<GLDepthSamplerDrawable*>& result, int levelDepth, const TAK::Engine::Core::MapSceneModel2& sceneModel, float x, float y) NOTHROWS {
    return TE_Done;
}

void GLMesh::depthSamplerDraw(GLDepthSampler& sampler, const TAK::Engine::Core::MapSceneModel2& sceneModel) NOTHROWS {
    ViewState_ viewState{
        sceneModel,
        1.0,//view.elevationScaleFactor,
        sceneModel.projection->getSpatialReferenceID(),
        offset_terrain_version_,
        getMeshTerrainMeshElevation,
        this
    };

    const VertexDataLayout& vertexDataLayout = this->subject_->getVertexDataLayout();
    this->prepareTransform(viewState);

    // set MVP
    Matrix2 m = transform_.projection;
    m.concatenate(transform_.modelView);
    double mxd[16];
    m.get(mxd, Matrix2::COLUMN_MAJOR);
    float mxf[16];
    for (std::size_t i = 0u; i < 16u; i++)
        mxf[i] = static_cast<float>(mxd[i]);
    glUniformMatrix4fv(sampler.uniformMVP(), 1, false, mxf);

    GLuint aVertCoords = sampler.attributeVertexCoords();
    
    glEnableVertexAttribArray(aVertCoords);

    /*const void* verts = nullptr;
    subject_->getVertices(&verts, TEVA_Position);
    GLsizei stride = (GLsizei)vertexDataLayout.position.stride;
    GLsizei count = (GLsizei)subject_->getNumVertices();

    glVertexAttribPointer(aVertCoords, 3, GL_FLOAT, false, stride, verts);*/

    this->updateBindVbo();
    if (use_vbo_) {
        glVertexAttribPointer(aVertCoords,
            3u, GL_FLOAT,
            false,
            static_cast<GLsizei>(vertexDataLayout.position.stride),
            (const void*)vertexDataLayout.position.offset);
    }
    else {
        const void* buf;
        subject_->getVertices(&buf, TEVA_Position);

        glVertexAttribPointer(aVertCoords,
            3u, GL_FLOAT,
            false,
            static_cast<GLsizei>(vertexDataLayout.position.stride),
            static_cast<const uint8_t*>(buf) + vertexDataLayout.position.offset);
    }

    /*GLsizei count = (GLsizei)subject_->getNumVertices();
    glDrawArrays(GL_TRIANGLES, 0, count);*/

    int mode;
    switch (this->subject_->getDrawMode()) {
    case TEDM_Triangles:
        mode = GL_TRIANGLES;
        break;
    case TEDM_TriangleStrip:
        mode = GL_TRIANGLE_STRIP;
        break;
    case TEDM_Points:
        mode = GL_POINTS;
        break;
    default:
        // XXX -
        return;
    }

    if (subject_->isIndexed()) {
        glDrawElements(mode,
            static_cast<GLsizei>(subject_->getNumIndices()),
            GL_UNSIGNED_SHORT, subject_->getIndices());
    }
    else {
        const std::size_t vertRenderLimit = (3u * 0xFFFFu);
        for (std::size_t i = 0; i < subject_->getNumVertices(); i += vertRenderLimit) {
            glDrawArrays(mode, static_cast<GLint>(i),
                static_cast<GLsizei>(std::min(vertRenderLimit, subject_->getNumVertices() - i)));
        }
    }

    if (use_vbo_)
        glBindBuffer(GL_ARRAY_BUFFER, 0);

    glDisableVertexAttribArray(aVertCoords);
}

TAKErr GLMesh::hitTest(TAK::Engine::Core::GeoPoint2 *value, const TAK::Engine::Core::MapSceneModel2 &sceneModel, const float x, const float y) NOTHROWS
{
    TAKErr code(TE_Ok);

    // this is the local frame being sent in to the MapSceneModel inverse
    // method that will translate between the mesh LCS, applying any
    // LLA->ECEF and relative altitude adjustments, and the WCS. This
    // transform may initially be 'null' if neither LLA->ECEF or relative
    // altitude adjustments are being applied
    std::unique_ptr<Matrix2> localFrame;
    if (!this->subject_.get())
        return TE_Done;
    const double zOffset = this->model_z_offset_;

    // apply LLA->ECEF transform if applicable
    if(sceneModel.projection->getSpatialReferenceID() == 4978) {
        if (!this->lla2ecef_.get()) {
            code = lla2ecef_transform(this->lla2ecef_, this->local_frame_.get());
            TE_CHECKRETURN_CODE(code);
        }
        localFrame.reset(new Matrix2(*this->lla2ecef_));
    }
    // apply relative altitude adjustment, if applicable
    if (zOffset != 0.0) {
        if (!localFrame.get())
            localFrame.reset(new Matrix2());
        localFrame->translate(0.0, 0.0, zOffset);
    }

    // member field 'localFrame' may be 'null' here if there is no local
    // frame that transforms between the mesh LCS and the WCS -- in other
    // words LCS == WCS.

    if (this->local_frame_.get() && !localFrame.get())
        // there's a LCS->WCS transform, but no LLA->ECEF and/or relative
        // altitude adjustment. we're passing through the LCS->WCS as
        // the transform to use for hit-test computation
        localFrame.reset(new Matrix2(*this->local_frame_));
    else if (this->local_frame_.get()) // & localFrame != null, per above check
        // there's a LCS->WCS transform and LLA->ECEF and/or relative
        // altitude adjustment. concatenate the LCS->WCS transform to
        // the LLA->ECEF and/or altitude adjustment
        localFrame->concatenate(*this->local_frame_);
    // else, no LCS->WCS, use any applicable LLA->ECEF and/or relative
    // altitude adjustment only

    // Note: this method accepts 'null' local frame, in which case it
    // assumes that LCS == WCS
    GeometryModel2Ptr gm(new TAK::Engine::Math::Mesh(this->subject_, localFrame.get()), Memory_deleter_const<TAK::Engine::Math::GeometryModel2, TAK::Engine::Math::Mesh>);

    code = sceneModel.inverse(value, TAK::Engine::Math::Point2<float>(x, y), *gm);
    if (code != TE_Ok)
        return TE_Done;

    // adjust altitude for renderer elevation offset
    if (!isnan(value->altitude)) {
        // specify a very small offset to move towards the camera. this is
        // to prevent z-fighting when a point is placed directly on the
        // surface. currently moving ~1ft
        const double offset = 0.30;
        GeoPoint2 result(*value);
        if (moveTowardsCamera(result, sceneModel, x, y, offset) == TE_Ok)
            *value = result;
    }

    return code;
}
void GLMesh::refreshLocalFrame() NOTHROWS
{
    // XXX - really bad, use control here instead
    lla2ecef_.reset();
}
TAKErr GLMesh::getControl(void **ctrl, const char *type) const NOTHROWS
{
    // XXX - implement controls support
    return TE_InvalidArg;
}
TAKErr GLMesh::setLocation(const GeoPoint2 &location, const Matrix2 *localFrame, const int srid, const AltitudeMode altitudeMode) NOTHROWS
{
    if (!localFrame)
        this->local_frame_.reset();
    else
        this->local_frame_ = Matrix2Ptr(new Matrix2(*localFrame), Memory_deleter_const<Matrix2>);

    this->altitude_mode_ = altitudeMode;

    refreshLocalFrame();

    return TE_Ok;
}
TAKErr GLMesh::setColor(const ColorControl::Mode mode, const unsigned int argb) NOTHROWS
{
    switch (mode) {
    case ColorControl::Modulate :
    case ColorControl::Colorize :
    case ColorControl::Replace :
        color_mode_ = mode;
        break;
    default :
        return TE_InvalidArg;
    }
    r_ = Color_red(argb) / 255.f;
    g_ = Color_green(argb) / 255.f;
    b_ = Color_blue(argb) / 255.f;
    a_ = Color_alpha(argb) / 255.f;
    return TE_Ok;
}
TAKErr GLMesh::getShader(std::shared_ptr<const Shader> &value, RenderContext &ctx, const TAK::Engine::Model::VertexDataLayout &layout, GLMaterial &material) NOTHROWS
{
    const bool isTextured = (material.isTextured() && material.getTexture());
    bool alphaDiscard = false;
    if(isTextured) {
        GLTexture2 *tex = material.getTexture();
        alphaDiscard = (tex->getFormat() == GL_RGBA);
    }

    RenderAttributes attrs;
    if (isTextured)
        attrs.textureIds[0u] = 1;
    attrs.opaque = !alphaDiscard;
    attrs.colorPointer = (layout.attributes & TEVA_Color) != 0;
    attrs.normals = (layout.attributes & TEVA_Normal) != 0;
    attrs.lighting = (layout.attributes & TEVA_Normal) != 0;

    Shader_get(value, ctx, attrs);
    return TE_Ok;
}
