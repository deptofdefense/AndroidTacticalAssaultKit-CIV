#ifndef ATAKMAP_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER_H_INCLUDED
#define ATAKMAP_RENDERER_FEATURE_GLBATCHGEOMETRYRENDERER_H_INCLUDED

#include <vector>

#include "util/MemBuffer.h"

#include "renderer/map/GLMapRenderable.h"

namespace atakmap {
    
    namespace util {
        
        template <typename T, typename Comp = std::less<T>>
        class InsertionSortedSet {
        public:
            typedef typename std::vector<T>::iterator iterator;

            void add(const T &item);
            size_t size() const;
            void clear();
            
            iterator begin();
            iterator end();
            
        private:
            std::vector<T> items;
            Comp comp;
        };
        
        template <typename T, typename Comp>
        void InsertionSortedSet<T, Comp>::add(const T &item) {
            typename std::vector<T>::iterator end = items.end();
            typename std::vector<T>::iterator it = items.begin();
            for (; it != end; ++it) {
                if (!comp(item, *it)) {
                    break;
                }
            }
            items.insert(it, item);
        }
        
        template <typename T, typename Comp>
        inline size_t InsertionSortedSet<T, Comp>::size() const {
            return items.size();
        }
        
        template <typename T, typename Comp>
        inline void InsertionSortedSet<T, Comp>::clear() {
            items.clear();
        }
        
        template <typename T, typename Comp>
        inline typename InsertionSortedSet<T, Comp>::iterator InsertionSortedSet<T, Comp>::begin() {
            return items.begin();
        }
        
        template <typename T, typename Comp>
        inline typename InsertionSortedSet<T, Comp>::iterator InsertionSortedSet<T, Comp>::end() {
            return items.end();
        }
    }
    
    
    namespace renderer {
        
        class GLRenderBatch;
        
        namespace map {
            class GLMapView;
        }
        
        namespace feature {
            
            class GLBatchPolygon;
            class GLBatchLineString;
            class GLBatchPoint;
            class GLBatchGeometry;
            
            class GLBatchGeometryRenderer : public atakmap::renderer::map::GLMapRenderable {
            private:
                struct BatchPipelineState {
                    
                    BatchPipelineState()
                    : color(0), lineWidth(0), texId(0), textureUnit(0) { }
                    
                    int color;
                    float lineWidth;
                    int texId;
                    int textureUnit;
                };

                struct VectorProgram {
                    int programHandle;
                    int uProjectionHandle;
                    int uModelViewHandle;
                    int aVertexCoordsHandle;
                    int uColorHandle;

                    const int vertSize;

                    VectorProgram(int vertSize);
                };

            private:
                bool InstanceFieldsInitialized = false;

                void InitializeInstanceFields();


                /*TODO--static System::Collections::Generic::IComparer<GLBatchPoint^> ^POINT_BATCH_COMPARATOR = gcnew ComparatorAnonymousInnerClassHelper();*/

            private:
                /*TODO--class ComparatorAnonymousInnerClassHelper : System::Collections::Generic::IComparer<GLBatchPoint^>
                {
                public:
                    ComparatorAnonymousInnerClassHelper();

                    virtual int Compare(GLBatchPoint ^lhs, GLBatchPoint ^rhs);
                };*/

            private:
                /*TODO- static System::Collections::Generic::IComparer<GLBatchGeometry^> ^FID_COMPARATOR = gcnew ComparatorAnonymousInnerClassHelper2();*/

            private:
                /*TODO-- class ComparatorAnonymousInnerClassHelper2 : System::Collections::Generic::IComparer<GLBatchGeometry^>
                {
                public:
                    ComparatorAnonymousInnerClassHelper2();

                    virtual int Compare(GLBatchGeometry ^lhs, GLBatchGeometry ^rhs);
                };*/

            private:
                static const int PRE_FORWARD_LINES_POINT_RATIO_THRESHOLD = 3;

                static const int MAX_BUFFERED_2D_POINTS = 20000;
                static const int MAX_BUFFERED_3D_POINTS = (MAX_BUFFERED_2D_POINTS * 2) / 3;
                static const int MAX_VERTS_PER_DRAW_ARRAYS = 5000;

                static const int POINT_BATCHING_THRESHOLD = 500;

                //TODO-- static array<float> ^SCRATCH_MATRIX = gcnew array<float>(16);

