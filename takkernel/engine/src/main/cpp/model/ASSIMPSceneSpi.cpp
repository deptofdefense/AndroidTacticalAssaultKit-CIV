
#include "model/ASSIMPSceneSpi.h"

#include <algorithm>
#include <list>
#include <sstream>

#include "assimp/Importer.hpp"
#include "assimp/IOSystem.hpp"
#include "assimp/IOStream.hpp"
#include "assimp/scene.h"
#include "assimp/ProgressHandler.hpp"
#include "util/DataInput2.h"
#include "util/IO2.h"
#include "model/SceneBuilder.h"
#include "model/MeshBuilder.h"
#include "math/Matrix2.h"

using namespace TAK::Engine::Model;

using namespace TAK::Engine::Math;
using namespace TAK::Engine::Util;

#define __EXP_PRETRANSFORM_VERTICES
#define __EXP_MESH_VERTEX_LIMIT (0xFFFF*3u) // 64k triangles

namespace {
    class TAKAssimpIOStream : public Assimp::IOStream {
    public:
        TAKAssimpIOStream(DataInput2Ptr &&inputPtr, const char *vpath, size_t fileSize, ProcessingCallback *cb)
            : inputPtr(std::move(inputPtr)), vpath(vpath), tellPos(0), fileSize(fileSize), callback(cb)
        { }

        static TAKAssimpIOStream *open(const char *vpath, const char *mode, ProcessingCallback *callback) {
            int64_t size = 0;
            TAKErr code = IO_getFileSizeV(&size, vpath);
            if (code != TE_Ok)
                return nullptr;

            if (size > SIZE_MAX) {
                //XXX-- ASSIMP interface limitation: file sizes are size_t
                return nullptr;
            }

            DataInput2Ptr inputPtr(nullptr, nullptr);
            code = IO_openFileV(inputPtr, vpath);
            if (code != TE_Ok)
                return nullptr;

            return new TAKAssimpIOStream(std::move(inputPtr), vpath, (size_t)size, callback);
        }

        ~TAKAssimpIOStream() override { }

        size_t Read(void* buffer, size_t size, size_t count) override {
            size_t numRead = 0;
            const size_t chunkSize = 256u * 1024u; // 256k
            uint8_t chunk[chunkSize];
            auto *outbuf = (uint8_t *)buffer;
            do {
                if (ProcessingCallback_isCanceled(callback))
                    break;
                size_t readThisPass = std::min(chunkSize, (count*size) - numRead);
                size_t actual;
                if (inputPtr->read(chunk, &actual, readThisPass) != TE_Ok)
                    break;
                if (readThisPass != actual) // eof
                    break;                
                memcpy(outbuf+numRead, chunk, actual);
                numRead += actual;
            } while (numRead < (count*size));
            tellPos += numRead;
            return numRead;
        }

        size_t Write(const void* pvBuffer, size_t pSize, size_t pCount) override { 
            /*read-only*/
            return 0;
        }

        aiReturn Seek(size_t offset, aiOrigin origin) override {
            switch (origin) {
            case aiOrigin_SET:
                
                if (offset == tellPos)
                    return aiReturn_SUCCESS;
                
                if (IO_openFileV(inputPtr, vpath.c_str()) != TE_Ok &&
                    inputPtr->skip(offset) != TE_Ok)
                    return aiReturn_FAILURE;

                tellPos = offset;

                break;

            case aiOrigin_CUR:

                if (offset == 0)
                    return aiReturn_SUCCESS;

                if (inputPtr->skip(offset) != TE_Ok)
                    return aiReturn_FAILURE;

                tellPos += offset;

                break;
            case aiOrigin_END:
                if (IO_openFileV(inputPtr, vpath.c_str()) != TE_Ok &&
                    inputPtr->skip(fileSize - offset) != TE_Ok)
                    return aiReturn_FAILURE;

                tellPos = fileSize - offset;

                break;
            }
            return aiReturn_SUCCESS;
        }

        size_t Tell() const override { 
            return tellPos;
        }

