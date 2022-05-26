#define CATCH_CONFIG_RUNNER
#define SRID_EQUIRECTANGULAR 4326
#define SRID_WEBMERCATOR 3857
#define SRID_ECEFWSG84 4978
#define SRID_INVALID 666

#include "catch.h"
#include "core/AtakMapView.h"

/******************************************************************************
 * NOTES
 ******************************************************************************
 * 1) Same use/test case -> same tag, regardless of method under test 
 *    Exception: tags of test cases that can be part of additional test groups 
 *    would be supersets of tags belonging to some, but not all of the same 
 *    test groups
 * 2) Test group (cmdline exec. format): "[1][2], [3]" matches all tests tagged 
 *    [1] and [2], as well as [3] (A series of tags form an AND expression 
 *    wheras a comma-separated sequence forms an OR expression)
 * 3) Once you can build, run through the debugger to get exact idea of 
 *    program flow
 ******************************************************************************
 * TODO / ASK DEV(S)
 ******************************************************************************
 * 1) Test against more AtakMapView initialization options (ie, resolutions/DPIs) 
 * 2) [layers][add][rainy]: Should the same layer be "addable" while it already
 * exists/has been added into the view?
 * 3) [layers][add], [layers][remove]: Determine better (less arbitrary) 
 * layer edge # value, and what should happen when it's surpassed.
 * 4) [layers][add][rainy]: expected result when adding a layer that's already
 * been added and not yet removed?
 *****************************************************************************/

using namespace atakmap::core;

const int NUM_LAYERS_EDGE_CONDITION = 25; 

//Helper function for layer tests
void layerTearDown(std::list<Layer* > &layers, AtakMapView* view)
{
    std::list<Layer* >::iterator it;
    for (it = layers.begin(); it != layers.end(); it++)
    {
        layers.erase(it);
    }
    delete view;
}

int main(int argc, char* const argv[])
{
    int result = Catch::Session().run(argc, argv);
    std::cin.get();
    return result;
}

TEST_CASE("COMMON LAYER MANAGEMENT: addLayer()", "[layers][add][sunny]")
{
    int layerCount = 0;
    AtakMapView* testView = new AtakMapView(240, 320, 240);

    //From state of 0-to-EDGE_CONDITION
    for (int layerAdder = layerCount; layerAdder < NUM_LAYERS_EDGE_CONDITION; layerAdder++)
    {
        Layer *newLayer;
        testView->addLayer(newLayer); layerCount++;
        CHECK(testView->getNumLayers() == layerCount);
    }

    //From state where prior behavior was removing layer
    Layer* newLayer;
    std::list<Layer* > layers = testView->getLayers();
    testView->removeLayer(layers.back()); layerCount--;
    testView->addLayer(newLayer); layerCount++;
    CHECK(testView->getNumLayers() == layerCount);

    //Tear down / deallocate
    layerTearDown(layers, testView);
}

TEST_CASE("BOUNDARY LAYER MANAGEMENT: addLayer()", "[layers][add][rainy]")
{
    int layerCount = 0;
    AtakMapView* testView = new AtakMapView(240, 320, 240);
    
    for (int layerAdder = layerCount; layerAdder < NUM_LAYERS_EDGE_CONDITION; layerAdder++)
    {
        Layer *newLayer;
        testView->addLayer(newLayer); layerCount++;
    }
                                                    
    //From numLayers == EDGE_CONDITION
    Layer* excessLayer;
    testView->addLayer(excessLayer);
    CHECK(testView->getNumLayers() == layerCount);
    delete excessLayer;  

    /****
    FILLER: ADDING A LAYER THAT'S ALREADY BEEN ADDED BUT NOT YET REMOVED.
    ******/
    layerTearDown(testView->getLayers(), testView);
}

TEST_CASE("COMMON LAYER MANAGEMENT: addLayer(int idx, Layer *layer)", "[layers][add][sunny]")
{
    int layerCount = 0;
    AtakMapView* testView = new AtakMapView(240, 320, 240);
    std::list<Layer* > layers = testView->getLayers();

    //From state of 0-to-EDGE_CONDITION
    for (int layerAdder = 0; layerAdder < NUM_LAYERS_EDGE_CONDITION; layerAdder++)
    {
        Layer *newLayer;
        testView->addLayer(layers.size(), newLayer); layerCount++;
        CHECK(testView->getNumLayers() == layerCount);
    }

    //From state where prior behavior was removing layer
    Layer* newLayer;
    testView->removeLayer(layers.back()); layerCount--;
    testView->addLayer(layers.size(), newLayer); layerCount++;
    CHECK(testView->getNumLayers() == layerCount);

    //Tear down / deallocate
    layerTearDown(layers, testView);
}

