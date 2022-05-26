#pragma once

#include "renderer/GL.h"
#include "GLBatchPointBuffer.h"

using namespace TAK::Engine::Util;

using namespace TAK::Engine::Math;

#define POINT_ELEMENTS_PER_VERTEX 5u // { float x, float y, float z, float u, float v }
#define POINT_VERTEX_SIZE 20u // { float x, float y, float z, float u, float v }

// uncomment to turn on explicit double buffered VBOs
//#define EXP__DOUBLE_BUFFER_VBO

// uncomment to turn on update via glBufferData to discard old VBO content and
// reset to 'vertices' as opposed to using glBufferSubData to update region of
// existing VBO
//#define EXP__BUFFERDATA_UPDATE

namespace TAK {
    namespace Engine {
        namespace Renderer {
            namespace Feature {

                // default to storing 64k points; adjust as appropriate
                const int GLBatchPointBuffer::DEFAULT_BUFFER_SIZE = 0xFFFF * POINT_VERTEX_SIZE;

                GLBatchPointBuffer::GLBatchPointBuffer() :
                    vbo(0),
                    bufferSize(0),
                    dirty(true)
                {
                    // reserve the buffer size up front
                    vertices.reserve(DEFAULT_BUFFER_SIZE/POINT_VERTEX_SIZE*POINT_ELEMENTS_PER_VERTEX);
                }

                GLBatchPointBuffer::~GLBatchPointBuffer()
                {
                    if (front.handle) {
                        glDeleteBuffers(1, &front.handle);
                        front.handle = 0u;
                        front.capacity = 0u;
                    }
                    if (back.handle) {
                        glDeleteBuffers(1, &back.handle);
                        back.handle = 0u;
                        back.capacity = 0u;
                    }
                }

                void GLBatchPointBuffer::invalidate()
                {
#if 0
                    glBindBuffer(GL_ARRAY_BUFFER, vbo);

                    auto iter = invalidPoints.begin();

                    while (iter != invalidPoints.end())
                    {
                        GLuint offset = (*iter).bufferOffset;

                        memset(&vertices[offset], 0, POINT_VERTEX_SIZE);
                        glBufferSubData(GL_ARRAY_BUFFER, offset, POINT_VERTEX_SIZE, 0);
                        offsetStack.push(offset);

                        ++iter;
                    }

                    glBindBuffer(GL_ARRAY_BUFFER, 0);

                    invalidPoints = points;
                    points.clear();
#endif
                }

                void GLBatchPointBuffer::validate(const GLMapView2 & view, GLBatchPoint3 * point, GLTextureAtlas * iconAtlas, const  TAK::Engine::Math::Matrix2 & invLocalFrame)
                {
                    BufferedPoint * bufferedPoint = nullptr;

                    auto iter = invalidPoints.begin();

                    while (iter != invalidPoints.end())
                    {
                        if ((*iter).batchPoint == point)
                        {
                            points.push_back(*iter);
                            bufferedPoint = &points.back();
                            invalidPoints.erase(iter);
                            break;
                        }

                        ++iter;
                    }
                    
                    if (bufferedPoint)
                    {
                        if (bufferedPoint->batchPoint->validateProjectedLocation(view))
                            return;

                        // point is out of sync with buffer contents
                        bufferedPoint->dirty = true;
                    }
                    else
                    {
                        size_t offset = vertices.size();

                        // look for an available offset to upload the data to
                        if (!offsetStack.empty())
                        {
                            offset = offsetStack.top();
                            offsetStack.pop();
                        }
                        else 
                        {
                            // no offset is available, increase the buffer size to make room
                            if (vertices.capacity() < (offset + POINT_ELEMENTS_PER_VERTEX))
                                vertices.reserve(vertices.capacity() + DEFAULT_BUFFER_SIZE);
                            vertices.resize(vertices.size() + POINT_ELEMENTS_PER_VERTEX);
                        }

                        // add the point to the list
                        points.push_back(BufferedPoint(point, static_cast<GLuint>(offset)));
                        bufferedPoint = &points.back();
                        bufferedPoint->batchPoint->validateProjectedLocation(view);
                    }

                    dirty = true;

                    auto relativeScaling = static_cast<float>(1.0f / view.pixelDensity);

                    int textureSize = static_cast<int>(std::ceil(iconAtlas->getTextureSize() * relativeScaling));
                    int iconSize = iconAtlas->getImageWidth(0);
                    int iconIndex = point->textureIndex;

                    auto fTextureSize = static_cast<float>(textureSize);

                    int numIconsX = textureSize / iconSize;

                    auto iconX = static_cast<float>((iconIndex % numIconsX) * iconSize);
                    auto iconY = static_cast<float>((iconIndex / numIconsX) * iconSize);

                    float * vertexPtr = &vertices[bufferedPoint->bufferOffset];
                                        
                    Point2<double> scratchPoint;
                    view.scene.forwardTransform.transform(&scratchPoint, point->posProjected);
                    point->screen_x = scratchPoint.x;
                    point->screen_y = scratchPoint.y;

                    Point2<double> xyz;
                    invLocalFrame.transform(&xyz, point->posProjected);

                    (*vertexPtr++) = static_cast<float>(xyz.x);
                    (*vertexPtr++) = static_cast<float>(xyz.y);
                    (*vertexPtr++) = static_cast<float>(xyz.z);
                    (*vertexPtr++) = iconX / fTextureSize;
                    (*vertexPtr++) = iconY / fTextureSize;
                }

