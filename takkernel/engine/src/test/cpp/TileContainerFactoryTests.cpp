#include "pch.h"

#include <string>
#include <vector>
#include <map>
#include <algorithm>
#include "raster/tilematrix/TileContainerFactory.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Raster::TileMatrix;

namespace takenginetests {
	namespace {
		class TestMatrix : public TileMatrix {
			std::string name;
			int srid;
			std::pair<double, double> origin;
			std::vector<ZoomLevel> zooms;

		public:
			TestMatrix(const char *name, int srid, double originX, double originY, ZoomLevel *zooms, int nZooms) : name(name), srid(srid), origin(originX, originY), zooms()
			{
				for (int i = 0; i < nZooms; ++i) {
					this->zooms.push_back(zooms[i]);
				}
			}
			virtual ~TestMatrix() NOTHROWS {}
			virtual const char* getName() const NOTHROWS { return name.c_str(); }
			virtual int getSRID() const NOTHROWS
			{
				return srid;
			};
			virtual TAK::Engine::Util::TAKErr getZoomLevel(TAK::Engine::Port::Collection<ZoomLevel>& value) const NOTHROWS
			{
				std::vector<ZoomLevel>::const_iterator iter;
				for (iter = zooms.begin(); iter != zooms.end(); iter++)
					value.add(*iter);
				return TAK::Engine::Util::TE_Ok;
			}
			virtual double getOriginX() const NOTHROWS
			{
				return origin.first;
			}
			virtual double getOriginY() const NOTHROWS
			{
				return origin.second;
			}
			virtual TAK::Engine::Util::TAKErr getTile(TAK::Engine::Renderer::BitmapPtr& result, const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS
			{
				return TAK::Engine::Util::TE_Unsupported;
			}
			virtual TAK::Engine::Util::TAKErr getTileData(std::unique_ptr<const uint8_t, void(*)(const uint8_t*)>& value, std::size_t* len,
				const std::size_t zoom, const std::size_t x, const std::size_t y) NOTHROWS
			{
				return TAK::Engine::Util::TE_Unsupported;
			}
			virtual TAK::Engine::Util::TAKErr getBounds(TAK::Engine::Feature::Envelope2 *value) const NOTHROWS
			{
				return TAK::Engine::Util::TE_Unsupported;
			}
		};


		struct TestContext {

			TestContext()
				: index(0), successIndex(-1)
			{
			}

			std::shared_ptr<TileContainerSpi> addSpi(const char *name, const char *ext, int srid, TAK::Engine::Util::TAKErr openRes);
			void touch(const char *name, bool success);
			std::vector<std::string> getTouchOrder();
			void resetTouches();
			int getSuccessIndex();

			static TAKErr visitor(void* opaque, TileContainerSpi &spi);

		private:
			int index;
			std::vector<std::shared_ptr<TileContainerSpi>> types;
			std::map<std::string, int> touches;
			int successIndex;
		};

		class TestTileContainerSpi : public TileContainerSpi {
		public:
			TestTileContainerSpi(TestContext &cxt, const char *name, const char *defExt, int compatSRID, TAK::Engine::Util::TAKErr openRes)
				: cxt(cxt), name(name), defExt(defExt), compatSRID(compatSRID), openRes(openRes)
			{
			}

			virtual ~TestTileContainerSpi() NOTHROWS
			{
			}

			virtual TAKErr create(TileContainerPtr &result, const char *name, const char *path, const TileMatrix *spec) const NOTHROWS
			{
				bool success = spec->getSRID() == compatSRID;
				cxt.touch(this->name.c_str(), success);
				return success ? TAK::Engine::Util::TE_Ok : TAK::Engine::Util::TE_Unsupported;
			}

			virtual TAKErr open(TileContainerPtr &result, const char *path, const TileMatrix *spec, bool readOnly) const NOTHROWS
			{
				TAKErr ret;
				if (spec)
					ret = spec->getSRID() == compatSRID ? TAK::Engine::Util::TE_Ok : TAK::Engine::Util::TE_Unsupported;
				else
					ret = openRes;
				cxt.touch(name.c_str(), ret == TE_Ok);
				return ret;
			}