        size_t FileSize() const override { 
            return fileSize;
        }
        
        void Flush() override { /*read-only*/ }

    private:
        DataInput2Ptr inputPtr; 
        std::string vpath;
        size_t tellPos;
        size_t fileSize;
        ProcessingCallback *callback;
    };

    class TAKAssimpIOSsytem : public Assimp::IOSystem {
    public:
        TAKAssimpIOSsytem()
        : resourceMapper(nullptr),
          callback(nullptr) {}

        ~TAKAssimpIOSsytem() override
        {}

        TAKErr resourceMapFilePath(TAK::Engine::Port::String &result, const char *filePath) const {

            TAKErr code(TE_Unsupported);

            if (this->resourceMapper) {
                TAK::Engine::Port::String workingString;
                code = IO_getRelativePath(workingString, pathBase.c_str(), filePath);
                TE_CHECKRETURN_CODE(code);

                code = ResourceMapper_getResourceMappingAbsolutePath(result, *this->resourceMapper, workingString.get(), pathBase.c_str());
            }

            return code;
        }

        bool Exists(const char *file) const override {

            TAK::Engine::Port::String mappedResource;
            if (resourceMapFilePath(mappedResource, file) == TE_Ok)
                file = mappedResource.get();

            bool result = false;
            IO_existsV(&result, file);

            return result;
        }

        char getOsSeparator() const override {
            return TAK::Engine::Port::Platform_pathSep();
        }


        Assimp::IOStream* Open(const char *file, const char *mode) override {
            if (ProcessingCallback_isCanceled(callback))
                return nullptr;

            // map resource file
            TAK::Engine::Port::String mappedResource;
            if (resourceMapFilePath(mappedResource, file) == TE_Ok)
                file = mappedResource.get();

            return TAKAssimpIOStream::open(file, mode, callback);
        }

        void Close(Assimp::IOStream* stream) override {
            delete stream;
        }

        std::string pathBase;
        ProcessingCallback *callback;
        const ResourceMapper *resourceMapper;
    };

    class TAKAssimpProgressHandler : public Assimp::ProgressHandler {
    public:
        TAKAssimpProgressHandler(bool hasPosProc)
            : callbacks(nullptr), normalizedPercentage(0.0f), fileStageMult(hasPosProc ? 0.5f : 1.0f) {}

        bool Update(float percentage) override {
            // ASSIMP at times gives > 1.0 values (very odd) and then gives -1.0 at the end.
            if (percentage > 0.0f)
                normalizedPercentage = std::max(normalizedPercentage, std::min(1.0f, percentage));
            if(callbacks->progress)
                callbacks->progress(callbacks->opaque, static_cast<int>(normalizedPercentage * 50), 100);
            return !ProcessingCallback_isCanceled(callbacks);
        }

        void UpdateFileRead(int currentStep, int numberOfSteps) override {

            // XXX-- ASSIMP is clearly bugged here, there is overrun for currentStep.  
            // Worth investigating as fixed in ASSIMP 5, but for now we keep the bar from freezing by assuming
            // the last 20% may be overflow. If not there will be a jump that is acceptible.
            uint64_t uNumOfSteps = *((unsigned int *)&numberOfSteps);
            uint64_t uCurrentStep = *((unsigned int *)&currentStep);
            float base = 0.8f;

            if (uNumOfSteps == 0) {
                Update(fileStageMult);
            } else if (uCurrentStep > uNumOfSteps) {
                uint64_t div = (uCurrentStep / uNumOfSteps);
                float sect = std::min(0.95f, div / 5.f) * 0.1f;
                base += sect;
                uCurrentStep %= uNumOfSteps;
                float curr = (0.02f * ((float)uCurrentStep / (float)uNumOfSteps));
                Update(fileStageMult * (base + curr));
            } else {
                Update(fileStageMult * base * ((float)uCurrentStep / (float)uNumOfSteps));
            }
        }

        void UpdatePostProcess(int currentStep, int numberOfSteps) override {
            float f = numberOfSteps ? currentStep / (float)numberOfSteps : 1.0f;
            Update(f * 0.5f + 0.5f);
        }

