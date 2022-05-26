#define CATCH_CONFIG_RUNNER
#define SRID_EQUIRECTANGULAR 4326
#define SRID_WEBMERCATOR 3857
#define SRID_ECEFWSG84 4978
#define SRID_INVALID 666

#include "catch.h"
#include "core/MapSceneModel.h"
#include "core/ProjectionFactory.h"
#include "math/Ellipsoid.h"
#include "math/Plane.h"

using namespace atakmap::core;
using namespace atakmap::math;

int main(int argc, char* const argv[])
{
    int result = Catch::Session().run(argc, argv);
    std::cin.get();
    return result;
}

void tearDown(AtakMapView* view, MapSceneModel* model, Projection* proj)
{
    delete view;
    delete model;
    delete proj;
}

/* 
 * CONVERTERS USED FOR FORWARD/INVERSE TRANSFORMATION TESTS
 * ECEF: http://www.oc.nps.edu/oc2902w/coord/llhxyz.htm
 * WebMercator: http://tool-online.com/en/coordinate-converter.php
 * Equirectangular: None
 */

TEST_CASE("COMMON SCENE MODEL CREATION: initScene(...)", "[mapscenemodel][initialziation][sunny]")
{

    //if 3D projection -> Earth represented as an ellipsoid
    AtakMapView* view = new AtakMapView(240, 320, 240);
    view->setProjection(SRID_ECEFWSG84);
    MapSceneModel* model = view->createSceneModel();
    CHECK_FALSE(!(std::is_base_of<Ellipsoid, decltype(model->earth)>::value));
    
    //ecef origin must be (0, 0, 0)
    Ellipsoid* eModel = dynamic_cast<Ellipsoid*>(model->earth);
    CHECK(eModel->center.x == 0); CHECK(eModel->center.y == 0); CHECK(eModel->center.z == 0);
    delete eModel;

    //if !3D projection -> Earth represented as a Plane
    view->setProjection(SRID_EQUIRECTANGULAR);
    model = view->createSceneModel();
    CHECK_FALSE(!(std::is_base_of<Plane, decltype(model->earth)>::value));

    view->setProjection(SRID_WEBMERCATOR);
    model = view->createSceneModel();
    CHECK_FALSE(!(std::is_base_of<Plane, decltype(model->earth)>::value));


    tearDown(view, model, NULL);
}

//Point coordinates currently in meters
//May need to account for scale factor of pixels
TEST_CASE("UNCOMMON ECEF forward(...)", "[forward][mapscenemodel][rainy]") {
    AtakMapView* ecefView = new AtakMapView(240, 320, 240);
    ecefView->setProjection(SRID_ECEFWSG84);
    MapSceneModel* ecefModel = ecefView->createSceneModel();

    GeoPoint* geo = new GeoPoint();
    Point<float>* point;

    SECTION("MAGNETIC N/S POLES") 
    {
        //Magnetic north pole
        geo->latitude = 75.7667;
        geo->longitude = 99.7833;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(-267314).epsilon(10.00));
        CHECK(point->y == Approx(1550275).epsilon(10.00));
        CHECK(point->z == Approx(6160362).epsilon(10.00));

        //Magnetic south pole
        geo->latitude = -64.28;
        geo->longitude = 136.59;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(-2016271).epsilon(10.00));
        CHECK(point->y == Approx(1907359).epsilon(10.00));
        CHECK(point->z == Approx(-572333).epsilon(10.00));
    }

    SECTION("GEOGRAPHIC N/S POLES")
    {
        //Geographic north pole
        geo->latitude = 90;
        geo->longitude = 0;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10.00));
        CHECK(point->y == Approx(0).epsilon(10.00));
        CHECK(point->z == Approx(6356752).epsilon(10.00));

        //Geographic south pole
        geo->latitude = -90;
        geo->longitude = -0;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10.00));
        CHECK(point->y == Approx(0).epsilon(10.00));
        CHECK(point->z == Approx(-6356752).epsilon(10.00));
    }
    
    SECTION("EQUATOR / PRIME MERIDIAN INTERSECTION")
    {
        geo->latitude = 0;
        geo->longitude = 0;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(6378137).epsilon(10.00));
        CHECK(point->y == Approx(0).epsilon(10.00));
        CHECK(point->z == Approx(0).epsilon(10.00));
    }

    SECTION("MAX/MAX, MIN/MIN LAT/LONGS")
    {
        geo->latitude = ecefModel->projection->getMaxLatitude(); //should be 90
        geo->longitude = ecefModel->projection->getMaxLongitude(); //should be 180

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10));
        CHECK(point->y == Approx(0).epsilon(10));
        CHECK(point->z == Approx(6356752).epsilon(10));

        geo->latitude = ecefModel->projection->getMinLatitude(); //should be -90
        geo->longitude = ecefModel->projection->getMinLongitude(); //should be -180

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10));
        CHECK(point->y == Approx(0).epsilon(10));
        CHECK(point->z == Approx(-6356752).epsilon(10));
    }
}

