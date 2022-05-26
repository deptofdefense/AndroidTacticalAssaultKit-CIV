#include "pch.h"

#include <formats/quantizedmesh/impl/TerrainData.h>
#include <util/Memory.h>
#include <core/GeoPoint2.h>
#include <elevation/ElevationSourceManager.h>
#include <elevation/ElevationManager.h>
#include <formats/dted/DtedElevationSource.h>

#include <chrono>

//#define ENABLE_DTED_BENCHMARK_TESTS

using namespace TAK::Engine::Core;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Formats::QuantizedMesh::Impl;
using namespace TAK::Engine::Elevation;
using namespace TAK::Engine::Formats::DTED;
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
        std::unique_ptr<TerrainData> td;
        TAKErr code = TerrainData_deserialize(td, resource.c_str(), 0);
        ASSERT_EQ(code, TE_Ok);

    	double elev;
    	elev = td->getElevation(10, 10, false);
        // Release and leak to prevent an invalid cross-dll cleanup.
        td.release();
    }

    TEST_F(ElevationMeshTileTests, MapTilerTiles)
    {
        std::string resource = getResource("2865.terrain");
        std::unique_ptr<TerrainData> td;
        TAKErr code = TerrainData_deserialize(td, resource.c_str(), 12);
        ASSERT_EQ(code, TE_Ok);

    	double elev;
    	elev = td->getElevation(35.925, -78.903, true);

        ASSERT_GT(elev, 77);
        ASSERT_LT(elev, 78);
        
        // Release and leak to prevent an invalid cross-dll cleanup.
        td.release();
    }