        ProcessingCallback *callbacks;
        float normalizedPercentage;
        float fileStageMult;
    };

    class MultiMeshBuilder
    {
    public :
        MultiMeshBuilder() NOTHROWS :
            tedm(TEDM_Triangles),
            tewo(TEWO_Undefined),
            attr(TEVA_Position),
            totalVerts(0u),
            builderVerts(0u)
        {}
    public :
        MeshBuilder &push(std::size_t numVerts = __EXP_MESH_VERTEX_LIMIT) NOTHROWS
        {
            builders.push_back(std::move(std::unique_ptr<MeshBuilder>(new MeshBuilder(tedm, attr))));
            builderVerts = 0u;
            for(std::size_t i = 0u; i < mats.size(); i++)
                builders.back()->addMaterial(mats[i]);
            builders.back()->setWindingOrder(tewo);
            builders.back()->reserveVertices(numVerts);
            return *builders.back();
        }
    public :
        std::list<std::unique_ptr<MeshBuilder>> builders;
        std::size_t totalVerts;
        std::size_t builderVerts;
        DrawMode tedm;
        WindingOrder tewo;
        unsigned attr;
        std::vector<Material> mats;
        
    };

	bool isXUpTransform(const aiMatrix4x4 &t) {
		const aiMatrix4x4 xUp(
			0, -1, 0, 0,
			1, 0, 0, 0,
			0, 0, 1, 0,
			0, 0, 0, 1);
		return t == xUp;
	}

	bool isYUpTransform(const aiMatrix4x4 &t) {
		return t.IsIdentity();
	}

    TAKErr getTransform(Matrix2 &result, bool &isIdentity, const aiNode &node) {
        TAKErr code(TE_Ok);
        const aiMatrix4x4 &t = node.mTransformation;
        result = Matrix2(
            t.a1, t.a2, t.a3, t.a4,
            t.b1, t.b2, t.b3, t.b4,
            t.c1, t.c2, t.c3, t.c4,
            t.d1, t.d2, t.d3, t.d4);
        isIdentity = t.IsIdentity();
        return code;
    }

    struct SceneProcessInfo {

        SceneProcessInfo(const aiScene& scene, ProcessingCallback* callbacks)
            : totalVertexCount(0),
            numMeshesProcessed(0),
            numVerticesProcessed(0),
            meshInstanceCount(scene.mNumMeshes, 0u),
            meshDataPushed(scene.mNumMeshes, false),
            callbacks(callbacks),
            builder(false)
        {}

        SceneProcessInfo(const aiScene& scene, ProcessingCallback* callbacks, const TAK::Engine::Math::Matrix2& rootTransform)
            : totalVertexCount(0),
            numMeshesProcessed(0),
            numVerticesProcessed(0),
            meshInstanceCount(scene.mNumMeshes, 0u),
            meshDataPushed(scene.mNumMeshes, false),
            callbacks(callbacks),
            builder(rootTransform, false)
        {}

        void reportVertexProgress() {
            if (callbacks && callbacks->progress) {
                const double vertProgress = (double)this->numVerticesProcessed / (double)this->totalVertexCount;
                callbacks->progress(callbacks->opaque, 50 + (int)(vertProgress * 50.0), 100);
            }
        }

        size_t totalVertexCount;
        size_t numMeshesProcessed;
        size_t numVerticesProcessed;
        std::map<std::string, int> meshVertCounts;
        std::vector<int> meshInstanceCount;
        std::vector<bool> meshDataPushed;
        ProcessingCallback* callbacks;
        SceneBuilder builder;
        std::map<std::string, MultiMeshBuilder> builders;
    };

