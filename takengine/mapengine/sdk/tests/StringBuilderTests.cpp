#include "pch.h"

#include "port/StringBuilder.h"

using namespace TAK::Engine::Util;

namespace takenginetests {

	TEST(StringBuilderTests, testAppendResize) {
		
		TAK::Engine::Port::StringBuilder sb;
		ASSERT_EQ(sb.length(), (size_t)0);

		sb.resize(0);
		ASSERT_EQ(sb.length(), (size_t)0);

		sb.resize(14);
		ASSERT_EQ(sb.length(), (size_t)0);
		ASSERT_EQ(sb.capacity(), (size_t)15);

		sb.resize(128);
		ASSERT_EQ(sb.length(), (size_t)0);
		ASSERT_EQ(sb.capacity(), (size_t)129);

	}
}