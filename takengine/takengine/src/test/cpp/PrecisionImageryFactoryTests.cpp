#include "pch.h"

#include <string>
#include <vector>
#include <map>
#include <algorithm>
#include "raster/PrecisionImageryFactory.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Raster;

namespace takenginetests {

	struct TestContext {

		TestContext()
			: index(0) { }

		std::shared_ptr<PrecisionImagerySpi> addType(const char *name, int priority, TAKErr c, TAKErr s);
		void touch(const char *name);
		std::vector<std::string> getTouchOrder();
		void resetTouches();

	private:
		int index;
		std::vector<std::shared_ptr<PrecisionImagerySpi>> types;
		std::map<std::string, int> touches;
	};

	class TestPrecisionImagerySpi : public PrecisionImagerySpi {
	public:
		TestPrecisionImagerySpi(TestContext &cxt, const char *type, int priority, TAKErr supportedRes, TAKErr createRes)
			: cxt(cxt), type(type), priority(priority), supportedRes(supportedRes), createRes(createRes)
		{ }

		virtual ~TestPrecisionImagerySpi() NOTHROWS
		{ }

		virtual TAKErr create(PrecisionImageryPtr &result, const char *URI) const NOTHROWS {
			cxt.touch(type.c_str());
			return createRes;
		}

		virtual TAKErr isSupported(const char *URI) const NOTHROWS {
			cxt.touch(type.c_str());
			return supportedRes;
		}

		virtual const char *getType() const NOTHROWS {
			return type.c_str();
		}

		virtual int getPriority() const NOTHROWS {
			return priority;
		}
	private:
		TestContext &cxt;
		std::string type;
		int priority;
		TAKErr supportedRes;
		TAKErr createRes;
	};

	std::shared_ptr<PrecisionImagerySpi> TestContext::addType(const char *name, int priority, TAKErr c, TAKErr s) {
		types.push_back(std::make_shared<TestPrecisionImagerySpi>(*this, name, priority, c, s));
		return types.back();
	}

	void TestContext::touch(const char *name) {
		touches.insert(std::make_pair(std::string(name), this->index));
		this->index++;
	}

	std::vector<std::string> TestContext::getTouchOrder() {
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

	void TestContext::resetTouches() {
		touches.clear();
		index = 0;
	}
	
	TEST(PrecisionImageryFactoryTests, testPriorityTouchOrder) {
		
		TestContext context;

		auto last = context.addType("last", 0, TE_Unsupported, TE_Unsupported);
		TAKErr code = PrecisionImageryFactory_register(last);
		ASSERT_TRUE(code == TE_Ok);

		auto middle = context.addType("middle", 1, TE_Unsupported, TE_Unsupported);
		code = PrecisionImageryFactory_register(middle);
		ASSERT_TRUE(code == TE_Ok);

		auto first = context.addType("first", 3, TE_Unsupported, TE_Unsupported);
		code = PrecisionImageryFactory_register(first);
		ASSERT_TRUE(code == TE_Ok);

		code = PrecisionImageryFactory_isSupported("foo");
		ASSERT_TRUE(code == TE_Unsupported);

		// check touch order on isSupported
		std::vector<std::string> touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)3);
		ASSERT_STREQ(touches[0].c_str(), "first");
		ASSERT_STREQ(touches[1].c_str(), "middle");
		ASSERT_STREQ(touches[2].c_str(), "last");

		context.resetTouches();

		PrecisionImageryPtr result(nullptr, nullptr);
		code = PrecisionImageryFactory_create(result, "foo");
		ASSERT_TRUE(code == TE_Unsupported);