#ifdef ENABLE_DTED_BENCHMARK_TESTS
    /**
     * @param millis            Elapsed milliseconds for elevation queries
     * @param poi               Point of interest
     * @param sampleExtentLat   The latitudinal extent of the sampling region, centered on `poi`, in degrees
     * @param sampleExtentLng   The longitudinal extent of the sampling region, centered on `poi`, in degrees
     * @param numSamplesLat     The number of latitude samples to be taken within the extent
     * @param numSamplesLng     The number of longitude samples to be taken within the extent
     */
    void _dtedSampleBenchmark(long long *millis, const GeoPoint2& poi, const double sampleExtentLat, const double sampleExtentLng, const std::size_t numSamplesLat, const std::size_t numSamplesLng) NOTHROWS
    {
        *millis = 0LL;

        ASSERT_GT(numSamplesLat, 1u);
        ASSERT_GT(numSamplesLng, 1u);

        std::chrono::steady_clock::time_point begin = std::chrono::steady_clock::now();

        const double sampleOriginLat = (poi.latitude - sampleExtentLat / 2.0);
        const double sampleOriginLng = (poi.longitude - sampleExtentLng / 2.0);
        const double sampleIntervalLat = sampleExtentLat / (double)(numSamplesLat-1u);
        const double sampleIntervalLng = sampleExtentLng / (double)(numSamplesLng-1u);
        for (std::size_t sampleLat = 0; sampleLat < numSamplesLat; sampleLat++) {
            for (std::size_t sampleLng = 0; sampleLng < numSamplesLng; sampleLng++) {
                auto pt = GeoPoint2(
                    sampleOriginLat - (sampleLat*sampleIntervalLat), // N to S
                    sampleOriginLng + (sampleLng*sampleIntervalLng)); // W to E
                double elevation;
                String source;
                TAKErr code = ElevationManager_getElevation(&elevation, &source, pt.latitude, pt.longitude,
                    ElevationSource::QueryParameters());
            }
        }

        std::chrono::steady_clock::time_point end = std::chrono::steady_clock::now();
        auto duration = end - begin;
        *millis = std::chrono::duration_cast<std::chrono::milliseconds>(duration).count();
    }

    /**
     * Queries 
     * @param millis            Elapsed milliseconds for elevation queries
     * @param poi               Point of interest
     * @param sampleExtentLat   The per-tile latitudinal extent of the sampling region, centered on `poi`, in degrees
     * @param sampleExtentLng   The per-tile longitudinal extent of the sampling region, centered on `poi`, in degrees
     * @param numSamplesLat     The number of latitude samples to be taken within the tile extent
     * @param numSamplesLng     The number of longitude samples to be taken within the tile extent
     */
    void _dtedBulkBenchmark(long long* millis, const GeoPoint2& poi, const double sampleExtentLat, const double sampleExtentLng, const std::size_t numSamplesLat, const std::size_t numSamplesLng, const std::size_t numTilesLat, const std::size_t numTilesLng) NOTHROWS
    {
        *millis = 0LL;

        ASSERT_GT(numSamplesLat, 1u);
        ASSERT_GT(numSamplesLng, 1u);

        std::vector<double> elevationPoints;
        elevationPoints.reserve(3u * (numSamplesLat*numSamplesLng));

        std::chrono::steady_clock::time_point begin = std::chrono::steady_clock::now();

        const double queryExtentLat = sampleExtentLat * numTilesLat;
        const double queryExtentLng = sampleExtentLng * numTilesLng;
        const double sampleOriginLat = (poi.latitude - queryExtentLat / 2.0);
        const double sampleOriginLng = (poi.longitude - queryExtentLng / 2.0);
        const double sampleIntervalLat = sampleExtentLat / (double)(numSamplesLat-1u);
        const double sampleIntervalLng = sampleExtentLng / (double)(numSamplesLng-1u);
        for (std::size_t tileLat = 0; tileLat < numTilesLat; tileLat++) {
            for (std::size_t tileLng = 0; tileLng < numTilesLng; tileLng++) {
                // prepare for next query
                elevationPoints.clear();
                for (std::size_t sampleLat = 0; sampleLat < numSamplesLat; sampleLat++) {
                    for (std::size_t sampleLng = 0; sampleLng < numSamplesLng; sampleLng++) {
                        auto pt = GeoPoint2(
                            sampleOriginLat - (tileLat*sampleExtentLat + sampleLat*sampleIntervalLat), // N to S
                            sampleOriginLng + (tileLng*sampleExtentLng + sampleLng*sampleIntervalLng)); // W to E
                        // push back query point
                        elevationPoints.push_back(pt.longitude);
                        elevationPoints.push_back(pt.latitude);
                        elevationPoints.push_back(NAN);
                    }
                }

                // perform bulk query
                ElevationManager_getElevation(&elevationPoints.at(2u),
                                              elevationPoints.size()/3u,
                                              &elevationPoints.at(1u),
                                              &elevationPoints.at(0u),
                                              3u,
                                              3u,
                                              3u,
                                              ElevationSource::QueryParameters());
            }
        }

        

        std::chrono::steady_clock::time_point end = std::chrono::steady_clock::now();
        auto duration = end - begin;
        *millis = std::chrono::duration_cast<std::chrono::milliseconds>(duration).count();
    }

    TEST_F(ElevationMeshTileTests, DtedParsingTest) {
        TAKErr code;
        std::shared_ptr<TAK::Engine::Elevation::ElevationSource> dted_source;
        dted_source.reset(new DtedElevationSource("C:\\ProgramData\\WinTAK\\DTED"));
        //dted_source.reset(new DtedElevationSource("D:\\DTED"));
        code = ElevationSourceManager_attach(dted_source);

        // run several benchmarks for different scenarios
        // note that point counts describe magnitude; e.g. actual number of points queried may not be exactly 100000
        auto pt = GeoPoint2(44.5, -103.5);
        long long singleCellSample10kPoints = 0LL;
        long long multiCellSample10kPoints = 0LL;
        long long singleCellSample100kPoints = 0LL;
        long long multiCellSample100kPoints = 0LL;

        _dtedSampleBenchmark(&singleCellSample10kPoints, pt, 0.5, 0.5, 100u, 100u);
        _dtedSampleBenchmark(&multiCellSample10kPoints, pt, 2.0, 2.0, 100u, 100u);
        _dtedSampleBenchmark(&singleCellSample100kPoints, pt, 0.5, 0.5, 320u, 320u);
        _dtedSampleBenchmark(&multiCellSample100kPoints, pt, 2.0, 2.0, 320u, 320u);

        long long singleCellBulk10kPoints = 0LL;
        long long multiCellBulk10kPoints = 0LL;
        long long singleCellBulk100kPoints = 0LL;
        long long multiCellBulk100kPoints = 0LL;

        _dtedBulkBenchmark(&singleCellBulk10kPoints, pt, 0.05, 0.05, 10u, 10u, 10u, 10u);
        _dtedBulkBenchmark(&multiCellBulk10kPoints, pt, 0.2, 0.2, 10u, 10u, 10u, 10u);
        _dtedBulkBenchmark(&singleCellBulk100kPoints, pt, 0.05, 0.05, 32u, 32u, 10u, 10u);
        _dtedBulkBenchmark(&multiCellBulk100kPoints, pt, 0.2, 0.2, 32u, 32u, 10u, 10u);

        std::cout << "Single Cell Sample, 10K Points = " << singleCellSample10kPoints << "[ms]"
                  << std::endl;
        std::cout << "Multi Cell Sample, 10K Points = " << multiCellSample10kPoints << "[ms]"
                  << std::endl;
        std::cout << "Single Cell Sample, 100K Points = " << singleCellSample100kPoints << "[ms]"
                  << std::endl;
        std::cout << "Multi Cell Sample, 100K Points = " << multiCellSample100kPoints << "[ms]"
                  << std::endl;

        std::cout << "Single Cell Bulk, 10K Points = " << singleCellSample10kPoints << "[ms]"
                  << std::endl;
        std::cout << "Multi Cell Bulk, 10K Points = " << multiCellSample10kPoints << "[ms]"
                  << std::endl;
        std::cout << "Single Cell Bulk, 100K Points = " << singleCellSample100kPoints << "[ms]"
                  << std::endl;
        std::cout << "Multi Cell Bulk, 100K Points = " << multiCellSample100kPoints << "[ms]"
                  << std::endl;

        std::cout << "Done" << std::endl;
    }
#endif
}

