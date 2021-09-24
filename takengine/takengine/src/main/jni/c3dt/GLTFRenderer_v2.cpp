#include "GLTFRenderer.h"

#include <iostream>
#include <memory>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#include <tinygltf/tiny_gltf.h>

#include "Matrix.h"

#define RETAIN_VBOS 0
#define BUFFER_OFFSET(i) ((char *)NULL + (i))

// Implementation of the V2 renderer is largely derived from examples/basic

namespace
{

    void bindMesh(tinygltf::Model &model, tinygltf::Mesh &mesh, GLRendererState *modelBinding, GLMeshState *binding);
    // bind models
    void bindModelNodes(tinygltf::Model &model, tinygltf::Node &node, GLRendererState *binding, GLNodeBinding &nodeBinding);

    int getPrimitiveTexture(tinygltf::Model &model, tinygltf::Primitive &primitive);
}

bool Renderer_bindModel(GLRendererState *state, tinygltf::Model &model) {
    if(model.scenes.empty())
        return false;

    GLRendererState &binding = *state;

    int sceneIdx = model.defaultScene;
    if(sceneIdx < 0 || sceneIdx >= model.scenes.size())
        sceneIdx = 0;

    // layout specifier in shader
    GLint vtloc = 0;
    GLint nrmloc = 1;
    GLint uvloc = 2;

    state->gGLProgramState.attribs[state->gGLProgramState.nextAttribAlias] = vtloc;
    state->gGLProgramState.attribAliases["POSITION"] = state->gGLProgramState.nextAttribAlias++;
    state->gGLProgramState.attribs[state->gGLProgramState.nextAttribAlias] = nrmloc;
    state->gGLProgramState.attribAliases["NORMAL"] = state->gGLProgramState.nextAttribAlias++;
    state->gGLProgramState.attribs[state->gGLProgramState.nextAttribAlias] = uvloc;
    state->gGLProgramState.attribAliases["TEXCOORD_0"] = state->gGLProgramState.nextAttribAlias++;

    // bind all Buffer Views to VBOs
    for (size_t i = 0; i < model.bufferViews.size(); ++i) {
        const tinygltf::BufferView &bufferView = model.bufferViews[i];
        if (bufferView.target == 0) {  // TODO impl drawarrays
            //__android_log_print(ANDROID_LOG_WARN, "GLTF", "WARN: bufferView.target is zero");
            continue;  // Unsupported bufferView.
            /*
              From spec2.0 readme:
              https://github.com/KhronosGroup/glTF/tree/master/specification/2.0
                       ... drawArrays function should be used with a count equal to
              the count            property of any of the accessors referenced by the
              attributes            property            (they are all equal for a given
              primitive).
            */
        }

        const tinygltf::Buffer &buffer = model.buffers[bufferView.buffer];
        //std::cout << "bufferview.target " << bufferView.target << std::endl;
        //__android_log_print(ANDROID_LOG_DEBUG, "GLTF", "bufferview.target  0x%X", bufferView.target);

        GLuint vbo;
        glGenBuffers(1, &vbo);
        binding.gBufferState[i] = vbo;
        glBindBuffer(bufferView.target, vbo);

        //std::cout << "buffer.data.size = " << buffer.data.size()
        //          << ", bufferview.byteOffset = " << bufferView.byteOffset
        //          << std::endl;
        //__android_log_print(ANDROID_LOG_DEBUG, "GLTF", "buffer.data.size = %lu, bufferview.byteOffset = %d", (unsigned int)buffer.data.size(), bufferView.byteOffset);

        glBufferData(bufferView.target, bufferView.byteLength,
                     &buffer.data.at(0) + bufferView.byteOffset, GL_STATIC_DRAW);
        glBindBuffer(bufferView.target, 0u);
    }

    // construct a single pixel white texture for untextured meshes
    {
        GLuint texid;
        glGenTextures(1, &texid);
        glBindTexture(GL_TEXTURE_2D, texid);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        unsigned short px = 0xFFFF;

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 1u,
                     1u, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5,
                     &px);

        glBindTexture(GL_TEXTURE_2D, 0);

        binding.textures[-1] = texid;
    }

    for(std::size_t i = 0; i < model.meshes.size(); i++) {
        bindMesh(model, model.meshes[i], &binding, &binding.gMeshState[i]);
    }

    const tinygltf::Scene &scene = model.scenes[sceneIdx];
    binding.nodes.resize(scene.nodes.size());
    for (size_t i = 0; i < scene.nodes.size(); ++i) {
        bindModelNodes(model, model.nodes[scene.nodes[i]], &binding, binding.nodes[i]);
    }

    return true;
}

