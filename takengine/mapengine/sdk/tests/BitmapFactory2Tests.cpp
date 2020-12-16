#include "pch.h"

#include <Windows.h>
#include <ObjIdl.h>
#ifdef NOMINMAX
#define min(a, b) ((a)<(b) ? (a) : (b))
#define max(a, b) ((a)<(b) ? (a) : (b))
#include <gdiplus.h>
#pragma comment (lib, "Gdiplus.lib")
#include <gdiplusheaders.h>
#include <gdipluspixelformats.h>
#undef min
#undef max
#else
#include <gdiplus.h>
#pragma comment (lib, "Gdiplus.lib")
#include <gdiplusheaders.h>
#include <gdipluspixelformats.h>
#endif

#ifndef MSVC
#define MSVC
#include "renderer/impl/BitmapAdapter_MSVC.h"
#undef MSVC
#else
#include "renderer/impl/BitmapAdapter_MSVC.h"
#endif

#include "renderer/Bitmap2.h"
#include "renderer/BitmapFactory2.h"
#include "util/DataInput2.h"
#include "util/DataOutput2.h"
#include "util/Memory.h"

#include "gdal_priv.h"

using namespace TAK::Engine::Renderer;
using namespace TAK::Engine::Util;
using namespace TAK::Engine::Tests;

namespace {

	TAKErr gdi_factory_decode(BitmapPtr &result, DataInput2 &input, const BitmapDecodeOptions *opts);

	ULONG_PTR           gdiplusToken(0);

#if _DEBUG
	HexdumpLogger       hexLogger;
#endif
}

namespace takenginetests {

	class BitmapFactory2Tests : public ::testing::Test
	{
	protected:

		void SetUp() override
		{
			LoggerPtr logger(new TestLogger, Memory_deleter_const<Logger2, TestLogger>);
			Logger_setLogger(std::move(logger));
			Logger_setLevel(TELL_All);
			
			// initialize GDI+ and GDAL because we're not within the application which has its own initializer
			GDALAllRegister();

			Gdiplus::GdiplusStartupInput gdiplusStartupInput;
			Gdiplus::GdiplusStartup(&gdiplusToken, &gdiplusStartupInput, NULL);
		}

		void TearDown() override
		{
			Gdiplus::GdiplusShutdown(gdiplusToken);
		}
	};

	TEST_F(BitmapFactory2Tests, testBitmapDecode) {
		TAKErr code(TE_Ok);

		const std::vector<std::string> resources = {
			TAK::Engine::Tests::getResource("m_3807707_se_18_060_20181025_bsq.tif"),
			TAK::Engine::Tests::getResource("FLAG_B24.PNG")
		};

		for (size_t i = 0, max = resources.size(); max > i; ++i) {

			FileInput2 fileInput;
			code = fileInput.open(resources[i].c_str());
			ASSERT_TRUE(TE_Ok == code);

			// MSVC specific bitmap
			BitmapPtr gdipBitmap(NULL, NULL);
			code = gdi_factory_decode(gdipBitmap, fileInput, NULL);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_NE(nullptr, gdipBitmap.get());

			// need to close and reopen
			fileInput.close();
			code = fileInput.open(resources[i].c_str());
			ASSERT_TRUE(TE_Ok == code);

			// cross platform bitmap
			BitmapPtr gdalBitmap(NULL, NULL);
			code = BitmapFactory2_decode(gdalBitmap, fileInput, NULL);
			ASSERT_TRUE(TE_Ok == code);
			ASSERT_NE(nullptr, gdalBitmap.get());

			fileInput.close();

			// bitmap comparison
			const std::size_t width = gdalBitmap->getWidth();
			const std::size_t height = gdalBitmap->getHeight();
			const std::size_t stride = gdalBitmap->getStride();
			const std::size_t dataLength = stride * height;

			ASSERT_TRUE(width == gdipBitmap->getWidth());
			ASSERT_TRUE(height == gdipBitmap->getHeight());

			const Bitmap2::Format gdalFormat = gdalBitmap->getFormat();
			const Bitmap2::Format gdipFormat = gdipBitmap->getFormat();

			if (gdalFormat != gdipFormat) {
				gdipBitmap.reset(new Bitmap2(*gdipBitmap, width, height, gdalFormat));
			}

			ASSERT_TRUE(gdalFormat == gdipBitmap->getFormat());
			ASSERT_TRUE(stride == gdipBitmap->getStride());

			const int relates = memcmp(gdalBitmap->getData(), gdipBitmap->getData(), dataLength);

			if (0 != relates) {
				std::ostringstream os;
				os << resources[i] << " memcmp: " << relates;

				Logger_log(TELL_Debug, os.str().c_str());

#if _DEBUG
				hexLogger.log("gdip", gdipBitmap->getData(), dataLength);
				hexLogger.log("gdal", gdalBitmap->getData(), dataLength);
#endif
			}

			ASSERT_TRUE(0 == relates);
		}
	}
}

namespace {

	class HandleLock
	{
	public:
		HandleLock(HGLOBAL handle_) :
			handle(handle_),
			data(::GlobalLock(handle_))
		{}
		~HandleLock() NOTHROWS
		{
			::GlobalUnlock(handle);
		}
	public:
		void *get() NOTHROWS
		{
			return data;
		}
	private:
		HGLOBAL handle;
		void *data;
	};

	void handle_deleter(HGLOBAL handle)
	{
		::GlobalFree(handle);
	}
	void istream_deleter(IStream *stream)
	{
		stream->Release();
	}

	TAKErr gdi_factory_decode(BitmapPtr &result, DataInput2 &input, const BitmapDecodeOptions *opts)
	{
		using namespace TAK::Engine::Renderer::Impl;

		TAKErr code(TE_Ok);
		DynamicOutput membuf;
		code = membuf.open(64u * 1024u);
		TE_CHECKRETURN_CODE(code);
		code = IO_copy(membuf, input);
		TE_CHECKRETURN_CODE(code);

		const uint8_t *data;
		std::size_t dataLen;
		code = membuf.get(&data, &dataLen);
		TE_CHECKRETURN_CODE(code);

		std::unique_ptr<void, void(*)(HGLOBAL)> bufferHandle(::GlobalAlloc(GMEM_MOVEABLE, dataLen), handle_deleter);
		if (!bufferHandle.get())
			return TE_Err;

		{
			HandleLock lock(bufferHandle.get());
			if (!lock.get())
				return TE_Err;
			CopyMemory(lock.get(), data, dataLen);
			IStream *stream = NULL;
			if (::CreateStreamOnHGlobal(bufferHandle.get(), FALSE, &stream) != S_OK)
				return TE_Err;
			std::unique_ptr<IStream, void(*)(IStream *)> streamPtr(stream, istream_deleter);
			std::unique_ptr<Gdiplus::Bitmap> bitmap(Gdiplus::Bitmap::FromStream(streamPtr.get()));
			if (!bitmap.get()) {
				const DWORD err = GetLastError();

				LPWSTR pBuffer = NULL;

				FormatMessage(FORMAT_MESSAGE_FROM_SYSTEM |
					FORMAT_MESSAGE_ALLOCATE_BUFFER,
					&err,
					0,
					0,
					(LPWSTR)&pBuffer,
					0,
					NULL);
				Logger_log(TELL_Debug, LPSTR(pBuffer));
				return TE_Err;
			}
			if (bitmap->GetLastStatus() != Gdiplus::Ok)
				return TE_Err;
			code = BitmapAdapter_adapt(result, *bitmap);
			TE_CHECKRETURN_CODE(code);
		}

		return code;
	}
}
