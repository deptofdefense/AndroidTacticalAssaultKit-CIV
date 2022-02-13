#ifndef TAK_ENGINE_MODEL_MESHTRANSFORMER_H_INCLUDED
#define TAK_ENGINE_MODEL_MESHTRANSFORMER_H_INCLUDED

#include "math/Matrix2.h"
#include "model/Mesh.h"
#include "model/VertexDataLayout.h"
#include "port/Platform.h"
#include "util/ProcessingCallback.h"
#include "util/Error.h"

namespace TAK {
    namespace Engine {
        namespace Model {
            struct ENGINE_API MeshTransformOptions
            {
            public :
                MeshTransformOptions() NOTHROWS;
                MeshTransformOptions(const int srid) NOTHROWS;
                MeshTransformOptions(const int srid, const Math::Matrix2 &localFrame) NOTHROWS;
                MeshTransformOptions(const int srid, const Math::Matrix2 &localFrame, const VertexDataLayout &layout);
                MeshTransformOptions(const int srid, const VertexDataLayout &layout) NOTHROWS;
                MeshTransformOptions(const VertexDataLayout &layout) NOTHROWS;
                MeshTransformOptions(const Math::Matrix2 &localFrame) NOTHROWS;
                MeshTransformOptions(const Math::Matrix2 &localFrame, const VertexDataLayout &layout);
                MeshTransformOptions(const MeshTransformOptions &other) NOTHROWS;
                ~MeshTransformOptions() NOTHROWS;
            public :
                int srid;
                Math::Matrix2Ptr localFrame;
                VertexDataLayoutPtr layout;
            };

            /**
             * Transforms the input mesh to the output using the specified source and destination options.
             *
             * @param value     Returns the transformed mesh
             * @param valueOpts Returns the options associated with the transformed mesh
             * @param src       The source mesh
             * @param srcOpts   The source mesh options, SRID and local frame (if specified). 'layout' is ignored and derived from 'src'.
             * @param dstOpts   The destination mesh options. If not specified,
             *                  <UL>
             *                      <LI>SRID is assumed 'srcOpts.srid'</LI>
             *                      <LI>localFrame is assumed 'srcOpts.localFrame', accounting for SRID</LI>
             *                      <LI>layout is assumed 'src.getVertexDataLayout()'</LI>
             *                  </UL>
             *
             * @return  TE_Ok on success, various codes on failure
             */
            ENGINE_API Util::TAKErr Mesh_transform(MeshPtr &value, MeshTransformOptions *valueOpts, const Mesh &src, const MeshTransformOptions &srcOpts, const MeshTransformOptions &dstOpts, Util::ProcessingCallback *callback) NOTHROWS;

            ENGINE_API Util::TAKErr Mesh_transform(Feature::Envelope2 *value, const Feature::Envelope2 &src, const MeshTransformOptions &srcOpts, const MeshTransformOptions &dstOpts) NOTHROWS;
        }
    }
}
#endif