namespace
{
    void bindMesh(tinygltf::Model &model,
                  tinygltf::Mesh &mesh,
                  GLRendererState *modelBinding,
                  GLMeshState *binding) {

        binding->primitives.reserve(mesh.primitives.size());
        for (size_t i = 0; i < mesh.primitives.size(); ++i) {
            tinygltf::Primitive primitive = mesh.primitives[i];

            GLPrimitive glprimitive;
            glprimitive.texid = 0u;
            glprimitive.mode = primitive.mode;

            if(primitive.indices >= 0 && primitive.indices < model.accessors.size()) {
                tinygltf::Accessor &indexAccessor = model.accessors[primitive.indices];

                glprimitive.indexed = true;

                glprimitive.indices.vb = modelBinding->gBufferState[indexAccessor.bufferView];
                glprimitive.indices.type = indexAccessor.componentType;
                glprimitive.indices.count = indexAccessor.count;
                glprimitive.indices.offset = indexAccessor.byteOffset;
            } else {
                glprimitive.indexed = false;
            }

            // bind all attributes
            glprimitive.accessors.reserve(primitive.attributes.size());
            for (auto &attrib : primitive.attributes) {
                tinygltf::Accessor accessor = model.accessors[attrib.second];

                if(modelBinding->gGLProgramState.attribAliases.find(attrib.first) == modelBinding->gGLProgramState.attribAliases.end())
                    continue;
                if(accessor.bufferView < 0 || accessor.bufferView >= model.bufferViews.size())
                    continue;
                if(modelBinding->gBufferState.find(accessor.bufferView) == modelBinding->gBufferState.end())
                    continue;

                int byteStride =
                        accessor.ByteStride(model.bufferViews[accessor.bufferView]);

                GLAccessor glaccessor;
                glaccessor.vb = modelBinding->gBufferState[accessor.bufferView];

                glaccessor.size = 1;
                if (accessor.type == TINYGLTF_TYPE_SCALAR)
                    glaccessor.size = 1;
                else if (accessor.type == TINYGLTF_TYPE_VEC2)
                    glaccessor.size = 2;
                else if (accessor.type == TINYGLTF_TYPE_VEC3)
                    glaccessor.size = 3;
                else if (accessor.type == TINYGLTF_TYPE_VEC4)
                    glaccessor.size = 4;
                else
                    assert(0);

                glaccessor.attrib = modelBinding->gGLProgramState.attribAliases[attrib.first];
                glaccessor.type = accessor.componentType;
                glaccessor.normalized = GL_FALSE;
                glaccessor.stride = byteStride;
                glaccessor.offset = accessor.byteOffset;
                glaccessor.vb = modelBinding->gBufferState[accessor.bufferView];
                glaccessor.count = accessor.count;

                glprimitive.accessors.push_back(glaccessor);
            }

            // primitive has no usable accessors, skip binding
            if(glprimitive.accessors.empty())
                continue;

            if(primitive.material >= 0 && primitive.material < model.materials.size())
                glprimitive.doubleSided = model.materials[primitive.material].doubleSided;
            else
                glprimitive.doubleSided = false;

            // generate and upload the texture
            const int primitiveMatTexIdx = getPrimitiveTexture(model, primitive);
            if(primitiveMatTexIdx >= 0) {
                // load the texture
                if (modelBinding->textures.find(model.textures[primitiveMatTexIdx].source) ==
                    modelBinding->textures.end()) {
                    GLuint texid;
                    glGenTextures(1, &texid);

                    tinygltf::Texture &tex = model.textures[primitiveMatTexIdx];
                    tinygltf::Image &image = model.images[tex.source];

                    glBindTexture(GL_TEXTURE_2D, texid);
                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                    glTexParameterf(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

                    GLenum format = GL_RGBA;

                    if (image.component == 1) {
                        format = GL_RED;
                    } else if (image.component == 2) {
                        format = GL_RG;
                    } else if (image.component == 3) {
                        format = GL_RGB;
                    } else {
                        // ???
                    }

                    GLenum type = GL_UNSIGNED_BYTE;
                    if (image.bits == 8) {
                        // ok
                    } else if (image.bits == 16) {
                        type = GL_UNSIGNED_SHORT;
                    } else {
                        // ???
                    }
                    void *data = image.image.empty() ? NULL : &image.image.at(0);
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, image.width, image.height, 0,
                                 format, type, data);
                    modelBinding->textures[tex.source] = texid;
                }

                // set the texture ID
                glprimitive.texid = modelBinding->textures[model.textures[primitiveMatTexIdx].source];
            }

            if(!glprimitive.texid)
                glprimitive.texid = modelBinding->textures[-1];

            binding->primitives.push_back(glprimitive);
        }
    }

