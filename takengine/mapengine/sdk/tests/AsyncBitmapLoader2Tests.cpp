#include "pch.h"

#include <algorithm>
#include <sstream>
#include <string>
#include <vector>

#include "renderer/AsyncBitmapLoader2.h"
#include "util/Memory.h"
#include "util/ProtocolHandler.h"

#include "TakEngineTestsResources.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Tests;

namespace takenginetests {

	TEST(AsyncBitmapLoader2Tests, testAsyncBitmapLoader2FileLoader) {
		TAKErr code(TE_Ok);
		FileProtocolHandler handler;
		DataInput2Ptr ctx(NULL, NULL);
		std::string resource = TAK::Engine::Tests::getResource("teapot.obj");

		code = handler.handleURI(ctx, resource.c_str());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(nullptr == ctx.get());
		ctx->close();

		std::replace(resource.begin(), resource.end(), '\\', '/');
		resource.insert(0, "file://");
		code = handler.handleURI(ctx, resource.c_str());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(nullptr == ctx.get());
		ctx->close();

		code = handler.handleURI(ctx, "http://sample.com/path/to/file");
		ASSERT_TRUE(TE_Unsupported == code);
	}

	TEST(AsyncBitmapLoader2Tests, testAsyncBitmapLoader2ZipLoader) {
		TAKErr code(TE_Ok);
		ZipProtocolHandler handler;
		DataInput2Ptr ctx(NULL, NULL);
		std::string resource = TAK::Engine::Tests::getResource("test.zip");
		std::replace(resource.begin(), resource.end(), '\\', '/');

		const std::string uri1 = "zip://" + resource + "!test1.txt";
		const std::string uri2 = "zip://" + resource + "!0/test.txt";
		const std::string uri3 = "arc://" + resource + "!0/test.txt";
		const std::string uri4 = "zip://" + resource;
		const std::string uri5 = "http://" + resource;

		std::istringstream is(resource);
		std::vector<std::string> tokens;
		std::string token;
		while (std::getline(is, token, '/'))
			tokens.push_back(token);

		std::string uri6("zip:/");
		for (std::size_t i = 0, max = tokens.size(), last = max - 1; max > i; ++i) {
			uri6 += (((0 == i) || (last == i))) ? ("/" + tokens[i]) : ("/" + tokens[i] + "/../" + tokens[i]);
		}
		uri6 += "!0\\../0/test.txt";

		resource = TAK::Engine::Tests::getResource("unitedNations.kmz");
		const std::string uri7 = "zip://" + resource + "!\\models\\../images/_08.jpg";

		code = handler.handleURI(ctx, uri1.c_str());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(nullptr == ctx.get());
		ctx->close();

		code = handler.handleURI(ctx, uri2.c_str());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(nullptr == ctx.get());
		ctx->close();

		code = handler.handleURI(ctx, uri3.c_str());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(nullptr == ctx.get());
		ctx->close();

		code = handler.handleURI(ctx, uri4.c_str());
		ASSERT_TRUE(TE_InvalidArg == code);

		code = handler.handleURI(ctx, uri5.c_str());
		ASSERT_TRUE(TE_Unsupported == code);

		code = handler.handleURI(ctx, uri6.c_str());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(nullptr == ctx.get());
		ctx->close();

		code = handler.handleURI(ctx, uri7.c_str());
		ASSERT_TRUE(TE_Ok == code);
		ASSERT_FALSE(nullptr == ctx.get());
		ctx->close();
	}
}