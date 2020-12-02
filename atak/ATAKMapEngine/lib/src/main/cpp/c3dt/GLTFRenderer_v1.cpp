#include "GLTFRenderer.h"

#include <map>
#include <memory>
#include <string>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#endif

#define tinygltf tinygltfloader
#define TAK_TINYGLTFLOADER_MODS
#include <tinygltfloader/tiny_gltf_loader.h>
#undef tinygltf

#include "Matrix.h"

#define BUFFER_OFFSET(i) ((char *)NULL + (i))

#define CheckGLErrors(desc)                                                   \
  {                                                                           \
    GLenum e = glGetError();                                                  \
    if (e != GL_NO_ERROR) {                                                   \
      printf("OpenGL error in \"%s\": %d (%d) %s:%d\n", desc, e, e, __FILE__, \
             __LINE__);                                                       \
      exit(20);                                                               \
    }                                                                         \
  }

using namespace tinygltfloader;

namespace {
    void SetupMeshState(GLRendererState &rstate, Scene &scene);
    void BindNodes(GLRendererState &rstate, const Scene &scene, const Node &node, GLNodeBinding &bnode);
    void DrawMesh(GLRendererState &rstate, const GLMeshState &mesh);
    void DrawNode(GLRendererState &rstate, const GLNodeBinding &node, const GLuint u_xform, const float *xform);
}

bool Renderer_bindModel(GLRendererState *state, Scene &scene)
{
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

    SetupMeshState(*state, scene);

    auto it = scene.scenes.find(scene.defaultScene);
    if (it != scene.scenes.end()) {
        state->nodes.reserve(it->second.size());
        for (size_t i = 0; i < it->second.size(); i++) {
            auto node_it = scene.nodes.find((it->second)[i]);
            if (node_it == scene.nodes.end()) continue;

            GLNodeBinding bnode;
            BindNodes(*state, scene, node_it->second, bnode);
            state->nodes.push_back(bnode);
        }
    }

    return true;
}

