#include "pch.h"
#include "renderer/GLText2.h"

namespace TAK {
	namespace Engine {
		namespace Renderer {
			extern ENGINE_API TAKErr GLText2_setBidirectionalTextMode(BidirectionalTextMode mode) NOTHROWS;
		}
	}
}

namespace takenginetests {

	TEST(GLTextLocalizeTests, testEmptyString) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostLeftToRight);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"");
		TAK::Engine::Port::String expect(u8"");
		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testWhiteSTring) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostLeftToRight);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8" ");
		TAK::Engine::Port::String expect(u8" ");
		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testNoTransform) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostLeftToRight);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"should be no transform");
		TAK::Engine::Port::String expect(u8"should be no transform");
		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());
		
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testNoTransformSingleCharacter) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostLeftToRight);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test("X");
		TAK::Engine::Port::String expect("X");
		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());
		
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostRightToLeft);

		result = nullptr;
		code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());
		
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformPrefix) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostLeftToRight);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"\u0600\u0610should be mixed");
		TAK::Engine::Port::String expect(u8"\u0610\u0600should be mixed");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformSuffix) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostLeftToRight);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"should be mixed\u0600\u0610");
		TAK::Engine::Port::String expect(u8"should be mixed\u0610\u0600");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformOneMiddle) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostLeftToRight);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"should \u0600 be same");
		TAK::Engine::Port::String expect(u8"should \u0600 be same");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformPrefixSpaceMiddle) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostLeftToRight);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"\u0600\u0610 \u0620\u0630should be mixed");
		TAK::Engine::Port::String expect(u8"\u0630\u0620 \u0610\u0600should be mixed");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformMultiLine) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostLeftToRight);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"\u0600\u0610\nmulti-line");
		TAK::Engine::Port::String expect(u8"\u0610\u0600\nmulti-line");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	///

	TEST(GLTextLocalizeTests, testEmptyStringRTL) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostRightToLeft);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"");
		TAK::Engine::Port::String expect(u8"");
		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testWhiteSTringRTL) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostRightToLeft);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8" ");
		TAK::Engine::Port::String expect(u8" ");
		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testNoTransformRTL) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostRightToLeft);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"should be no transform");
		TAK::Engine::Port::String expect(u8"should be no transform");
		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformPrefixRTL) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostRightToLeft);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"\u0600\u0610should be mixed");
		TAK::Engine::Port::String expect(u8"should be mixed\u0610\u0600");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformSuffixRTL) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostRightToLeft);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"should be mixed\u0600\u0610");
		TAK::Engine::Port::String expect(u8"\u0610\u0600should be mixed");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformOneMiddleRTL) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostRightToLeft);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"should \u0600 be same");
		TAK::Engine::Port::String expect(u8"be same \u0600 should");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformPrefixSpaceMiddleRTL) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostRightToLeft);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"\u0600\u0610 \u0620\u0630should be mixed");
		TAK::Engine::Port::String expect(u8"should be mixed\u0630\u0620 \u0610\u0600");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}

	TEST(GLTextLocalizeTests, testTransformMultiLineRTL) {

		TAK::Engine::Renderer::GLText2_setBidirectionalTextMode(TAK::Engine::Renderer::TEBTM_HostRightToLeft);

		TAK::Engine::Port::String result;
		TAK::Engine::Port::String test(u8"\u0600\u0610\nmulti-line");
		TAK::Engine::Port::String expect(u8"\u0610\u0600\nmulti-line");

		TAK::Engine::Util::TAKErr code = TAK::Engine::Renderer::GLText2_localize(&result, test.get());

		ASSERT_TRUE(TE_Ok == code);
		ASSERT_TRUE(result == expect);
	}
}