TEST_CASE("BOUNDARY LAYER MANAGEMENT: addLayer(int idx, Layer *layer)", "[layers][add][rainy]")
{
    int layerCount = 0;
    AtakMapView* testView = new AtakMapView(240, 320, 240);
    std::list<Layer* > layers = testView->getLayers();

    //From state of no layers w/ negative index
    Layer* newLayer;
    CHECK_THROWS(testView->addLayer(layers.size() - (layers.size() - 1), newLayer));

    //From state of no layers w/ invalid (too big) index
    CHECK_THROWS(testView->addLayer(layers.size() + 1, newLayer));

    for (int layerAdder = testView->getNumLayers(); layerAdder < NUM_LAYERS_EDGE_CONDITION; layerAdder++)
    {
        Layer *newLayer;
        testView->addLayer(layers.size(), newLayer); layerCount++;
    }
                                                        
    //From numLayers == EDGE_CONDITION
    Layer* excessLayer;
    testView->addLayer(layers.size(), excessLayer);
    CHECK(testView->getNumLayers() == layerCount);

    layerTearDown(layers, testView);
}

TEST_CASE("COMMON LAYER MANAGEMENT: removeLayer(Layer *layer), removeAllLayers()", "[layers][remove][sunny]")
{
    int layerCount = 0;
    AtakMapView* testView = new AtakMapView(240, 320, 240);

    for (int layerAdder = 0; layerAdder < NUM_LAYERS_EDGE_CONDITION; layerAdder++)
    {
        Layer* newLayer;
        testView->addLayer(newLayer); layerCount++;
    }

    std::list<Layer* > layers = testView->getLayers();
    //First layer in a state of many
    testView->removeLayer(*layers.begin()); layerCount--;
    CHECK(testView->getNumLayers() == layerCount);

    //In-between layer in a state of many
    std::list<Layer* >::iterator it = layers.begin();
    std::advance(it, layers.size() / 2);
    testView->removeLayer(*it); layerCount--;
    CHECK(testView->getNumLayers() == layerCount);

    //Last layer in a state of many
    testView->removeLayer(*layers.end()); layerCount--;
    CHECK(testView->getNumLayers() == layerCount);

    //All remaining layers
    int layersToRemove = layers.size();
    testView->removeAllLayers();
    layerCount -= layersToRemove;
    CHECK(testView->getNumLayers() == layerCount);

    delete testView;
}

TEST_CASE("BOUNDARY LAYER MANAGEMENT: removeLayer(Layer *layer), removeAllLayers()", "[layers][remove][rainy]")
{
    int layerCount = 0;
    Layer* newLayer;
    AtakMapView* testView = new AtakMapView(240, 320, 240);
    std::list<Layer* > layers = testView->getLayers();

    //From a state of no layers
    CHECK_NOTHROW(testView->removeLayer(newLayer));

    //All remaining layers from a state of no layers
    int layersToRemove = layers.size();
    testView->removeAllLayers();
    layerCount -= layersToRemove;
    CHECK(testView->getNumLayers() == layerCount);

    //From a state of 1 layer, but this layer hasn't been added
    Layer* tempLayer;
    testView->addLayer(newLayer); layerCount++;
    testView->removeLayer(tempLayer);
    CHECK(testView->getNumLayers() == layerCount);

    delete testView;
}

TEST_CASE("COMMON LAYER MANAGEMENT: setLayerPosition(Layer *layer, const int position)", "[layers][set][sunny]")
{
    AtakMapView* testView = new AtakMapView(240, 320, 240);
    for (int layerAdder = 0; layerAdder < NUM_LAYERS_EDGE_CONDITION; layerAdder++)
    {
        Layer* newLayer;
        testView->addLayer(newLayer);
    }
    std::list<Layer* > layers = testView->getLayers();
    Layer* beginPtr = *layers.begin();
    Layer* endPtr = *layers.end();
    int oldNumLayers = testView->getNumLayers();

    SECTION("position last layer -> first layer")
    {
        testView->setLayerPosition(endPtr, 0); oldNumLayers--; //old layer at new position removed
        CHECK(*testView->getLayers().begin() == endPtr);
        CHECK(testView->getNumLayers() == oldNumLayers); 
    }

    SECTION("position first layer -> last layer")
    {
        testView->setLayerPosition(beginPtr, layers.size() - 1); oldNumLayers--;
        CHECK(*testView->getLayers().end() == beginPtr);
        CHECK(testView->getNumLayers() == oldNumLayers);
    }

    SECTION("position first layer -> in-between layer"){
        testView->setLayerPosition(beginPtr, layers.size() / 2); oldNumLayers--;
        std::list<Layer* >::iterator it = layers.begin();
        std::advance(it, layers.size() / 2);
        CHECK(beginPtr == *it);
    }

    layerTearDown(layers, testView);
}

