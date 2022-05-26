#include "pch.h"

#include <cmath>

#include <util/Memory.h>
#include <util/URIOfflineCache.h>

using namespace TAK::Engine;
using namespace TAK::Engine::Util;

using namespace TAK::Engine::Tests;

namespace takenginetests {
	class URIOfflineCacheTests : public ::testing::Test
	{
	protected:

		void SetUp() override {
			// start fresh
			std::string cachePath = TAK::Engine::Tests::getResource("URIOfflineCacheTests");
			//IO_delete(cachePath.c_str());

			LoggerPtr logger(new TestLogger, Memory_deleter_const<Logger2, TestLogger>);
			Logger_setLogger(std::move(logger));
			Logger_setLevel(TELL_All);
		}
	};

}