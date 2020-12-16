#include "pch.h"

#include <util/MathUtils.h>

using namespace TAK::Engine::Util;

namespace takenginetests {

	TEST(MathUtilsTests, testInterpolate_four_valid_values) {
        const double weightX = 0.5;
        const double weightY = 0.2;
        const double ul = 91.0;
        const double ur = 210.0;
        const double lr = 95.0;
        const double ll = 162.0;

        const double v = MathUtils_interpolate(ul, ur, lr, ll, weightX, weightY);
        ASSERT_EQ(146.1, v);
	}
}