                /// <summary>
                ///********************************************************************** </summary>

                std::vector<GLBatchPolygon *> polys;
                std::vector<GLBatchLineString *> lines;
                //TODO-- SortedSet<GLBatchPoint^> ^batchPoints = gcnew SortedSet<GLBatchPoint^>(POINT_BATCH_COMPARATOR);
                
                typedef atakmap::util::InsertionSortedSet<GLBatchPoint *> BatchPointsSet;
                BatchPointsSet batchPoints;
                
                std::vector<GLBatchPoint *> labels;
                std::vector<GLBatchPoint *> loadingPoints;
                
                int linesPoints = 0;

                //TODO--SortedSet<GLBatchGeometry^> ^sortedPolys = gcnew SortedSet<GLBatchGeometry^>(FID_COMPARATOR);
                //TODO--SortedSet<GLBatchGeometry^> ^sortedLines = gcnew SortedSet<GLBatchGeometry^>(FID_COMPARATOR);

                /*REF--array<float> ^pointsBuffer = gcnew array<float>(MAX_BUFFERED_2D_POINTS * 2);
                int pointsBufferPosition = 0;
                int pointsBufferLimit = 0;*/
                util::MemBufferT<float> pointsBuffer;
                
                //REF-- array<float> ^pointsVertsTexCoordsBuffer;
                util::MemBufferT<float> pointsVertsTexCoordsBuffer;
                
                /*REF--array<int> ^textureAtlasIndicesBuffer;
                int textureAtlasIndicesBufferPosition = 0;
                int textureAtlasIndicesBufferLimit = 0;*/
                util::MemBufferT<int> textureAtlasIndiciesBuffer;

                BatchPipelineState state;

                GLRenderBatch *batch;

                int textureProgram = 0;
                int tex_uProjectionHandle = 0;
                int tex_uModelViewHandle = 0;
                int tex_uTextureHandle = 0;
                int tex_aTextureCoordsHandle = 0;
                int tex_aVertexCoordsHandle = 0;
                int tex_uColorHandle = 0;

                VectorProgram vectorProgram2d;
                VectorProgram vectorProgram3d;

            public:
                GLBatchGeometryRenderer();
                
                virtual ~GLBatchGeometryRenderer();

                //TODO--virtual System::Collections::Generic::SortedSet<GLBatchPoint^> ^renderablePoints();

                /*REF--property System::Collections::Generic::ICollection<GLBatchGeometry^> ^Batch
                 {
                 virtual void set(System::Collections::Generic::ICollection<GLBatchGeometry^> ^value);
                 }*/
        //TODO--        virtual void setBatch(GLBatchGeometry *value);
                
                virtual void draw(const atakmap::renderer::map::GLMapView *view);

            private:
                void batchDrawLinesProjected(const atakmap::renderer::map::GLMapView *view);

                static void renderLinesBuffers(VectorProgram *vectorProgram, const float *buf, int mode, int numPoints);

                void batchDrawPoints(const atakmap::renderer::map::GLMapView *view);

                void renderPointsBuffers(const atakmap::renderer::map::GLMapView *view);

            public:
                virtual void start();
                virtual void stop();

                virtual void release();

                /// <summary>
                ///*********************************************************************** </summary>

            private:
                static void fillVertexArrays(float *translations, int *texAtlasIndices, int iconSize, int textureSize, float *vertsTexCoords, int count);

                /// <summary>
                /// Expands a buffer containing a line strip into a buffer containing lines.
                /// None of the properties of the specified buffers (e.g. position, limit)
                /// are modified as a result of this method.
                /// </summary>
                /// <param name="size">              The vertex size, in number of elements </param>
                /// <param name="linestrip">         The pointer to the base of the line strip buffer </param>
                /// <param name="linestripPosition"> The position of the linestrip buffer (should
                ///                          always be <code>linestrip.position()</code>). </param>
                /// <param name="lines">             The pointer to the base of the destination
                ///                          buffer for the lines </param>
                /// <param name="linesPosition">     The position of the lines buffer (should always
                ///                          be <code>lines.position()</code>). </param>
                /// <param name="count">             The number of points in the line string to be
                ///                          consumed. </param>
                static void expandLineStringToLines(int size, float *verts, int vertsOff, float *lines, int linesOff, int count);
            };
        }
    }
}

#endif
