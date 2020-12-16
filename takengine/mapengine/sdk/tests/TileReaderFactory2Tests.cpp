#include "pch.h"

#include <string>
#include <vector>
#include <map>
#include <algorithm>
#include "raster/tilereader/TileReaderFactory2.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Raster::TileReader;

namespace takenginetests {
	struct TestTileReader {

		TestTileReader()
			: index(0)
		{
		}
		~TestTileReader()
		{
			unregisterAll();
		}

		std::shared_ptr<TileReaderSpi2> addType(const char *name, int priority, TAKErr c, TAKErr s);
		void touch(const char *name);
		std::vector<std::string> getTouchOrder();
		void resetTouches();
		void unregisterAll();

	private:
		int index;
		std::vector<std::shared_ptr<TileReaderSpi2>> types;
		std::map<std::string, int> touches;
	};

	class TestTileReaderSpi2 : public TileReaderSpi2 {
	public:
		TestTileReaderSpi2(TestTileReader &cxt, const char *name, int priority, TAKErr supportedRes, TAKErr createRes)
			: cxt(cxt), name(name), priority(priority), supportedRes(supportedRes), createRes(createRes)
		{
		}

		virtual ~TestTileReaderSpi2() NOTHROWS
		{
		}

		virtual TAKErr create(TileReader2Ptr &result, const char *uri, const TileReaderFactory2Options *options) const NOTHROWS
		{
			cxt.touch(name.c_str());
			return createRes;
		}

		virtual TAKErr isSupported(const char *uri) const NOTHROWS
		{
			cxt.touch(name.c_str());
			return supportedRes;
		}

		virtual const char *getName() const NOTHROWS
		{
			return name.c_str();
		}

		virtual int getPriority() const NOTHROWS
		{
			return priority;
		}
	private:
		TestTileReader &cxt;
		std::string name;
		int priority;
		TAKErr supportedRes;
		TAKErr createRes;
	};

	static void delSpi(const TestTileReaderSpi2 *spi)
	{
		delete spi;
	}

	std::shared_ptr<TileReaderSpi2> TestTileReader::addType(const char *name, int priority, TAKErr c, TAKErr s)
	{
		types.push_back(std::shared_ptr<TestTileReaderSpi2>(new TestTileReaderSpi2(*this, name, priority, c, s), delSpi));
		return types.back();
	}

	void TestTileReader::touch(const char *name)
	{
		touches.insert(std::make_pair(std::string(name), this->index));
		this->index++;
	}

	std::vector<std::string> TestTileReader::getTouchOrder()
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

	void TestTileReader::resetTouches()
	{
		touches.clear();
		index = 0;
	}

	void TestTileReader::unregisterAll() {
		for (std::vector<std::shared_ptr<TileReaderSpi2>>::iterator iter = types.begin(); iter != types.end(); iter++)
		{
			TileReaderFactory2_unregister(*iter);
		}
		types.clear();
	}