    // bind models
    void bindModelNodes(tinygltf::Model &model,
                        tinygltf::Node &node, GLRendererState *binding, GLNodeBinding &bnode) {

        float mx[16];
        Matrix_identity(mx);
        if (node.matrix.size() == 16) {
            bnode.matrix.resize(16);
            memcpy(&bnode.matrix.at(0), &node.matrix.at(0), sizeof(double)*16u);
        } else if(!node.scale.empty() || !node.rotation.empty() || !node.translation.empty()) {
            bnode.matrix.resize(16);
            Matrix_identity(&bnode.matrix.at(0));

            // Assume Trans x Rotate x Scale order
            if (node.scale.size() == 3) {
                //glScaled(node.scale[0], node.scale[1], node.scale[2]);
                Matrix_scale(&bnode.matrix.at(0), &bnode.matrix.at(0), node.scale[0], node.scale[1], node.scale[2]);
            }

            if (node.rotation.size() == 4) {
                //glRotated(node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3]);
                Matrix_rotate(&bnode.matrix.at(0), &bnode.matrix.at(0), node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3]);
            }

            if (node.translation.size() == 3) {
                //glTranslated(node.translation[0], node.translation[1], node.translation[2]);
                Matrix_translate(&bnode.matrix.at(0), &bnode.matrix.at(0), node.translation[0], node.translation[1], node.translation[2]);
            }
        }

        auto it = binding->gMeshState.find(node.mesh);
        if (it != binding->gMeshState.end()) {
            bnode.meshes.push_back(it->second);
        }

        bnode.children.resize(node.children.size());
        for (size_t i = 0; i < node.children.size(); i++) {
            bindModelNodes(model, model.nodes[node.children[i]], binding, bnode.children[i]);
        }
    }

    int getPrimitiveTexture(tinygltf::Model &model, tinygltf::Primitive &primitive)
    {
        if(model.textures.empty())
            return -1; // no textures
        if(primitive.material < 0)
            return -1; // primitive has no material
        if(model.materials.size() < primitive.material)
            return -1; // material is not valid
        const int primitiveMatTexIdx = model.materials[primitive.material].pbrMetallicRoughness.baseColorTexture.index;
        if(primitiveMatTexIdx < 0)
            return -1;
        if(model.textures.size() < primitiveMatTexIdx)
            return -1; // texture is not valid
        return primitiveMatTexIdx;
    }
}