		// check touch order on create
		touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)3);
		ASSERT_STREQ(touches[0].c_str(), "first");
		ASSERT_STREQ(touches[1].c_str(), "middle");
		ASSERT_STREQ(touches[2].c_str(), "last");

		PrecisionImageryFactory_unregister(first);
		PrecisionImageryFactory_unregister(middle);
		PrecisionImageryFactory_unregister(last);
	}

	TEST(PrecisionImageryFactoryTests, testPriorityTouchOrderSucceed) {

		TestContext context;

		auto last = context.addType("last", 0, TE_Unsupported, TE_Unsupported);
		TAKErr code = PrecisionImageryFactory_register(last);
		ASSERT_TRUE(code == TE_Ok);

		auto middle = context.addType("middle", 1, TE_Ok, TE_Ok);
		code = PrecisionImageryFactory_register(middle);
		ASSERT_TRUE(code == TE_Ok);

		auto first = context.addType("first", 3, TE_Unsupported, TE_Unsupported);
		code = PrecisionImageryFactory_register(first);
		ASSERT_TRUE(code == TE_Ok);

		code = PrecisionImageryFactory_isSupported("foo");
		ASSERT_TRUE(code == TE_Ok);

		// check touch order on isSupported
		std::vector<std::string> touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)2);
		ASSERT_STREQ(touches[0].c_str(), "first");
		ASSERT_STREQ(touches[1].c_str(), "middle");

		context.resetTouches();

		PrecisionImageryPtr result(nullptr, nullptr);
		code = PrecisionImageryFactory_create(result, "foo");
		ASSERT_TRUE(code == TE_Ok);

		// check touch order on create
		touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)2);
		ASSERT_STREQ(touches[0].c_str(), "first");
		ASSERT_STREQ(touches[1].c_str(), "middle");

		PrecisionImageryFactory_unregister(first);
		PrecisionImageryFactory_unregister(middle);
		PrecisionImageryFactory_unregister(last);
	}

	TEST(PrecisionImageryFactoryTests, testHint) {

		TestContext context;
		
		auto theOne = context.addType("the_one", 0, TE_Ok, TE_Ok);
		TAKErr code = PrecisionImageryFactory_register(theOne);
		ASSERT_TRUE(code == TE_Ok);
		auto nothing = context.addType("nothing", 0, TE_Ok, TE_Ok);
		code = PrecisionImageryFactory_register(nothing);
		ASSERT_TRUE(code == TE_Ok);
		auto anotherNothing = context.addType("another_nothing", 0, TE_Ok, TE_Ok);
		code = PrecisionImageryFactory_register(anotherNothing);
		ASSERT_TRUE(code == TE_Ok);

		code = PrecisionImageryFactory_isSupported("foo", "the_one");
		ASSERT_TRUE(code == TE_Ok);

		// check isSupported
		std::vector<std::string> touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)1);
		ASSERT_STREQ(touches[0].c_str(), "the_one");

		context.resetTouches();

		PrecisionImageryPtr result(nullptr, nullptr);
		code = PrecisionImageryFactory_create(result, "foo", "the_one");
		ASSERT_TRUE(code == TE_Ok);

		// check touch order on create
		touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)1);
		ASSERT_STREQ(touches[0].c_str(), "the_one");

		PrecisionImageryFactory_unregister(theOne);
		PrecisionImageryFactory_unregister(nothing);
		PrecisionImageryFactory_unregister(anotherNothing);
	}

	TEST(PrecisionImageryFactoryTests, testFirstToFail) {

		// Test for create and isSupported returning a non-TE_Ok or TE_Unsupported error code and stopping
		// as this is considered a full failure

		TestContext context;

		auto a = context.addType("a", 3, TE_Unsupported, TE_Unsupported);
		TAKErr code = PrecisionImageryFactory_register(a);
		ASSERT_TRUE(code == TE_Ok);
		auto b = context.addType("b", 2, TE_Err, TE_Err);
		code = PrecisionImageryFactory_register(b);
		ASSERT_TRUE(code == TE_Ok);
		auto c = context.addType("c", 1, TE_Ok, TE_Ok);
		code = PrecisionImageryFactory_register(c);
		ASSERT_TRUE(code == TE_Ok);

		code = PrecisionImageryFactory_isSupported("foo");
		ASSERT_TRUE(code == TE_Err);

		// check isSupported
		std::vector<std::string> touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)2);
		ASSERT_STREQ(touches[0].c_str(), "a");
		ASSERT_STREQ(touches[1].c_str(), "b");

		context.resetTouches();

		PrecisionImageryPtr result(nullptr, nullptr);
		code = PrecisionImageryFactory_create(result, "foo");
		ASSERT_TRUE(code == TE_Err);

		// check touch order on create
		touches = context.getTouchOrder();
		ASSERT_EQ(touches.size(), (size_t)2);
		ASSERT_STREQ(touches[0].c_str(), "a");
		ASSERT_STREQ(touches[1].c_str(), "b");

		PrecisionImageryFactory_unregister(a);
		PrecisionImageryFactory_unregister(b);
		PrecisionImageryFactory_unregister(c);
	}
}