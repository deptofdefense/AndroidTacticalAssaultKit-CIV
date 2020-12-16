#include "pch.h"

#include <vector>

#include "util/HttpProtocolHandler.h"
#include "util/URI.h"

using namespace TAK::Engine::Util;
using namespace TAK::Engine::Tests;

namespace takenginetests {
	TEST(HttpProtocolHandlerTests, testGoogle) {
		//XXX-- need to find out how we can test INET type connections with CI
#if 0
		TAK::Engine::Port::String baseURI;

		HttpProtocolHandler handler;

		DataInput2Ptr input(nullptr, nullptr);
		handler.handleURI(input, "https://www.google.com");
		if (input) {
			size_t numRead = 0;
			uint8_t buf[1024];
			memset(buf, 0, sizeof(buf));
			input->read(buf, &numRead, 1023);
		}
#endif
	}
}
