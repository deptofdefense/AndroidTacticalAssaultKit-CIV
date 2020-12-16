#include "pch.h"

#include <formats/quantizedmesh/TerrainData.h>

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Formats::QuantizedMesh;
using namespace TAK::Engine::Tests;

namespace takenginetests
{

    class ElevationMeshTileTests : public ::testing::Test
    {
		void SetUp() override
		{
			LoggerPtr logger(new TestLogger, Memory_deleter_const<Logger2, TestLogger>);
			Logger_setLogger(std::move(logger));
			Logger_setLevel(TELL_All);
		}
    };

    TEST_F(ElevationMeshTileTests, ReadTerrainTiles)
    {
        std::string resource = TAK::Engine::Tests::getResource("0.terrain");
        auto td = TerrainData(0);
        td.parseTerrainFile(resource.c_str(), 0);

    	double elev;
    	td.getElevation(10, 10, &elev);
    	
    }
    
}