    TAKErr preprocessMesh(SceneProcessInfo &info, size_t &totalVertexCount, std::map<std::string, int> &meshVertCounts, std::vector<int> &meshInstanceCount, const char *uri, const aiScene &scene, const aiMesh &mesh) NOTHROWS
    {
        unsigned int materialIndex = mesh.mMaterialIndex;
        std::string textureUri;
        if (materialIndex >= 0 && materialIndex < scene.mNumMaterials) {
            aiMaterial *material = scene.mMaterials[materialIndex];

            // XXX - need to generate materials for each texture type, as applicable
            // XXX - iterate over number of textures for given type

            aiString texturePath;
            aiTextureMapping textureMapping;
            unsigned int uvIndex;
            if (material->GetTexture(aiTextureType_DIFFUSE, 0, &texturePath, &textureMapping, &uvIndex) == aiReturn_SUCCESS) {
                if (texturePath.length) {
                    textureUri = texturePath.C_Str();
                }
            }
        }

        meshVertCounts[textureUri] += mesh.mNumVertices;
        totalVertexCount += mesh.mNumVertices;
        return TE_Ok;
    }

    TAKErr preprocessSceneMeshVertCounts(SceneProcessInfo &info, const char *uri, const aiScene &scene, const aiNode &node) NOTHROWS
    {
        TAKErr code(TE_Ok);
        for (std::size_t i = 0; i < node.mNumMeshes; i++) {
            const int mesh = node.mMeshes[i];
            // skip instanced meshes
            if (info.meshInstanceCount[mesh] > 1)
                continue;
            code = preprocessMesh(info, info.totalVertexCount, info.meshVertCounts, info.meshInstanceCount, uri, scene, *scene.mMeshes[mesh]);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        for (std::size_t i = 0u; i < node.mNumChildren; i++) {
            code = preprocessSceneMeshVertCounts(info, uri, scene, *node.mChildren[i]);
            TE_CHECKBREAK_CODE(code);
        }
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    void preprocessSceneCountMeshInstances(SceneProcessInfo &info, const aiNode &node) {
        for (std::size_t i = 0u; i < node.mNumMeshes; i++)
            info.meshInstanceCount[node.mMeshes[i]]++;
        for (std::size_t i = 0u; i < node.mNumChildren; i++)
            preprocessSceneCountMeshInstances(info, *node.mChildren[i]);
    }

    TAKErr preprocessScene(SceneProcessInfo &info, const char *uri, const aiScene &scene) NOTHROWS
    {
        TAKErr code(TE_Ok);
        // determine all instanced meshes
        preprocessSceneCountMeshInstances(info, *scene.mRootNode);

        code = preprocessSceneMeshVertCounts(info, uri, scene, *scene.mRootNode);
        TE_CHECKRETURN_CODE(code);

        return code;
    }

    /**
     * @param transform A transformation matrix to pre-transform the vertices
     */
    TAKErr addMesh(SceneProcessInfo &p, const char *URI, const aiScene &scene, const std::size_t meshId, const aiMesh &mesh, const Matrix2 *transform, 
        const ResourceMapper *resourceMapper, const bool isInstanced) {

        TAKErr code(TE_Ok);
        
        std::unique_ptr<MultiMeshBuilder, void(*)(const MultiMeshBuilder*)> mmbuilder(nullptr, nullptr);

        float matr = 1.f;
        float matg = 1.f;
        float matb = 1.f;
        float mata = 1.f;

        std::size_t numTexCoords = 0u;
        for (unsigned int i = 0u; i < 8u; ++i)
            if (mesh.HasTextureCoords(i))
                ++numTexCoords;

        int meshVertCount = 0;

        unsigned int materialIndex = mesh.mMaterialIndex;
        if (materialIndex < scene.mNumMaterials) {
            aiMaterial *material = scene.mMaterials[materialIndex];
            aiString texturePath;
            aiTextureMapping textureMapping;
            unsigned int uvIndex;
            TAK::Engine::Port::String textureUri;
            if (material->GetTexture(aiTextureType_DIFFUSE, 0, &texturePath, &textureMapping, &uvIndex) == aiReturn_SUCCESS)
                textureUri = texturePath.data;
   
            int twoSided = 0;
            material->Get(AI_MATKEY_TWOSIDED, twoSided);

            aiColor4D diffuse;
            material->Get(AI_MATKEY_COLOR_DIFFUSE, diffuse);

            matr = diffuse.r;
            matg = diffuse.g;
            matb = diffuse.b;
            mata = diffuse.a;

            // look up builder
            std::string key;
            if (textureUri)
                key = textureUri;
            auto entry = p.builders.find(key);
            if (entry == p.builders.end() || isInstanced) {
                unsigned int vertexAttr = TEVA_Position;
                if (mesh.HasNormals())
                    vertexAttr |= TEVA_Normal;

                for (unsigned int i = 0u; i < 8u; ++i)
                    if (mesh.HasTextureCoords(i))
                        vertexAttr |= (TEVA_TexCoord0 << i);

                if (!isInstanced || mesh.HasVertexColors(0))
                    vertexAttr |= TEVA_Color;

                if (isInstanced)
                    mmbuilder = std::unique_ptr<MultiMeshBuilder, void(*)(const MultiMeshBuilder*)>(new MultiMeshBuilder(), Memory_deleter_const<MultiMeshBuilder>);
                else
                    mmbuilder = std::unique_ptr<MultiMeshBuilder, void(*)(const MultiMeshBuilder*)>(&p.builders[key], Memory_leaker_const<MultiMeshBuilder>);
                mmbuilder->tedm = TEDM_Triangles;
                mmbuilder->attr = vertexAttr;
                mmbuilder->tewo = !twoSided ? TEWO_CounterClockwise : TEWO_Undefined;

                // resolve the texture URI
                if (resourceMapper) {
                    TAKErr rmCode = resourceMapper->getResourceRefTargetPath(textureUri, texturePath.data);
                    if (rmCode != TE_Unsupported)
                        code = rmCode;
                }
                TE_CHECKRETURN_CODE(code);

                // XXX - add material
                Material mat;
                if (textureUri) {
                    mat.textureCoordIndex = uvIndex;

                    // construct the absolute path.
                    {
                        std::ostringstream strm;

                        TAK::Engine::Port::String parent;
                        if(TE_Ok == IO_getParentFile(parent, URI) && parent) {
                            strm << parent;
#ifdef _MSC_VER
                            strm << '\\';
#else
                            strm << '/';
#endif
                        }

                        strm << textureUri;

                        mat.textureUri = strm.str().c_str();
                    }
                }
                mat.twoSided = !!twoSided;

                // if instancing, preserve color
                if (isInstanced) {
                    mat.color = (((unsigned int) (mata * 0xFFu) << 24u) & 0xFF000000u) |
                                (((unsigned int) (matr * 0xFFu) << 16u) & 0xFF0000u) |
                                (((unsigned int) (matg * 0xFFu) << 8u) & 0xFF00u) |
                                (((unsigned int) (matb * 0xFFu)) & 0xFFu);
                }
                mmbuilder->mats.push_back(mat);
                TE_CHECKRETURN_CODE(code);
                
                auto e = p.meshVertCounts.find(key);
                if (e != p.meshVertCounts.end())
                    meshVertCount = e->second;
                TE_CHECKRETURN_CODE(code);

                mmbuilder->push();
            } else {
                mmbuilder = std::unique_ptr<MultiMeshBuilder, void(*)(const MultiMeshBuilder*)>(&entry->second, Memory_leaker_const<MultiMeshBuilder>);
            }
        } else {
            auto entry = p.builders.find("");
            if (entry == p.builders.end() || isInstanced) {
                unsigned int vertexAttr = TEVA_Position;
                if (mesh.HasNormals())
                    vertexAttr |= TEVA_Normal;
                if (!isInstanced || mesh.HasVertexColors(0))
                    vertexAttr |= TEVA_Color;

                if (isInstanced)
                    mmbuilder = std::unique_ptr<MultiMeshBuilder, void(*)(const MultiMeshBuilder*)>(new MultiMeshBuilder(), Memory_deleter_const<MultiMeshBuilder>);
                else
                    mmbuilder = std::unique_ptr<MultiMeshBuilder, void(*)(const MultiMeshBuilder*)>(&p.builders[""], Memory_leaker_const<MultiMeshBuilder>);
                mmbuilder->tedm = TEDM_Triangles;
                mmbuilder->tewo = TEWO_Undefined;
                mmbuilder->attr = vertexAttr;
                
                // add material
                Material mat;
                mat.twoSided = true;

                // if instancing, preserve color
                if (isInstanced) {
                    mat.color = (((unsigned int) (mata * 0xFFu) << 24u) & 0xFF000000u) |
                                (((unsigned int) (matr * 0xFFu) << 16u) & 0xFF0000u) |
                                (((unsigned int) (matg * 0xFFu) << 8u) & 0xFF00u) |
                                (((unsigned int) (matb * 0xFFu)) & 0xFFu);
                }
                mmbuilder->mats.push_back(mat);

                auto e = p.meshVertCounts.find("");
                if (e != p.meshVertCounts.end())
                    meshVertCount = e->second;
                TE_CHECKRETURN_CODE(code);

                mmbuilder->push();
            } else {
                mmbuilder = std::unique_ptr<MultiMeshBuilder, void(*)(const MultiMeshBuilder*)>(&entry->second, Memory_leaker_const<MultiMeshBuilder>);
            }
        }

        if (!meshVertCount)
            meshVertCount = mesh.mNumVertices;


        //TODO-- other types points, etc.
        MeshBuilder *builder = mmbuilder->builders.back().get();
        aiVector3D *verts = mesh.mVertices;
        aiVector3D *norms = mesh.mNormals;
        const std::size_t vertCount = mesh.mNumVertices/3u*3u;
        const std::size_t updateInterval = vertCount/100u;
        for(std::size_t i = 0u; i < vertCount; i++, ++p.numVerticesProcessed) {
            if (ProcessingCallback_isCanceled(p.callbacks))
                return TE_Canceled;

            Point2<double> xyz(verts[i].x,
                verts[i].y,
                verts[i].z);

#ifdef __EXP_PRETRANSFORM_VERTICES
            if (!isInstanced && transform) {
                transform->transform(&xyz, xyz);
            }
#endif

            float nx = 0.f,
                ny = 0.f,
                nz = 0.f;
            if (norms) {
                nx = norms[i].x;
                ny = norms[i].y;
                nz = norms[i].z;
            }
            
            float r = 1.f,
                g = 1.f,
                b = 1.f,
                a = 1.f;

            if (mesh.HasVertexColors(0)) {
                r = mesh.mColors[0][i].r;
                g = mesh.mColors[0][i].g;
                b = mesh.mColors[0][i].b;
                a = mesh.mColors[0][i].a;
            }

            r *= matr;
            g *= matg;
            b *= matb;
            a *= mata;

            float uv[16];
            for (size_t j = 0; j < numTexCoords; ++j) { 
                uv[j * 2] = mesh.mTextureCoords[j][i].x;
                uv[j * 2 + 1] = 1.f - mesh.mTextureCoords[j][i].y;
            }

            if (mmbuilder->builderVerts == __EXP_MESH_VERTEX_LIMIT)
                builder = &mmbuilder->push();
            code = builder->addVertex(xyz.x, xyz.y, xyz.z, uv, nx, ny, nz, r, g, b, a);
            TE_CHECKRETURN_CODE(code);

            mmbuilder->builderVerts++;
            mmbuilder->totalVerts++;

            // push progress update on progress interval
            if (updateInterval > 0 && (i % updateInterval) == 0) {
                p.reportVertexProgress();
            }
        }

        if (isInstanced) {
            for (auto j = mmbuilder->builders.begin(); j != mmbuilder->builders.end(); j++) {
                MeshPtr built_mesh(nullptr, nullptr);
                code = (*j)->build(built_mesh);
                TE_CHECKBREAK_CODE(code);
                code = p.builder.addMesh(std::move(built_mesh), meshId, transform);
                TE_CHECKBREAK_CODE(code);
            }
            TE_CHECKRETURN_CODE(code);
        }
        p.numMeshesProcessed++;

        return code;
    }

    TAKErr addMeshes(SceneProcessInfo &p, const char *URI, const aiScene &scene, const aiNode &node, const Matrix2 *transform, const ResourceMapper *resourceMapper) {
        TAKErr code(TE_Ok);
        for (size_t i = 0; i < node.mNumMeshes; ++i) {
            if (ProcessingCallback_isCanceled(p.callbacks))
                return TE_Canceled;
            const std::size_t instanceCount = p.meshInstanceCount[node.mMeshes[i]];
            const bool isInstanced = (instanceCount > 1);
            // XXX - instancing not yet supported
            if (isInstanced && instanceCount > 64u)
                continue;
            if (isInstanced && p.meshDataPushed[node.mMeshes[i]]) {
                // if the node mesh is an instance mesh and the mesh data is pushed to the scene builder, just add the instance ID
                code = p.builder.addMesh(node.mMeshes[i] + 1u, transform);
                TE_CHECKBREAK_CODE(code);
            } else {
                const aiMesh *mesh = scene.mMeshes[node.mMeshes[i]];
                code = addMesh(p, URI, scene, node.mMeshes[i]+1u, *mesh, transform, resourceMapper, isInstanced);
                TE_CHECKBREAK_CODE(code);
                p.meshDataPushed[node.mMeshes[i]] = true;
            }
            
        }
        TE_CHECKRETURN_CODE(code);
        return code;
    }

    TAKErr buildScene(SceneProcessInfo &p, const char *URI, const aiScene &scene, const aiNode &node, const Matrix2 *transform, const ResourceMapper *resourceMapper) 
    {
        TAKErr code(TE_Ok);

        Matrix2 nodeTransform;
        bool isIdentity;
        code = getTransform(nodeTransform, isIdentity, node);
        TE_CHECKRETURN_CODE(code);

#ifdef __EXP_PRETRANSFORM_VERTICES
        if (transform) {
            nodeTransform.preConcatenate(*transform);
            transform = &nodeTransform;
        } else if (!isIdentity) {
            transform = &nodeTransform;
        }
        code = addMeshes(p, URI, scene, node, transform, resourceMapper);
#else
        code = addMeshes(p, URI, scene, node, NULL, resourceMapper);
#endif
        TE_CHECKRETURN_CODE(code);

        for (size_t i = 0; i < node.mNumChildren; ++i) {
            if (ProcessingCallback_isCanceled(p.callbacks))
                return TE_Canceled;

#ifndef __EXP_PRETRANSFORM_VERTICES
            if (!isIdentity) {
                code = p.builder.push(&nodeTransform);
                TE_CHECKRETURN_CODE(code);
            }
#endif

#ifdef __EXP_PRETRANSFORM_VERTICES
            code = buildScene(p, URI, scene, *node.mChildren[i], transform, resourceMapper);
#else
            code = buildScene(p, URI, scene, *node.mChildren[i], NULL, resourceMapper);
#endif
            TE_CHECKRETURN_CODE(code);

#ifndef __EXP_PRETRANSFORM_VERTICES
            if (!isIdentity) {
                code = p.builder.pop();
                TE_CHECKRETURN_CODE(code);
            }
#endif
        }

        return code;
    }
}

ASSIMPSceneSpi::~ASSIMPSceneSpi()
{ }

const char *ASSIMPSceneSpi::getType() const NOTHROWS {
    return "ASSIMP";
}

int ASSIMPSceneSpi::getPriority() const NOTHROWS {
    return 2;
} 

TAKErr ASSIMPSceneSpi::create(ScenePtr &scene, const char *URI, ProcessingCallback *callbacks, const TAK::Engine::Port::Collection<ResourceAlias> *resourceAliases) const NOTHROWS {

    TAKAssimpIOSsytem ioSys;
    
    ResourceMapper resourceMapper;
    if (resourceAliases) {
        resourceMapper.loadAliases(*resourceAliases);
        ioSys.resourceMapper = &resourceMapper;
    }

    TAK::Engine::Port::String basePath;
    TAKErr code = IO_getParentFile(basePath, URI);
    TE_CHECKRETURN_CODE(code);

    TE_BEGIN_TRAP() {
        ioSys.pathBase = basePath;
        ioSys.callback = callbacks;
    } TE_END_TRAP(code);
    TE_CHECKRETURN_CODE(code);

    class ImporterCleanup
    {
    public :
        ImporterCleanup(Assimp::Importer &i_) NOTHROWS :
            i(i_)
        {}
        ~ImporterCleanup() NOTHROWS
        {
            i.SetIOHandler(nullptr);
            i.SetProgressHandler(nullptr);
        }
    private:
        Assimp::Importer &i;
    };

    Assimp::Importer importer;
    ImporterCleanup cleaner(importer);

    importer.SetIOHandler(&ioSys);

    TAKAssimpProgressHandler progHandler(false);
    if (callbacks) {
        progHandler.callbacks = callbacks;
        importer.SetProgressHandler(&progHandler);
    }

    const aiScene *assimpScene = importer.ReadFile(URI, 0);
    if (!assimpScene) {
        return TE_Err;
    }

    aiNode *rootNode = assimpScene->mRootNode;
    if (!rootNode) {
        return TE_Err;
    }

    TAK::Engine::Math::Matrix2 rootTransform;
    bool isIdentity = true;
#ifndef __EXP_PRETRANSFORM_VERTICES
    code = getTransform(rootTransform, isIdentity, *rootNode);
    TE_CHECKRETURN_CODE(code);
#endif

    if (strstr(URI, ".dae")) {

		Matrix2 yup;
		
		// ASSIMP interprets Collada axis_up differently than Google Earth.
		// The root node is a transformation to y-up coordinate space,
		// but it also appears to interpret explicit node matrices and transform
		// operations differently.
		// We can determine Collada up_axis like so (root node transform):
		//   * Y_UP = Identity
		//   * Z_UP = [1  0  0  0
		//             0  0  1  0
		//             0 -1  0  0
		//             0  0  0  1]
		//   * X_UP = [0 -1  0  0
		//             1  0  0  0
		//             0  0  1  0
		//             0  0  0  1]

		// Try and adapt to Google Earth's intepretation

		if (assimpScene && assimpScene->mRootNode) {
			aiMatrix4x4 &transform = assimpScene->mRootNode->mTransformation;
			if (transform.IsIdentity()) {
				// Y_UP
				rootTransform.scale(-1.0, 1.0, -1.0);
			} else if (isXUpTransform(transform)) {
				// X_UP
				rootTransform.scale(1.0, -1.0, -1.0);
			}
			// Z_UP is the same
		}

		yup.setToRotate(90.0*M_PI / 180.0, 1.0, 0.0, 0.0);
		rootTransform.preConcatenate(yup);
		isIdentity = false;
    }

    SceneProcessInfo 
#ifdef __EXP_PRETRANSFORM_VERTICES
        procInfo(*assimpScene, callbacks);
#else
        procInfo(*assimpScene, callbacks, rootTransform);
#endif
    code = preprocessScene(procInfo, URI, *assimpScene);
    TE_CHECKRETURN_CODE(code);

    code = buildScene(procInfo, URI, *assimpScene, *rootNode, isIdentity ? nullptr : &rootTransform, ioSys.resourceMapper);
    TE_CHECKRETURN_CODE(code);

    for (auto i = procInfo.builders.begin(); i != procInfo.builders.end(); i++) {
        for (auto j = i->second.builders.begin(); j != i->second.builders.end(); j++) {
            MeshPtr mesh(nullptr, nullptr);
            code = (*j)->build(mesh);
            TE_CHECKBREAK_CODE(code);
            code = procInfo.builder.addMesh(std::move(mesh), nullptr);
            TE_CHECKBREAK_CODE(code);
        }
    }
    TE_CHECKRETURN_CODE(code);

    return procInfo.builder.build(scene);
}
