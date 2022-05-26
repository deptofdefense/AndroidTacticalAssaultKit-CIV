#define CATCH_CONFIG_RUNNER

#include "catch.h"
#include "core/AtakMapController.h"
#include "core/AtakMapView.h"

using namespace atakmap::core;

int main(int argc, char* const argv[])
{
    int result = Catch::Session().run(argc, argv);
    std::cin.get();
    return result;
}

void tearDown(AtakMapView* view, AtakMapController* controller, GeoPoint* point)
{
    delete view;
    delete controller;
    delete point;
}

TEST_CASE("panZoomRotateTo(...);", "[pan][sunny]")
{
    AtakMapView* view = new AtakMapView(240, 320, 240);
    AtakMapController* controller = new AtakMapController(view);
    GeoPoint* zoomTo = new GeoPoint(1, 1);

    controller->panZoomRotateTo(zoomTo, 2, 45, true);
    view->getPoint(zoomTo); //refreshes zoomTo fields with AMV's fields

    CHECK(zoomTo->altitude == 1);
    CHECK(zoomTo->longitude == 1);
    CHECK(view->getMapScale() == 2);
    CHECK(view->getMapRotation() == 45);

    tearDown(view, controller, zoomTo);
}

TEST_CASE("BOUNDARY PAN MANAGEMENT: panZoomRotateTo(...); ", "[pan][rainy]")
{
    AtakMapView* view = new AtakMapView(240, 320, 240);
    const int minMapScale = view->getMinMapScale();
    AtakMapController* controller = new AtakMapController(view);
    GeoPoint* zoomTo = new GeoPoint();

    //Verify the invalid pan didn't go through
    controller->panZoomRotateTo(zoomTo, NAN, NAN, false);
    CHECK(view->getMapScale() == minMapScale);
    CHECK(view->getMapRotation() == 0.0);

    tearDown(view, controller, zoomTo);
}