#ifndef TAK_ENGINE_UTIL_ERROR_HANDLING_H_INCLUDED
#define TAK_ENGINE_UTIL_ERROR_HANDLING_H_INCLUDED

#ifdef MSVC
#include <windows.h>
#include <winnt.h>

#include "port/Platform.h"

namespace TAK {
	namespace Engine {
		namespace Util {
			ENGINE_API void InitErrorHandling();

			void PureCallHandler();
			void InvalidParameterHandler(const wchar_t* expression, const wchar_t* function, const wchar_t* file, unsigned int line, uintptr_t pReserved);
			LONG WINAPI UnhandledExceptionFilter(PEXCEPTION_POINTERS pExceptionPtrs);
		}
	}
}
#endif //MSVC

#endif //TAK_ENGINE_UTIL_ERROR_HANDLING_H_INCLUDED