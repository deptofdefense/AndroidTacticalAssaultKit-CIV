#include "pch.h"

#include <feature/FeatureSetDatabase.h>

using namespace TAK::Engine;
using namespace TAK::Engine::Feature;
using namespace TAK::Engine::Port;
using namespace TAK::Engine::Util;

using namespace TAK::Engine::Tests;

namespace takenginetests {

	class FeatureSetDatabaseTests : public ::testing::Test
	{
	protected:

		void SetUp() override
		{
			LoggerPtr logger(new TestLogger, Memory_deleter_const<Logger2, TestLogger>);
			Logger_setLogger(std::move(logger));
			Logger_setLevel(TELL_All);
		}
	};

	TEST_F(FeatureSetDatabaseTests, schemaOverride) {
		TAKErr code(TE_Ok);

		FeatureSetDatabase fdb;
		code = fdb.open(nullptr);
		ASSERT_EQ(TE_Ok, code);

		constexpr auto fsid = 1LL;

		code = fdb.insertFeatureSet(fsid, "provider", "type", "name", 0.0, std::numeric_limits<double>::max());

		for (std::size_t i = 0u; i < 2u; i++) {
			{
				atakmap::util::AttributeSet attributes1;
				attributes1.setInt("variant_attrib", 0);
				code = fdb.insertFeature(nullptr, fsid, "feature 1", atakmap::feature::Point(0.0, 0.0), TEAM_ClampToGround, 0.0, nullptr, attributes1);
				ASSERT_EQ(TE_Ok, code);
			}
			{
				atakmap::util::AttributeSet attributes2;
				attributes2.setDouble("variant_attrib", 0.0);
				code = fdb.insertFeature(nullptr, fsid, "feature 2", atakmap::feature::Point(0.0, 0.0), TEAM_ClampToGround, 0.0, nullptr, attributes2);
				ASSERT_EQ(TE_Ok, code);
			}
			{
				atakmap::util::AttributeSet attributes3;
				attributes3.setString("variant_attrib", "0");
				code = fdb.insertFeature(nullptr, fsid, "feature 3", atakmap::feature::Point(0.0, 0.0), TEAM_ClampToGround, 0.0, nullptr, attributes3);
				ASSERT_EQ(TE_Ok, code);
			}
		}
	}
}