TEST_CASE("BOUNDARY LAYER MANAGEMENT: setLayerPosition(Layer *layer, const int position)", "[layers][set][rainy]")
{
    AtakMapView* testView = new AtakMapView(240, 320, 240);
    for (int layerAdder = 0; layerAdder < NUM_LAYERS_EDGE_CONDITION; layerAdder++)
    {
        Layer* newLayer;
        testView->addLayer(newLayer);
    }
    std::list<Layer* > layers = testView->getLayers();
    Layer* beginPtr = *layers.begin();
    Layer* endPtr = *layers.end();
    int oldNumLayers = testView->getNumLayers();

    SECTION("passing a layer that was never added")
    {
        Layer* unknownLayer;
        testView->setLayerPosition(unknownLayer, 0);
        Layer* firstLayer = *layers.begin();
        CHECK(firstLayer != unknownLayer);
        delete unknownLayer;
        CHECK(testView->getNumLayers() == oldNumLayers);
    }

    SECTION("position == old position")
    {
        testView->setLayerPosition(beginPtr, 0);
        testView->setLayerPosition(endPtr, layers.size() - 1);
        CHECK(*testView->getLayers().begin() == beginPtr);
        CHECK(*testView->getLayers().end() == endPtr);
        CHECK(oldNumLayers == testView->getNumLayers()); //no layers moved, no layers deleted
    }

    SECTION("position OOB")
    {
        testView->setLayerPosition(beginPtr, testView->getNumLayers() + 1);
        testView->setLayerPosition(endPtr, -1);
        CHECK(oldNumLayers == testView->getNumLayers());
    }

    layerTearDown(layers, testView);
}

//Method coupled to other classes that can be tested
TEST_CASE("COMMON PROJECTION MANAGEMENT: setProjection(const int srid)", "[projections][sunny][set]")
{
    AtakMapView* testView = new AtakMapView(240, 320, 240);

    //To a valid projection, from an empty projection
    REQUIRE(testView->setProjection(SRID_WEBMERCATOR) == true);

    //To a valid projection, from a valid projection
    int oldSRID = testView->getProjection();
    CHECK(testView->setProjection(SRID_ECEFWSG84) == true);
    CHECK(testView->getProjection() != oldSRID);

    delete testView;
}

TEST_CASE("BOUNDARY PROJECTION MANAGEMENT: setProjection(const int srid)", "[projections][rainy][set]")
{
    AtakMapView* testView = new AtakMapView(240, 320, 240);

    //To an invalid projection/srid
    CHECK(testView->setProjection(SRID_INVALID) == false);

    //To a valid projection, from the SAME projection
    REQUIRE(testView->setProjection(SRID_WEBMERCATOR) == true);
    CHECK(testView->setProjection(SRID_WEBMERCATOR) == false);

    delete testView;
}


//To test projection getters more thoroughly, need to access coupled classes/dependecies
TEST_CASE("COMMON PROJECTION MANAGEMENT: getProjection()", "[projections][sunny][get]")
{
    AtakMapView* testView = new AtakMapView(240, 320, 240);
    
    testView->setProjection(SRID_EQUIRECTANGULAR);
    CHECK(testView->getProjection() == SRID_EQUIRECTANGULAR);

    testView->setProjection(SRID_ECEFWSG84);
    CHECK(testView->getProjection() == SRID_ECEFWSG84);

    testView->setProjection(SRID_WEBMERCATOR);
    CHECK(testView->getProjection() == SRID_WEBMERCATOR);

    delete testView;
}

TEST_CASE("BOUNDARY PROJECTION MANAGEMENT: getProjection()", "[projections][rainy][get]")
{
    AtakMapView* testView = new AtakMapView(240, 320, 240);

    //New view, no layer
    CHECK(testView->getProjection() == NULL);

    testView->setProjection(SRID_INVALID);
    CHECK(testView->getProjection() == NULL);

    //Verify: changing from valid to invalid layer -> maintains valid layer
    testView->setProjection(SRID_WEBMERCATOR);
    testView->setProjection(SRID_INVALID);
    CHECK(testView->getProjection() == SRID_WEBMERCATOR);

    delete testView;
}