			virtual TAKErr isCompatible(bool *result, const TileMatrix *spec) const NOTHROWS
			{
				//cxt.touch(name.c_str(), false);
				*result = spec->getSRID() == compatSRID;
				return TAK::Engine::Util::TE_Ok;
			}

			virtual const char *getName() const NOTHROWS
			{
				return name.c_str();
			}

			virtual const char *getDefaultExtension() const NOTHROWS
			{
				return defExt.c_str();
			}
		private:
			TestContext &cxt;
			std::string name;
			std::string defExt;
			int compatSRID;
			TAK::Engine::Util::TAKErr openRes;
		};

		std::shared_ptr<TileContainerSpi> TestContext::addSpi(const char *name, const char *ext, int srid, TAK::Engine::Util::TAKErr openRes)
		{
			types.push_back(std::make_shared<TestTileContainerSpi>(*this, name, ext, srid, openRes));
			return types.back();
		}

		void TestContext::touch(const char *name, bool success)
		{
			touches.insert(std::make_pair(std::string(name), this->index));
			if (success)
				successIndex = index;
			this->index++;
		}

		int TestContext::getSuccessIndex()
		{
			return successIndex;
		}

		std::vector<std::string> TestContext::getTouchOrder()
		{
			std::vector<std::pair<std::string, int>> entries;
			for (auto &touch : touches) {
				entries.push_back(touch);
			}

			std::sort(entries.begin(), entries.end(), [](const std::pair<std::string, int> &a, std::pair<std::string, int> &b) {
				return a.second < b.second;
			});

			std::vector<std::string> result;
			for (auto &touch : entries) {
				result.push_back(touch.first);
			}
			return result;
		}

		void TestContext::resetTouches()
		{
			touches.clear();
			index = 0;
			successIndex = -1;
		}

		TAKErr TestContext::visitor(void* opaque, TileContainerSpi &spi)
		{
			TestContext *c = (TestContext *)opaque;
			c->touch(spi.getName(), false);
			return TE_Ok;
		}
	}

