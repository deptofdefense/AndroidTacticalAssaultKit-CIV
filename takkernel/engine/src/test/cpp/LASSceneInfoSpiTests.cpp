#include "pch.h"

#include "model/LASSceneInfoSpi.h"
#include "port/STLVectorAdapter.h"
#include "core/ProjectionFactory3.h"
#include "formats/osr/OSRProjectionSpi.h"
#include "util/Memory.h"

#include <gdal_priv.h>


using namespace TAK::Engine::Util;
using namespace TAK::Engine::Model;

namespace takenginetests {
	// enable for local testing
#if 0
	TEST(LASSceneInfoSpiTests, testLocalFile) {

		const char* local_resource = "XXX";
		const char* gdal_dir = "XXX";
		
		std::string resource = local_resource;
		
		// NOTE: AllRegister automatically loads the shared libraries
		GDALAllRegister();
		CPLSetConfigOption("GDAL_DATA", gdal_dir);
		// debugging
		CPLSetConfigOption("CPL_DEBUG", "OFF");
		CPLSetConfigOption("CPL_LOG_ERRORS", "ON");

		CPLSetConfigOption("GDAL_DISABLE_READDIR_ON_OPEN", "TRUE");

		std::shared_ptr<TAK::Engine::Core::ProjectionSpi3> gdalProjSpi(std::move(TAK::Engine::Core::ProjectionSpi3Ptr(&TAK::Engine::Formats::OSR::OSRProjectionSpi_get(), 
			TAK::Engine::Util::Memory_leaker_const<TAK::Engine::Core::ProjectionSpi3>)));

		TAK::Engine::Core::ProjectionFactory3_registerSpi(gdalProjSpi, 0);

		std::shared_ptr<LASSceneInfoSpi> daeSupport = std::make_shared<LASSceneInfoSpi>();
		TAKErr code = SceneInfoFactory_registerSpi(daeSupport);
		ASSERT_EQ((int)code, (int)TE_Ok);

		LASSceneInfoSpi spi;
		TAK::Engine::Port::STLVectorAdapter<SceneInfoPtr> scenes;
		code = spi.create(scenes, resource.c_str());
		ASSERT_EQ((int)code, (int)TE_Ok);

	}
#endif
}