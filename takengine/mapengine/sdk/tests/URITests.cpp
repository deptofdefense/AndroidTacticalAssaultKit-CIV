#include "pch.h"

#include <cmath>

#include <util/Memory.h>
#include <util/URI.h>

using namespace TAK::Engine;
using namespace TAK::Engine::Util;

using namespace TAK::Engine::Tests;

namespace takenginetests {
	class URITests : public ::testing::Test
	{
	protected:

		void SetUp() override {
			// start fresh
			LoggerPtr logger(new TestLogger, Memory_deleter_const<Logger2, TestLogger>);
			Logger_setLogger(std::move(logger));
			Logger_setLevel(TELL_All);
		}
	};

	TEST(URITests, test_parse_full) {
		TAK::Engine::Port::String scheme;
		TAK::Engine::Port::String authority;
		TAK::Engine::Port::String user;
		TAK::Engine::Port::String host;
		TAK::Engine::Port::String port;
		TAK::Engine::Port::String path;
		TAK::Engine::Port::String query;
		TAK::Engine::Port::String fragment;

		TAKErr code = URI_parse(&scheme, &authority, &user, &host, &port, &path, &query, &fragment,
			"http://user:pass@host.com:80/p/a/t/h?q=1;r=2#FOO");

		ASSERT_EQ(code, TE_Ok);
		ASSERT_EQ(scheme, "http");
		ASSERT_EQ(authority, "user:pass@host.com:80");
		ASSERT_EQ(user, "user:pass");
		ASSERT_EQ(host, "host.com");
		ASSERT_EQ(port, "80");
		ASSERT_EQ(path, "/p/a/t/h");
		ASSERT_EQ(query, "q=1;r=2");
		ASSERT_EQ(fragment, "FOO");
	}

	TEST(URITests, test_parse_no_auth) {
		TAK::Engine::Port::String scheme;
		TAK::Engine::Port::String authority;
		TAK::Engine::Port::String user;
		TAK::Engine::Port::String host;
		TAK::Engine::Port::String port;
		TAK::Engine::Port::String path;
		TAK::Engine::Port::String query;
		TAK::Engine::Port::String fragment;

		TAKErr code = URI_parse(&scheme, &authority, &user, &host, &port, &path, &query, &fragment,
			"mailto:user@email.com?q=1;r=2#FOO");

		ASSERT_EQ(code, TE_Ok);
		ASSERT_EQ(scheme, "mailto");
		ASSERT_EQ(authority.get(), nullptr);
		ASSERT_EQ(user.get(), nullptr);
		ASSERT_EQ(host.get(), nullptr);
		ASSERT_EQ(port.get(), nullptr);
		ASSERT_EQ(path, "user@email.com");
		ASSERT_EQ(query, "q=1;r=2");
		ASSERT_EQ(fragment, "FOO");
	}

	TEST(URITests, test_full_combine) {
		TAK::Engine::Port::String result;
		TAKErr code = URI_combine(&result, "http://user:pass@host.com:80/p/a/t/h?q=1;r=2#FOO", "m/o/r/e");

		ASSERT_EQ(code, TE_Ok);
		ASSERT_EQ(result, "http://user:pass@host.com:80/p/a/t/h/m/o/r/e?q=1;r=2#FOO");
	}

	TEST(URITests, test_get_parent) {
		TAK::Engine::Port::String result;
		TAKErr code = URI_getParent(&result, "http://user:pass@host.com:80/p/a/t/h?q=1;r=2#FOO");

		ASSERT_EQ(code, TE_Ok);
		ASSERT_EQ(result, "http://user:pass@host.com:80/p/a/t?q=1;r=2#FOO");
	}

	TEST(URITests, test_get_relative_windows_path) {

		TAK::Engine::Port::String result;
		TAKErr code = URI_getRelative(&result, "c:\\windows\\style\\path\\", "c:\\windows\\style\\path\\folder\\file.txt");

		ASSERT_EQ(code, TE_Ok);
		ASSERT_EQ(result, "folder\\file.txt");


	}

	TEST(URITests, test_get_relative_unix_style_path) {

		TAK::Engine::Port::String result;
		TAKErr code = URI_getRelative(&result, "/unix/style/path/", "/unix/style/path/folder/file.txt");

		ASSERT_EQ(code, TE_Ok);
		ASSERT_EQ(result, "folder/file.txt");
	}

	TEST(URITests, test_get_relative_full_uri) {
		TAK::Engine::Port::String result;
		TAKErr code = URI_getRelative(&result, "http://user:pass@host.com:80/p/a/t/h", "http://user:pass@host.com:80/p/a/t/h/folder/file.txt");

		ASSERT_EQ(code, TE_Ok);
		ASSERT_EQ(result, "folder/file.txt");
	}
}