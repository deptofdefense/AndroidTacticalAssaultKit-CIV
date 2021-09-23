#ifndef ATAKMAP_RASTER_DEFAULTDATASETPROJECTION_H_INCLUDED
#define ATAKMAP_RASTER_DEFAULTDATASETPROJECTION_H_INCLUDED

#include "raster/DatasetProjection.h"
#include "math/Matrix.h"
#include "port/Platform.h"
#include "core/ProjectionFactory2.h"

namespace atakmap {
    
    namespace math {
        class ENGINE_API Matrix;
    }
    
    namespace raster {
        class ENGINE_API DefaultDatasetProjection : public DatasetProjection {
        private:
            TAK::Engine::Core::ProjectionPtr2 mapProjection;
            math::Matrix img2proj;
            math::Matrix proj2img;
            
        public:
            DefaultDatasetProjection(int srid, int width, int height,
                                     const core::GeoPoint &ul, const core::GeoPoint &ur, const core::GeoPoint &lr, const core::GeoPoint &ll);
            
            virtual ~DefaultDatasetProjection();
            
        public:
            virtual bool imageToGround(const math::Point<double> &image, core::GeoPoint *ground) const;
            
            virtual bool groundToImage(const core::GeoPoint &ground, math::Point<double> *image) const;
        };
    }
}

#endif // ATAKMAP_RASTER_DEFAULTDATASETPROJECTION_H_INCLUDED
