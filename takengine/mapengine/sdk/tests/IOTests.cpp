#include "pch.h"

#include "util/ZipFile.h"
#include "port/STLVectorAdapter.h"

using namespace TAK::Engine::Util;

namespace takenginetests {

	TEST(IOTests, testExistsVIntoZip) {
		std::string resource = TAK::Engine::Tests::getResource("test.zip\\0\\test.txt");
		std::string resourceNoexist = TAK::Engine::Tests::getResource("test.zip\\NOEXIST");

		bool exists = false;
		TAKErr code = IO_existsV(&exists, resource.c_str());
		ASSERT_EQ((int)code, (int)TE_Ok);
		ASSERT_TRUE(exists);

		exists = true;
		code = IO_existsV(&exists, resourceNoexist.c_str());
		ASSERT_EQ((int)code, (int)TE_Ok);
		ASSERT_FALSE(exists);
	}

	TEST(IOTests, testIsDirectoryV) {
		std::string resource = TAK::Engine::Tests::getResource("test.zip");

		bool value = false;
		TAKErr code = IO_isDirectoryV(&value, resource.c_str());
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_TRUE(value);

		resource = TAK::Engine::Tests::getResource("test.zip\\0");
		value = false;
		code = IO_isDirectoryV(&value, resource.c_str());
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_TRUE(value);
	}

	TEST(IOTests, testListFiles) {
		std::string resource = TAK::Engine::Tests::getResource("testdir");

		TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> files;
		TAKErr code = IO_listFiles(files, resource.c_str());
		ASSERT_EQ((int)TE_Ok, (int)code);

		TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String>::IteratorPtr iter(nullptr, nullptr);
		code = files.iterator(iter);
		ASSERT_EQ((int)TE_Ok, (int)code);

		TAK::Engine::Port::String path;
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\0.txt").c_str(), path.get());

		code = iter->next();
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\1.txt").c_str(), path.get());

		code = iter->next();
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\a").c_str(), path.get());

		code = iter->next();
		ASSERT_EQ((int)TE_Done, (int)code);
	}

	TEST(IOTests, testRecursivelyListFiles) {
		std::string resource = TAK::Engine::Tests::getResource("testdir");

		TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> files;
		TAKErr code = IO_listFiles(files, resource.c_str(), TELFM_Recursive);
		ASSERT_EQ((int)TE_Ok, (int)code);

		TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String>::IteratorPtr iter(nullptr, nullptr);
		code = files.iterator(iter);
		ASSERT_EQ((int)TE_Ok, (int)code);

		TAK::Engine::Port::String path;
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\0.txt").c_str(), path.get());

		code = iter->next();
		ASSERT_EQ((int)TE_Ok, (int)code);
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\1.txt").c_str(), path.get());

		code = iter->next();
		ASSERT_EQ((int)TE_Ok, (int)code);
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\a").c_str(), path.get());

		code = iter->next();
		ASSERT_EQ((int)TE_Ok, (int)code);
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\a\\a0.txt").c_str(), path.get());

		code = iter->next();
		ASSERT_EQ((int)TE_Ok, (int)code);
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\a\\a1.txt").c_str(), path.get());

		code = iter->next();
		ASSERT_EQ((int)TE_Done, (int)code);
	}

	TEST(IOTests, testListFilesVInZipRoot) {
		std::string resource = TAK::Engine::Tests::getResource("test.zip");

		TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> files;
		TAKErr code = IO_listFilesV(files, resource.c_str(), TELFM_Immediate);
		ASSERT_EQ((int)TE_Ok, (int)code);

		TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String>::IteratorPtr iter(nullptr, nullptr);
		code = files.iterator(iter);
		ASSERT_EQ((int)TE_Ok, (int)code);

		TAK::Engine::Port::String path;
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\0").c_str(), path.get());

		code = iter->next();
		ASSERT_EQ((int)TE_Ok, (int)code);
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\test1.txt").c_str(), path.get());

		code = iter->next();
		ASSERT_EQ((int)TE_Done, (int)code);
	}

	TEST(IOTests, testListFilesVInZipDirEntry) {
		std::string resource = TAK::Engine::Tests::getResource("test.zip\\0");

		TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String> files;
		TAKErr code = IO_listFilesV(files, resource.c_str(), TELFM_Immediate);
		ASSERT_EQ((int)TE_Ok, (int)code);

		TAK::Engine::Port::STLVectorAdapter<TAK::Engine::Port::String>::IteratorPtr iter(nullptr, nullptr);
		code = files.iterator(iter);
		ASSERT_EQ((int)TE_Ok, (int)code);

		TAK::Engine::Port::String path;
		code = iter->get(path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ((resource + "\\test.txt").c_str(), path.get());

		code = iter->next();
		ASSERT_EQ((int)TE_Done, (int)code);
	}

	TEST(IOTests, testGetExtZip) {

		const char *path = "folder.thing/foo.zip";
		const char *extPos = nullptr;
		TAK::Engine::Port::String ext;

		TAK::Engine::Util::TAKErr code = TAK::Engine::Util::IO_getExt(ext, &extPos, path);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ(ext.get(), ".thing");

		code = TAK::Engine::Util::IO_getExt(ext, &extPos, extPos + 1);
		ASSERT_EQ((int)TE_Ok, (int)code);
		ASSERT_STREQ(ext.get(), ".zip");

		code = TAK::Engine::Util::IO_getExt(ext, &extPos, extPos + 1);
		ASSERT_EQ((int)TE_Done, (int)code);
		ASSERT_EQ(nullptr, extPos);
	}
}