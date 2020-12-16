#include "pch.h"

#include "model/SceneInfo.h"

using namespace TAK::Engine::Model;

namespace takenginetests {

	class TestSceneInfoSpi : public SceneInfoSpi {
	public:
		virtual ~TestSceneInfoSpi() NOTHROWS
		{ }

		virtual int getPriority() const NOTHROWS {
			return 42;
		}

		virtual const char *getName() const NOTHROWS {
			return "Test";
		}
		virtual bool isSupported(const char *path) NOTHROWS {
			return true;
		}

		virtual TAK::Engine::Util::TAKErr create(TAK::Engine::Port::Collection<SceneInfoPtr> &scenes, const char *path) NOTHROWS {
			return TAK::Engine::Util::TE_Ok;
		}
	};

	TEST(SceneInfoTests, testFactoryIsSupported) {
		bool supported = SceneInfoFactory_isSupported("none", nullptr);
		ASSERT_FALSE(supported);

		std::shared_ptr<TestSceneInfoSpi> spi = std::make_shared<TestSceneInfoSpi>();
		SceneInfoFactory_registerSpi(spi);

		supported = SceneInfoFactory_isSupported("none", nullptr);
		ASSERT_TRUE(supported);

		SceneInfoFactory_unregisterSpi(spi);
		supported = SceneInfoFactory_isSupported("none", nullptr);

		ASSERT_FALSE(supported);
	}
}