                const void *GLBatchPointBuffer::get() const NOTHROWS
                {
                    if (vertices.empty())
                        return nullptr;
                    else
                        return &vertices[0];
                }
                void GLBatchPointBuffer::commit()
                {
                    // nothing to draw, set the VBO handle to NULL and return immediately
                    if (vertices.empty()) {
                        vbo = 0;
                        return;
                    }

#ifdef EXP__DOUBLE_BUFFER_VBO
                    vbo_t &update = back;
#else
                    vbo_t &update = front;
#endif
                    // if the VBO to updated has not been generated, generate
                    if (!update.handle)
                        glGenBuffers(1, &update.handle);

                    // bind the VBO to prepare for the update
                    glBindBuffer(GL_ARRAY_BUFFER, update.handle);

                    // NOTE: it's not yet clear what the performance
                    // implications for the various hints are. datasets
                    // employed thus far have reached ~15k points on screen; no
                    // noticeable difference between the various draw modes has
                    // been observed
#ifdef EXP__BUFFERDATA_UPDATE
                    glBufferData(GL_ARRAY_BUFFER, vertices.size() * sizeof(float), &vertices[0], GL_STATIC_DRAW);
                    update.capacity = vertices.size() * sizeof(float);
#else
                    // ensure the buffer has sufficient capacity for the points
                    if (vertices.size() * sizeof(float) > update.capacity)
                    {
                        update.capacity = ((vertices.size() * sizeof(float)) / DEFAULT_BUFFER_SIZE) + 1;
                        update.capacity *= DEFAULT_BUFFER_SIZE;

                        glBufferData(GL_ARRAY_BUFFER, update.capacity, nullptr, GL_STREAM_DRAW);
                    }

                    // update the buffer content
                    glBufferSubData(GL_ARRAY_BUFFER, 0u, vertices.size() * sizeof(float), &vertices[0]);

#endif

#ifdef EXP__DOUBLE_BUFFER_VBO
                    vbo_t swap = back;
                    back = front;
                    front = swap;
#endif
                    vbo = update.handle;

                    glBindBuffer(GL_ARRAY_BUFFER, 0);

                    dirty = false;
                }

                void GLBatchPointBuffer::purge()
                {
                    vertices.clear();
                    points.clear();
                    invalidPoints.clear();
                    offsetStack = std::stack<std::size_t>();
                    dirty = true;
                }

                size_t GLBatchPointBuffer::size()
                {
                    return vertices.size() / POINT_ELEMENTS_PER_VERTEX;
                }

                bool GLBatchPointBuffer::empty()
                {
                    return points.size() == 0 && invalidPoints.size() == 0;
                }

                bool GLBatchPointBuffer::isDirty()
                {
                    return dirty;
                }

            }
        }
    }
}