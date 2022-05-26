#include "raster/DefaultDatasetProjection.h"
#include "core/Projection.h"
#include "math/Matrix.h"

using namespace atakmap::raster;

using namespace atakmap::core;
using namespace atakmap::math;

DefaultDatasetProjection::DefaultDatasetProjection(int srid, int width, int height,
                         const GeoPoint &ul, const GeoPoint &ur, const GeoPoint &lr, const GeoPoint &ll)
        : mapProjection(TAK::Engine::Core::ProjectionFactory2_getProjection(srid)){
    
    if(!this->mapProjection) {
        // EquirectangularMapProjection
        mapProjection = TAK::Engine::Core::ProjectionFactory2_getProjection(4326);
    }
    
    math::Point<double> imgUL(0, 0);
    math::Point<double> projUL;
    this->mapProjection->forward(&ul, &projUL);
    
    math::Point<double> imgUR(width-1, 0);
    math::Point<double> projUR;
    this->mapProjection->forward(&ur, &projUR);
    
    math::Point<double> imgLR(width-1, height-1);
    math::Point<double> projLR;
    this->mapProjection->forward(&lr, &projLR);
    
    math::Point<double> imgLL(0, height-1);
    math::Point<double> projLL;
    this->mapProjection->forward(&ll, &projLL);
    
    try {
        math::Matrix::mapQuads(&imgUL, &imgUR, &imgLR, &imgLL, &projUL, &projUR, &projLR, &projLL, &this->img2proj);

        this->img2proj.createInverse(&proj2img);
    } catch(const std::exception &) {
        //TODO--System.out.println("Failed to invert img2proj, trying manual matrix construction");
        
        math::Matrix::mapQuads(&projUL, &projUR, &projLR, &projLL,
                               &imgUL, &imgUR, &imgLR, &imgLL, &proj2img);
    }
}

DefaultDatasetProjection::~DefaultDatasetProjection() { }

bool DefaultDatasetProjection::imageToGround(const Point<double> &image, GeoPoint *ground) const {
    math::Point<double> projected;
    this->img2proj.transform(&image, &projected);
    this->mapProjection->inverse(&projected, ground);
    //XXX---
    return true;
}

bool DefaultDatasetProjection::groundToImage(const GeoPoint &ground, Point<double> *image) const {
    math::Point<double> projected;
    this->mapProjection->forward(&ground, &projected);
    this->proj2img.transform(&projected, image);
    return true;
}