	TEST(TileContainerFactoryTests, testOpen)
	{

		TestContext context;
		TestMatrix spec50("testmatrix50", 50, 0, 0, NULL, 0);
		TestMatrix spec99("testmatrix99", 99, 0, 0, NULL, 0);

		TAKErr code = TileContainerFactory_registerSpi(context.addSpi("spi50fail", "s50", 50, TAK::Engine::Util::TE_Unsupported));
		ASSERT_TRUE(code == TE_Ok);

		// Baseline test - single SPI that fails should fail to open
		TileContainerPtr result(nullptr, nullptr);
		code = TileContainerFactory_open(result, "foo", true, NULL);
		ASSERT_TRUE(code == TE_Unsupported);

		context.resetTouches();


		// Add succeeding spi for 50 and repeat
		code = TileContainerFactory_registerSpi(context.addSpi("spi50succeed", "s50", 50, TAK::Engine::Util::TE_Ok));
		ASSERT_TRUE(code == TE_Ok);

		code = TileContainerFactory_open(result, "foo", true, NULL);
		ASSERT_TRUE(code == TE_Ok);

		// Check that is was opened by the right spi
		std::vector<std::string> touches = context.getTouchOrder();
		int successIndex = context.getSuccessIndex();
		ASSERT_TRUE(successIndex >= 0);
		ASSERT_STREQ(touches[successIndex].c_str(), "spi50succeed");

		context.resetTouches();


		// Add succeeding spi for 99 and 1 and test hinting
		code = TileContainerFactory_registerSpi(context.addSpi("spi99", "s99", 99, TAK::Engine::Util::TE_Ok));
		ASSERT_TRUE(code == TE_Ok);

		code = TileContainerFactory_registerSpi(context.addSpi("spi1", "s1", 1, TAK::Engine::Util::TE_Ok));
		ASSERT_TRUE(code == TE_Ok);

		code = TileContainerFactory_open(result, "foo", true, "spi50succeed");
		ASSERT_TRUE(code == TE_Ok);

		// Check that is was opened by the right spi
		touches = context.getTouchOrder();
		successIndex = context.getSuccessIndex();
		ASSERT_TRUE(successIndex >= 0);
		ASSERT_STREQ(touches[successIndex].c_str(), "spi50succeed");

		context.resetTouches();

		// Again with another hint
		code = TileContainerFactory_open(result, "foo", true, "spi99");
		ASSERT_TRUE(code == TE_Ok);

		// Check that is was opened by the right spi
		touches = context.getTouchOrder();
		successIndex = context.getSuccessIndex();
		ASSERT_TRUE(successIndex >= 0);
		ASSERT_STREQ(touches[successIndex].c_str(), "spi99");


		context.resetTouches();

		// Test creation
		code = TileContainerFactory_openOrCreateCompatibleContainer(result, "foo", &spec50, NULL);
		ASSERT_TRUE(code == TE_Ok);

		touches = context.getTouchOrder();
		successIndex = context.getSuccessIndex();
		ASSERT_TRUE(successIndex >= 0);
		// This could be opened by either 50-supporting spis
		bool ok = touches[successIndex] == "spi50succeed" || touches[successIndex] == "spi50fail";
		ASSERT_TRUE(ok);


		context.resetTouches();

		// Test creation - part 2
		code = TileContainerFactory_openOrCreateCompatibleContainer(result, "foo", &spec99, NULL);
		ASSERT_TRUE(code == TE_Ok);

		touches = context.getTouchOrder();
		successIndex = context.getSuccessIndex();
		ASSERT_TRUE(successIndex >= 0);
		ASSERT_STREQ(touches[touches.size() - 1].c_str(), "spi99");
	}

	TEST(TileContainerFactoryTests, testVisitation)
	{

		TestContext context;
		TestContext visit;
		TestMatrix spec50("testmatrix50", 50, 0, 0, NULL, 0);
		TestMatrix spec99("testmatrix99", 99, 0, 0, NULL, 0);

		TAKErr code = TileContainerFactory_registerSpi(context.addSpi("spi50fail", "s50", 50, TAK::Engine::Util::TE_Unsupported));
		ASSERT_TRUE(code == TE_Ok);
		code = TileContainerFactory_registerSpi(context.addSpi("spi50succeed", "s50", 50, TAK::Engine::Util::TE_Ok));
		ASSERT_TRUE(code == TE_Ok);
		code = TileContainerFactory_registerSpi(context.addSpi("spi99", "s99", 99, TAK::Engine::Util::TE_Ok));
		ASSERT_TRUE(code == TE_Ok);
		code = TileContainerFactory_registerSpi(context.addSpi("spi1", "s1", 1, TAK::Engine::Util::TE_Ok));
		ASSERT_TRUE(code == TE_Ok);

		// Test visitation hits all
		TileContainerFactory_visitSpis(TestContext::visitor, &visit);
		std::vector<std::string> touches = visit.getTouchOrder();
		ASSERT_TRUE(touches.size() == 4);

		visit.resetTouches();


		// Test against specific matrix style
		TileContainerFactory_visitCompatibleSpis(TestContext::visitor, &visit, &spec50);
		touches = visit.getTouchOrder();
		ASSERT_TRUE(touches.size() == 2);
		for (int i = 0; i < 2; ++i)
			ASSERT_TRUE(touches[i] == "spi50succeed" || touches[i] == "spi50fail");

		visit.resetTouches();


		// Again with different matrix
		TileContainerFactory_visitCompatibleSpis(TestContext::visitor, &visit, &spec99);
		touches = visit.getTouchOrder();
		ASSERT_TRUE(touches.size() == 1);
		ASSERT_STREQ(touches[0].c_str(), "spi99");

	}
}