TEST_CASE("COMMON ECEF forward(...)", "[forward][mapscenemodel][sunny]") {
    AtakMapView* ecefView = new AtakMapView(240, 320, 240);
    ecefView->setProjection(SRID_ECEFWSG84);
    MapSceneModel* ecefModel = ecefView->createSceneModel();

    GeoPoint* geo = new GeoPoint();
    Point<float>* point;

    SECTION("MIDPOINT(EQUATOR, MIN/MAX LAT)")
    {
        geo->latitude = 45;
        geo->longitude = 0;
        
        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(4517591).epsilon(10.00));
        CHECK(point->y == Approx(0).epsilon(10.00));
        CHECK(point->z == Approx(4487348).epsilon(10.00));

        geo->latitude = -45;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(4517591).epsilon(10.00));
        CHECK(point->y == Approx(0).epsilon(10.00));
        CHECK(point->z == Approx(-4487348).epsilon(10.00));
    }

    SECTION("MIDPOINT(PRIME_MERIDIAN, MIN/MAX LAT)") 
    {
        geo->latitude = 0;
        geo->longitude = 90;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10.00));
        CHECK(point->y == Approx(6378137).epsilon(10.00));
        CHECK(point->z == Approx(0).epsilon(10.00));

        geo->longitude = -90;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10.00));
        CHECK(point->y == Approx(-6378137).epsilon(10.00));
        CHECK(point->z == Approx(0).epsilon(10.00));
    }

    //1 Point in each quarter of the globe
    SECTION("MIDPOINT(PRIME_MERIDIAN + EQUATOR, MIN/MAX LAT)")
    {
        //Top right quarter
        geo->latitude = 45;
        geo->longitude = 90;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10.00));
        CHECK(point->y == Approx(4517591).epsilon(10.00));
        CHECK(point->z == Approx(4487348).epsilon(10.00));
    
        //Top left quarter
        geo->latitude = 45;
        geo->longitude = -90;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10.00));
        CHECK(point->y == Approx(-4517591).epsilon(10.00));
        CHECK(point->z == Approx(4487348).epsilon(10.00));

        //Bottom left quarter
        geo->latitude = -45;
        geo->longitude = -90;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10.00));
        CHECK(point->y == Approx(-4517591).epsilon(10.00));
        CHECK(point->z == Approx(-4487348).epsilon(10.00));

        //Bottom right quarter
        geo->latitude = -45;
        geo->longitude = 90;

        ecefModel->forward(geo, point);
        CHECK(point->x == Approx(0).epsilon(10.00));
        CHECK(point->y == Approx(4517591).epsilon(10.00));
        CHECK(point->z == Approx(-4487348).epsilon(10.00));
    }
}

TEST_CASE("UNCOMMON EQUIRECTANGULAR forward(...)", "[forward][mapscenemodel][rainy]") {
    AtakMapView* equiView = new AtakMapView(240, 320, 240);
    equiView->setProjection(SRID_EQUIRECTANGULAR);
    MapSceneModel* equiModel = equiView->createSceneModel();

    GeoPoint* geo = new GeoPoint();
    Point<float>* point;

    SECTION("MAGNETIC N/S POLES")
    {
        //Magnetic north pole
        geo->latitude = 75.7667;
        geo->longitude = 99.7833;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);

        //Magnetic south pole
        geo->latitude = -64.28;
        geo->longitude = 136.59;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);
    }

    SECTION("GEOGRAPHIC N/S POLES")
    {
        //Geographic north pole
        geo->latitude = 90;
        geo->longitude = 0;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);

        //Geographic south pole
        geo->latitude = -90;
        geo->longitude = -0;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);
    }

    SECTION("EQUATOR / PRIME MERIDIAN INTERSECTION")
    {
        geo->latitude = 0;
        geo->longitude = 0;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);
    }

    SECTION("MAX/MAX, MIN/MIN LAT/LONGS")
    {
        geo->latitude = equiModel->projection->getMaxLatitude(); //should be 90
        geo->longitude = equiModel->projection->getMaxLongitude(); //should be 180

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);

        geo->latitude = equiModel->projection->getMinLatitude(); //should be -90
        geo->longitude = equiModel->projection->getMinLongitude(); //should be -180

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);
    }
}

TEST_CASE("COMMON EQUIRECTANGULAR forward(...)", "[forward][mapscenemodel][rainy]")
{
    AtakMapView* equiView = new AtakMapView(240, 320, 240);
    equiView->setProjection(SRID_EQUIRECTANGULAR);
    MapSceneModel* equiModel = equiView->createSceneModel();

    GeoPoint* geo = new GeoPoint();
    Point<float>* point;

    SECTION("MIDPOINT(EQUATOR, MIN/MAX LAT)")
    {
        geo->latitude = 45;
        geo->longitude = 0;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);

        geo->latitude = -45;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);
    }

    SECTION("MIDPOINT(PRIME_MERIDIAN, MIN/MAX LAT)")
    {
        geo->latitude = 0;
        geo->longitude = 90;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);

        geo->longitude = -90;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);
    }

    //1 Point in each quarter of the globe
    SECTION("MIDPOINT(PRIME_MERIDIAN + EQUATOR, MIN/MAX LAT)")
    {
        //Top right quarter
        geo->latitude = 45;
        geo->longitude = 90;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);

        //Top left quarter
        geo->latitude = 45;
        geo->longitude = -90;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);

        //Bottom left quarter
        geo->latitude = -45;
        geo->longitude = -90;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);

        //Bottom right quarter
        geo->latitude = -45;
        geo->longitude = 90;

        equiModel->forward(geo, point);
        CHECK(point->x == geo->latitude);
        CHECK(point->y == geo->longitude);
        CHECK(point->z == 0);
    }
}

TEST_CASE("COMMON forward(...)", "[forward][mapscenemodel][sunny]") {
    AtakMapView* view = new AtakMapView(240, 320, 240);
    GeoPoint* geo = new GeoPoint();
    Point<float>* point;

    view->setProjection(SRID_ECEFWSG84);
    MapSceneModel* ecefModel = view->createSceneModel();

    
}