namespace {
    void SetupMeshState(GLRendererState &rstate, Scene &scene) {
        // Buffer
        {
            std::map<std::string, BufferView>::const_iterator it(
                    scene.bufferViews.begin());
            std::map<std::string, BufferView>::const_iterator itEnd(
                    scene.bufferViews.end());

            for (; it != itEnd; it++) {
                const BufferView &bufferView = it->second;
                if (bufferView.target == 0) {
                    //std::cout << "WARN: bufferView.target is zero" << std::endl;
#ifdef __ANDROID__
                    __android_log_print(ANDROID_LOG_WARN, "Cesium3DTiles", "WARN: bufferView.target is zero");
#endif
                    continue;  // Unsupported bufferView.
                }

                const Buffer &buffer = scene.buffers[bufferView.buffer];
                GLuint vb;
                glGenBuffers(1, &vb);
                glBindBuffer(bufferView.target, vb);
                //std::cout << "buffer.size= " << buffer.data.size()
                //          << ", byteOffset = " << bufferView.byteOffset << std::endl;
                glBufferData(bufferView.target, bufferView.byteLength,
                             &buffer.data.at(0) + bufferView.byteOffset, GL_STATIC_DRAW);
                glBindBuffer(bufferView.target, 0);

                rstate.gBufferState[rstate.nextBufferAlias] = vb;
                rstate.bufferAliases[it->first] = rstate.nextBufferAlias++;
            }
        }

        // Texture
        {
            // construct a single pixel white texture for untextured meshes
            GLuint defaultTexId;
            {
                glGenTextures(1, &defaultTexId);
                glBindTexture(GL_TEXTURE_2D, defaultTexId);
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

                rstate.textures[rstate.nextTextureAlias] = defaultTexId;
                rstate.textureAliases[""] = rstate.nextTextureAlias++;
            }

            std::map<std::string, Mesh>::const_iterator it(
                    scene.meshes.begin());
            std::map<std::string, Mesh>::const_iterator itEnd(
                    scene.meshes.end());

            for (; it != itEnd; it++) {
                const Mesh &mesh = it->second;

                GLMeshState &glmesh = rstate.gMeshState[rstate.nextMeshAlias];
                rstate.meshAliases[it->first] = rstate.nextMeshAlias++;

                for (size_t primId = 0; primId < mesh.primitives.size(); primId++) {
                    const Primitive &primitive = mesh.primitives[primId];

                    GLPrimitive glprimitive;
                    glprimitive.indexed = false;
                    glprimitive.shared_vbo = false;
                    glprimitive.doubleSided = false;
                    glprimitive.texid = 0u;

                    for (auto pit = primitive.attributes.begin(); pit != primitive.attributes.end(); pit++) {
                        assert(scene.accessors.find(pit->second) != scene.accessors.end());
                        const Accessor &accessor = scene.accessors[pit->second];

                        // check attribute is utilized
                        if(rstate.gGLProgramState.attribAliases.find(pit->first) == rstate.gGLProgramState.attribAliases.end())
                            continue;

                        // check bufferview is present
                        if(rstate.bufferAliases.find(accessor.bufferView) == rstate.bufferAliases.end())
                            continue;

                        GLAccessor glaccessor;
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

                        glaccessor.attrib = rstate.gGLProgramState.attribs[rstate.gGLProgramState.attribAliases[pit->first]];
                        glaccessor.type = accessor.componentType;
                        glaccessor.normalized = GL_FALSE;
                        glaccessor.stride = accessor.byteStride;
                        glaccessor.offset = accessor.byteOffset;
                        glaccessor.vb = rstate.gBufferState[rstate.bufferAliases[accessor.bufferView]];
                        glaccessor.count = accessor.count;

                        glprimitive.accessors.push_back(glaccessor);
                    }

                    // primitive has no usable accessors, skip binding
                    if(glprimitive.accessors.empty())
                        continue;

                    glprimitive.mode = -1;
                    if (primitive.mode == TINYGLTF_MODE_TRIANGLES) {
                        glprimitive.mode = GL_TRIANGLES;
                    } else if (primitive.mode == TINYGLTF_MODE_TRIANGLE_STRIP) {
                        glprimitive.mode = GL_TRIANGLE_STRIP;
                    } else if (primitive.mode == TINYGLTF_MODE_TRIANGLE_FAN) {
                        glprimitive.mode = GL_TRIANGLE_FAN;
                    } else if (primitive.mode == TINYGLTF_MODE_POINTS) {
                        glprimitive.mode = GL_POINTS;
                    } else if (primitive.mode == TINYGLTF_MODE_LINE) {
                        glprimitive.mode = GL_LINES;
                    } else if (primitive.mode == TINYGLTF_MODE_LINE_LOOP) {
                        glprimitive.mode = GL_LINE_LOOP;
                    } else {
                        assert(0);
                    }

                    glprimitive.indexed = (scene.accessors.find(primitive.indices) != scene.accessors.end());
                    if(glprimitive.indexed) {
                        const Accessor &indexAccessor = scene.accessors[primitive.indices];
                        // check bufferview is present
                        if(rstate.bufferAliases.find(indexAccessor.bufferView) == rstate.bufferAliases.end())
                            continue;

                        glprimitive.indices.vb = rstate.gBufferState[rstate.bufferAliases[indexAccessor.bufferView]];
                        glprimitive.indices.type = indexAccessor.componentType;
                        glprimitive.indices.count = indexAccessor.count;
                        glprimitive.indices.offset = indexAccessor.byteOffset;
                    }

                    if (!primitive.material.empty()) {
                        Material &mat = scene.materials[primitive.material];
                        // printf("material.name = %s\n", mat.name.c_str());
                        if (mat.values.find("diffuse") != mat.values.end()) {
                            Parameter &diffuse = mat.values["diffuse"];
                            std::string diffuseTexName = diffuse.string_value;
                            if (rstate.textureAliases.find(diffuseTexName) != rstate.textureAliases.end()) {
                                glprimitive.texid = rstate.textures[rstate.textureAliases[diffuseTexName]];
                            } else if (scene.textures.find(diffuseTexName) !=
                                       scene.textures.end()) {
                                Texture &tex = scene.textures[diffuseTexName];
                                if (scene.images.find(tex.source) != scene.images.end()) {
                                    Image &image = scene.images[tex.source];
                                    GLuint texId;
                                    glGenTextures(1, &texId);
                                    glBindTexture(tex.target, texId);
                                    glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
                                    glTexParameterf(tex.target, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                                    glTexParameterf(tex.target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

                                    // Ignore Texture.fomat.
                                    GLenum format = GL_RGBA;
                                    if (image.component == 3) {
                                        format = GL_RGB;
                                    }
                                    glTexImage2D(tex.target, 0, format, image.width,
                                                 image.height, 0, format, tex.type,
                                                 &image.image.at(0));

                                    glBindTexture(tex.target, 0);

                                    rstate.textures[rstate.nextTextureAlias] = texId;
                                    rstate.textureAliases[diffuseTexName] = rstate.nextTextureAlias++;
                                    glprimitive.texid = texId;
                                }
                            }
                        }
                    }

                    // if the mesh doesn't have a texture, apply the default white
                    if(!glprimitive.texid)
                        glprimitive.texid = defaultTexId;

                    // emit completed primitive binding
                    glmesh.primitives.push_back(glprimitive);
                }
            }
        }
    }

    void BindNodes(GLRendererState &rstate, const Scene &scene, const Node &node, GLNodeBinding &bnode)
    {
//glPushMatrix();
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

        //std::cout << "node " << node.name << ", Meshes " << node.meshes.size() << std::endl;
        for (size_t i = 0; i < node.meshes.size(); i++) {
            auto it = rstate.meshAliases.find(node.meshes[i]);
            if (it != rstate.meshAliases.end()) {
                bnode.meshes.push_back(rstate.gMeshState[it->second]);
            }
        }

        // Draw child nodes.
        for (size_t i = 0; i < node.children.size(); i++) {
            auto it = scene.nodes.find(node.children[i]);
            if (it != scene.nodes.end()) {
                GLNodeBinding child;
                BindNodes(rstate, scene, it->second, child);
                bnode.children.push_back(child);
            }
        }
    }

    void DrawMesh(GLRendererState &rstate, const GLMeshState &glmesh) {
#if 0
        if (rstate.gGLProgramState.uniforms["diffuseTex"] >= 0) {
            glUniform1i(rstate.gGLProgramState.uniforms["diffuseTex"], 0);  // TEXTURE0
        }

        if (rstate.gGLProgramState.uniforms["isCurvesLoc"] >= 0) {
            glUniform1i(rstate.gGLProgramState.uniforms["isCurvesLoc"], 0);
        }
#endif

        for (size_t i = 0; i < glmesh.primitives.size(); i++) {
            const GLPrimitive &primitive = glmesh.primitives[i];

            //if (primitive.indices.empty()) return;

            std::vector<GLuint> activeAttribs;
            activeAttribs.reserve(primitive.accessors.size());

            // Assume TEXTURE_2D target for the texture object.
            glBindTexture(GL_TEXTURE_2D, primitive.texid);

            for (const GLAccessor &accessor : primitive.accessors) {
                glBindBuffer(GL_ARRAY_BUFFER, accessor.vb);

                // it->first would be "POSITION", "NORMAL", "TEXCOORD_0", ...
                glVertexAttribPointer(accessor.attrib, accessor.size,
                                      accessor.type, accessor.normalized,
                                      accessor.stride,
                                      BUFFER_OFFSET(accessor.offset));

                glEnableVertexAttribArray(accessor.attrib);
                activeAttribs.push_back(accessor.attrib);
            }

            if(primitive.indexed) {
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, primitive.indices.vb);
                glDrawElements(primitive.mode, primitive.indices.count, primitive.indices.type,
                               BUFFER_OFFSET(primitive.indices.offset));

                // unbind the index buffer
                glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
            } else {
                glDrawArrays(primitive.mode, 0, primitive.accessors[0].count);
            }
            // disable the attributes enabled for draw
            for(auto attrib_id : activeAttribs)
                glDisableVertexAttribArray(attrib_id);

            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
    }

// Hierarchically draw nodes
    void DrawNode(GLRendererState &rstate, const GLNodeBinding &node, const GLuint u_xform, const float *xform) {
        // Apply xform

        //glPushMatrix();
        const float *node_xform = xform;
        float mx[16];
        Matrix_identity(mx);
        if (node.matrix.size() == 16) {
            // Use `matrix' attribute
            //glMultMatrixd(node.matrix.data());
            if(xform)
                Matrix_concatenate(mx, xform, node.matrix.data());
            else
                Matrix_concatenate(mx, mx, node.matrix.data());
            node_xform = mx;
        }

        // upload the node transform if it has been modified
        if(node_xform != xform)
            glUniformMatrix4fv(u_xform, 1u, false, node_xform);

        //std::cout << "node " << node.name << ", Meshes " << node.meshes.size() << std::endl;

        for (size_t i = 0; i < node.meshes.size(); i++) {
            DrawMesh(rstate, node.meshes[i]);
        }

        // Draw child nodes.
        for (size_t i = 0; i < node.children.size(); i++) {
            DrawNode(rstate, node.children[i], u_xform, node_xform);
        }

        //glPopMatrix();
        if(node_xform != xform) {
            if(!xform) {
                Matrix_identity(mx);
                xform = mx;
            }
            glUniformMatrix4fv(u_xform, 1u, false, xform);
        }
    }
}
