#include "pch.h"

#include "util/ZipFile.h"

using namespace TAK::Engine::Util;

namespace takenginetests {

	TEST(ZipFileTests, testOpenNonExisting) {
		ZipFilePtr zipPtr(nullptr, nullptr);
		TAKErr code = ZipFile::open(zipPtr, "!!__DOESNT_EXIST__!!");
		ASSERT_EQ((int)code, (int)TE_InvalidArg);
		ASSERT_EQ(nullptr, zipPtr.get());
	}

	TEST(ZipFileTests, testOpenExisting) {
		ZipFilePtr zipPtr(nullptr, nullptr);
		std::string resource = TAK::Engine::Tests::getResource("test.zip");
		TAKErr code = ZipFile::open(zipPtr, resource.c_str());
		ASSERT_EQ((int)code, (int)TE_Ok);
		ASSERT_NE(nullptr, zipPtr.get());
	}

	TEST(ZipFileTests, testEnumerateFiles) {
		ZipFilePtr zipPtr(nullptr, nullptr);
		std::string resource = TAK::Engine::Tests::getResource("test.zip");
		TAKErr code = ZipFile::open(zipPtr, resource.c_str());
		ASSERT_EQ((int)code, (int)TE_Ok);
		ASSERT_NE(nullptr, zipPtr.get());

		code = zipPtr->gotoFirstEntry();
		ASSERT_EQ((int)code, (int)TE_Ok);

		TAK::Engine::Port::String filePath;
		code = zipPtr->getCurrentEntryPath(filePath);
		ASSERT_EQ((int)code, (int)TE_Ok);
		ASSERT_STREQ(filePath.get(), "0/");

		code = zipPtr->gotoNextEntry();
		ASSERT_EQ((int)code, (int)TE_Ok);

		code = zipPtr->getCurrentEntryPath(filePath);
		ASSERT_EQ((int)code, (int)TE_Ok);
		ASSERT_STREQ(filePath.get(), "0/test.txt");

		code = zipPtr->gotoNextEntry();
		ASSERT_EQ((int)code, (int)TE_Ok);

		code = zipPtr->getCurrentEntryPath(filePath);
		ASSERT_EQ((int)code, (int)TE_Ok);
		ASSERT_STREQ(filePath.get(), "test1.txt");

		code = zipPtr->gotoNextEntry();
		ASSERT_EQ((int)code, (int)TE_Done);
	}

	TEST(ZipFileTests, testGlobalComment) {
		std::string resource = TAK::Engine::Tests::getResource("test.zip");
		constexpr char writeComment[] = "written by unit test";
		TAKErr code(TE_Ok);

		{
			ZipFilePtr zipPtr(nullptr, nullptr);
			code = ZipFile::open(zipPtr, resource.c_str());
			ASSERT_EQ((int)code, (int)TE_Ok);
			ASSERT_NE(nullptr, zipPtr.get());

			TAK::Engine::Port::String readComment;
			code = zipPtr->getGlobalComment(readComment);
			ASSERT_EQ((int)code, (int)TE_Ok);
			ASSERT_STREQ(readComment.get(), "");
		}

		code = ZipFile::setGlobalComment(resource.c_str(), writeComment);
		ASSERT_EQ((int)code, (int)TE_Ok);

		{
			ZipFilePtr zipPtr(nullptr, nullptr);
			TAKErr code = ZipFile::open(zipPtr, resource.c_str());
			ASSERT_EQ((int)code, (int)TE_Ok);
			ASSERT_NE(nullptr, zipPtr.get());

			TAK::Engine::Port::String readComment;
			code = zipPtr->getGlobalComment(readComment);
			ASSERT_EQ((int)code, (int)TE_Ok);
			ASSERT_STREQ(writeComment, readComment.get());
		}

		// Reset back to an empty string.
		code = ZipFile::setGlobalComment(resource.c_str(), "");
		ASSERT_EQ((int)code, (int)TE_Ok);
	}
}