	TEST(TileReaderFactory2Tests, testTileReaderPriorityTouchOrder)
	{

		TestTileReader context;

		TAKErr code = TileReaderFactory2_register(context.addType("last", 0, TE_Unsupported, TE_Unsupported));
		ASSERT_TRUE(code == TE_Ok);

		code = TileReaderFactory2_register(context.addType("middle", 1, TE_Unsupported, TE_Unsupported));
		ASSERT_TRUE(code == TE_Ok);

		code = TileReaderFactory2_register(context.addType("first", 3, TE_Unsupported, TE_Unsupported));
		ASSERT_TRUE(code == TE_Ok);

		code = TileReaderFactory2_isSupported("foo");
		ASSERT_TRUE(code == TE_Unsupported);

		// check touch order on isSupported
		std::vector<std::string> touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)3);
		ASSERT_STREQ(touches[0].c_str(), "first");
		ASSERT_STREQ(touches[1].c_str(), "middle");
		ASSERT_STREQ(touches[2].c_str(), "last");

		context.resetTouches();

		TileReader2Ptr result(nullptr, nullptr);
		code = TileReaderFactory2_create(result, "foo");
		ASSERT_TRUE(code == TE_Unsupported);

		// check touch order on create
		touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)3);
		ASSERT_STREQ(touches[0].c_str(), "first");
		ASSERT_STREQ(touches[1].c_str(), "middle");
		ASSERT_STREQ(touches[2].c_str(), "last");

	}

	TEST(TileReaderFactory2Tests, testTileReaderPriorityTouchOrderSucceed)
	{

		TestTileReader context;

		TAKErr code = TileReaderFactory2_register(context.addType("last", 0, TE_Unsupported, TE_Unsupported));
		ASSERT_TRUE(code == TE_Ok);

		code = TileReaderFactory2_register(context.addType("middle", 1, TE_Ok, TE_Ok));
		ASSERT_TRUE(code == TE_Ok);

		code = TileReaderFactory2_register(context.addType("first", 3, TE_Unsupported, TE_Unsupported));
		ASSERT_TRUE(code == TE_Ok);

		code = TileReaderFactory2_isSupported("foo");
		ASSERT_TRUE(code == TE_Ok);

		// check touch order on isSupported
		std::vector<std::string> touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)2);
		ASSERT_STREQ(touches[0].c_str(), "first");
		ASSERT_STREQ(touches[1].c_str(), "middle");

		context.resetTouches();

		TileReader2Ptr result(nullptr, nullptr);
		code = TileReaderFactory2_create(result, "foo");
		ASSERT_TRUE(code == TE_Ok);

		// check touch order on create
		touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)2);
		ASSERT_STREQ(touches[0].c_str(), "first");
		ASSERT_STREQ(touches[1].c_str(), "middle");
	}

	TEST(TileReaderFactory2Tests, testTileReaderHint)
	{

		TestTileReader context;

		TAKErr code = TileReaderFactory2_register(context.addType("the_one", 0, TE_Ok, TE_Ok));
		ASSERT_TRUE(code == TE_Ok);
		code = TileReaderFactory2_register(context.addType("nothing", 0, TE_Ok, TE_Ok));
		ASSERT_TRUE(code == TE_Ok);
		code = TileReaderFactory2_register(context.addType("another_nothing", 0, TE_Ok, TE_Ok));
		ASSERT_TRUE(code == TE_Ok);

		code = TileReaderFactory2_isSupported("foo", "the_one");
		ASSERT_TRUE(code == TE_Ok);

		// check isSupported
		std::vector<std::string> touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)1);
		ASSERT_STREQ(touches[0].c_str(), "the_one");

		context.resetTouches();

		TileReader2Ptr result(nullptr, nullptr);
		TileReaderFactory2Options options;
		options.preferredSpi = "the_one";
		code = TileReaderFactory2_create(result, "foo", &options);
		ASSERT_TRUE(code == TE_Ok);

		// check touch order on create
		touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)1);
		ASSERT_STREQ(touches[0].c_str(), "the_one");
	}

	TEST(TileReaderFactory2Tests, testTileReaderFirstToFail)
	{

		// Test for create and isSupported returning a non-TE_Ok or TE_Unsupported error code and stopping
		// as this is considered a full failure

		TestTileReader context;

		TAKErr code = TileReaderFactory2_register(context.addType("a", 3, TE_Unsupported, TE_Unsupported));
		ASSERT_TRUE(code == TE_Ok);
		code = TileReaderFactory2_register(context.addType("b", 2, TE_Err, TE_Err));
		ASSERT_TRUE(code == TE_Ok);
		code = TileReaderFactory2_register(context.addType("c", 1, TE_Ok, TE_Ok));
		ASSERT_TRUE(code == TE_Ok);

		code = TileReaderFactory2_isSupported("foo");
		ASSERT_TRUE(code == TE_Err);

		// check isSupported
		std::vector<std::string> touches = context.getTouchOrder();
		ASSERT_EQ((size_t)2, touches.size());
		ASSERT_STREQ(touches[0].c_str(), "a");
		ASSERT_STREQ(touches[1].c_str(), "b");

		context.resetTouches();

		TileReader2Ptr result(nullptr, nullptr);
		code = TileReaderFactory2_create(result, "foo");
		ASSERT_TRUE(code == TE_Err);

		// check touch order on create
		touches = context.getTouchOrder();
		ASSERT_EQ((size_t)2, touches.size());
		ASSERT_STREQ(touches[0].c_str(), "a");
		ASSERT_STREQ(touches[1].c_str(), "b");
	}
}