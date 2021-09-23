#include "util/ErrorHandling.h"

#ifdef MSVC
#include "util/Logging2.h"

#include <windows.h>
#include <winnt.h>

#include <string>
#include <locale>
#include <codecvt>
#include <vector>
#include <Psapi.h>
#include <algorithm>
#include <iomanip>
#include <iostream>
#include <stdexcept>
#include <iterator>
#include <imagehlp.h>
#include <sstream>
#include <shellapi.h>
#include <shlobj.h>

namespace TAK
{
	namespace Engine
	{
		namespace Util
		{
			struct module_data {
				std::string image_name;
				std::string module_name;
				void *base_address {nullptr};
				DWORD load_size {0};
			};
			typedef std::vector<module_data> ModuleList;

			bool show_stack(std::ostream &, HANDLE hThread, CONTEXT& c);
			void write_dump(HANDLE hProcess, PEXCEPTION_POINTERS pExceptionPtrs);
			void *load_modules_symbols(HANDLE hProcess, DWORD pid);

			void InitErrorHandling()
			{
				SetUnhandledExceptionFilter(UnhandledExceptionFilter);
				_set_purecall_handler(PureCallHandler);
				_set_invalid_parameter_handler(InvalidParameterHandler);
				_CrtSetReportMode(_CRT_ASSERT, 0);
			}

			void PureCallHandler()
			{
				std::string errorMessage = std::string("Pure Virtual Function Call Detected ");
				{
					HANDLE thread;
					HANDLE hProcess = GetCurrentProcess();
					DuplicateHandle(hProcess, GetCurrentThread(),
						hProcess, &thread, 0, false, DUPLICATE_SAME_ACCESS);

					EXCEPTION_RECORD exception_record = {};
					CONTEXT exception_context = {};
					EXCEPTION_POINTERS exception_ptrs = { &exception_record, &exception_context };
					::RtlCaptureContext(&exception_context);

					write_dump(hProcess, &exception_ptrs);

					std::ostringstream strm(std::ios_base::out | std::ios_base::binary);
					show_stack(strm, thread, exception_context);

					errorMessage += strm.str();
				}

				Logger_log(LogLevel::TELL_Severe, errorMessage.c_str());

#ifdef _DEBUG
                __debugbreak();
#endif
			}

			void InvalidParameterHandler(const wchar_t* expression, const wchar_t* function, const wchar_t* file, unsigned int line, uintptr_t pReserved)
			{
				std::string errorMessage = std::string("Invalid Parameter Detected in ");
				std::string str;
				std::wstring wstr = std::wstring(function);
				using convert_type = std::codecvt_utf8<wchar_t>;
				std::wstring_convert<convert_type, wchar_t> converter;
				if (wstr.length() > 0)
				{
					str = converter.to_bytes(wstr);
					errorMessage += str;
					errorMessage += "\t";
				}
				wstr = std::wstring(file);
				if (wstr.length() > 0)
				{
					str = converter.to_bytes(wstr);
					errorMessage += str;
					errorMessage += "\t";
					errorMessage += ":";
					if (line > 0)
					{
						errorMessage += std::to_string(line);
					}
				}
				wstr = std::wstring(expression);
				if (wstr.length() > 0)
				{
					str = converter.to_bytes(wstr);
					errorMessage += "\n";
					errorMessage += str;
				}

				Logger_log(LogLevel::TELL_Severe, errorMessage.c_str());

#ifdef _DEBUG
                __debugbreak();
#endif

				{
					HANDLE thread;
					HANDLE hProcess = GetCurrentProcess();
					DuplicateHandle(hProcess, GetCurrentThread(),
						hProcess, &thread, 0, false, DUPLICATE_SAME_ACCESS);

					EXCEPTION_RECORD exception_record = {};
					CONTEXT exception_context = {};
					EXCEPTION_POINTERS exception_ptrs = { &exception_record, &exception_context };
					::RtlCaptureContext(&exception_context);

					write_dump(hProcess, &exception_ptrs);

					std::ostringstream strm(std::ios_base::out | std::ios_base::binary);
					show_stack(strm, thread, exception_context);

					errorMessage += strm.str();
				}
			}

			LONG WINAPI UnhandledExceptionFilter(PEXCEPTION_POINTERS pExceptionPtrs)
			{
				std::string errorMessage = "Unhandled SEH Exception\n";
				switch (pExceptionPtrs->ExceptionRecord->ExceptionCode)
				{
				case EXCEPTION_ACCESS_VIOLATION:
					errorMessage += "Error: EXCEPTION_ACCESS_VIOLATION";
					break;
				case EXCEPTION_ARRAY_BOUNDS_EXCEEDED:
					errorMessage += "Error: EXCEPTION_ARRAY_BOUNDS_EXCEEDED";
					break;
				case EXCEPTION_BREAKPOINT:
					errorMessage += "Error: EXCEPTION_BREAKPOINT";
					break;
				case EXCEPTION_DATATYPE_MISALIGNMENT:
					errorMessage += "Error: EXCEPTION_DATATYPE_MISALIGNMENT";
					break;
				case EXCEPTION_FLT_DENORMAL_OPERAND:
					errorMessage += "Error: EXCEPTION_FLT_DENORMAL_OPERAND";
					break;
				case EXCEPTION_FLT_DIVIDE_BY_ZERO:
					errorMessage += "Error: EXCEPTION_FLT_DIVIDE_BY_ZERO";
					break;
				case EXCEPTION_FLT_INEXACT_RESULT:
					errorMessage += "Error: EXCEPTION_FLT_INEXACT_RESULT";
					break;
				case EXCEPTION_FLT_INVALID_OPERATION:
					errorMessage += "Error: EXCEPTION_FLT_INVALID_OPERATION";
					break;
				case EXCEPTION_FLT_OVERFLOW:
					errorMessage += "Error: EXCEPTION_FLT_OVERFLOW";
					break;
				case EXCEPTION_FLT_STACK_CHECK:
					errorMessage += "Error: EXCEPTION_FLT_STACK_CHECK";
					break;
				case EXCEPTION_FLT_UNDERFLOW:
					errorMessage += "Error: EXCEPTION_FLT_UNDERFLOW";
					break;
				case EXCEPTION_ILLEGAL_INSTRUCTION:
					errorMessage += "Error: EXCEPTION_ILLEGAL_INSTRUCTION";
					break;
				case EXCEPTION_IN_PAGE_ERROR:
					errorMessage += "Error: EXCEPTION_IN_PAGE_ERROR";
					break;
				case EXCEPTION_INT_DIVIDE_BY_ZERO:
					errorMessage += "Error: EXCEPTION_INT_DIVIDE_BY_ZERO";
					break;
				case EXCEPTION_INT_OVERFLOW:
					errorMessage += "Error: EXCEPTION_INT_OVERFLOW";
					break;
				case EXCEPTION_INVALID_DISPOSITION:
					errorMessage += "Error: EXCEPTION_INVALID_DISPOSITION";
					break;
				case EXCEPTION_NONCONTINUABLE_EXCEPTION:
					errorMessage += "Error: EXCEPTION_NONCONTINUABLE_EXCEPTION";
					break;
				case EXCEPTION_PRIV_INSTRUCTION:
					errorMessage += "Error: EXCEPTION_PRIV_INSTRUCTION";
					break;
				case EXCEPTION_SINGLE_STEP:
					errorMessage += "Error: EXCEPTION_SINGLE_STEP";
					break;
				case EXCEPTION_STACK_OVERFLOW:
					errorMessage += "Error: EXCEPTION_STACK_OVERFLOW";
					break;
				default:
					errorMessage += "Error: Unrecognized Exception 0x";
					std::stringstream sstream;
					sstream << std::hex << pExceptionPtrs->ExceptionRecord->ExceptionCode;
					errorMessage  += sstream.str();
					break;
				}

				Logger_log(LogLevel::TELL_Severe, errorMessage.c_str());
				
#ifdef _DEBUG
                __debugbreak();
#endif

				// RWI If this is a stack overflow then we can't walk the stack
				if (EXCEPTION_STACK_OVERFLOW != pExceptionPtrs->ExceptionRecord->ExceptionCode)
				{
					HANDLE thread;
					HANDLE hProcess = GetCurrentProcess();

					write_dump(hProcess, pExceptionPtrs);

					DuplicateHandle(hProcess, GetCurrentThread(),
						hProcess, &thread, 0, false, DUPLICATE_SAME_ACCESS);

					std::ostringstream strm(std::ios_base::out | std::ios_base::binary);
					show_stack(strm, thread, *(pExceptionPtrs->ContextRecord));

					errorMessage += strm.str();
				}

				// RWI - For now just let the default execute handler go next, which will terminate.
				// But we may want to consider returning EXCEPTION_CONTINUE_EXECUTION if we think we can continue safely depending on the type of exception
				return EXCEPTION_EXECUTE_HANDLER;
			}

			void write_dump(HANDLE hProcess, PEXCEPTION_POINTERS pExceptionPtrs)
			{
				CHAR szPath[MAX_PATH];
				CHAR szFileName[MAX_PATH];
				DWORD dwProcessId = GetCurrentProcessId();
				HANDLE hDumpFile;
				SYSTEMTIME stLocalTime;
				GetLocalTime(&stLocalTime);
				SHGetFolderPathA(nullptr, CSIDL_APPDATA, nullptr, 0, szPath);
				std::string filePath(szPath);
				filePath += "\\WinTAK\\Logs\\";
				snprintf(szFileName, MAX_PATH, "Crash-WinTAK-%04d-%02d-%02d-%ld-%ld.dmp",
					stLocalTime.wYear, stLocalTime.wMonth, stLocalTime.wDay,
					stLocalTime.wHour, stLocalTime.wMinute);
				filePath += szFileName;
				hDumpFile = CreateFile(filePath.c_str(), GENERIC_READ | GENERIC_WRITE,
					FILE_SHARE_WRITE | FILE_SHARE_READ, nullptr, CREATE_ALWAYS, 0, nullptr);

				MINIDUMP_EXCEPTION_INFORMATION ExpParam;
				ExpParam.ThreadId = GetCurrentThreadId();
				ExpParam.ExceptionPointers = pExceptionPtrs;
				ExpParam.ClientPointers = TRUE;

				MiniDumpWriteDump(hProcess, dwProcessId, hDumpFile, MiniDumpWithDataSegs, &ExpParam, nullptr, nullptr);
			}

			class SymHandler {
				HANDLE p;
			public:
				SymHandler(HANDLE process, char const *path = nullptr, bool intrude = false) : p(process) {
					if (!SymInitialize(p, path, intrude))
						throw(std::logic_error("Unable to initialize symbol handler"));
				}
				~SymHandler() { SymCleanup(p); }
			};

			STACKFRAME64 init_stack_frame(CONTEXT c) {
				STACKFRAME64 s;
#ifdef _WIN64
				s.AddrPC.Offset = c.Rip;
				s.AddrPC.Mode = AddrModeFlat;
				s.AddrStack.Offset = c.Rsp;
				s.AddrStack.Mode = AddrModeFlat;
				s.AddrFrame.Offset = c.Rbp;
				s.AddrFrame.Mode = AddrModeFlat;
#else
                s.AddrPC.Offset = c.Eip;
                s.AddrPC.Mode = AddrModeFlat;
                s.AddrStack.Offset = c.Esp;
                s.AddrStack.Mode = AddrModeFlat;
                s.AddrFrame.Offset = c.Ebp;
                s.AddrFrame.Mode = AddrModeFlat;
#endif
				return s;
			}

			void sym_options(DWORD add, DWORD remove = 0) {
				DWORD symOptions = SymGetOptions();
				symOptions |= add;
				symOptions &= ~remove;
				SymSetOptions(symOptions);
			}

			class symbol {
				typedef IMAGEHLP_SYMBOL64 sym_type;
				sym_type *sym;
				static const int max_name_len = 1024;
			public:
				symbol(HANDLE process, DWORD64 address) : sym((sym_type *)::operator new(sizeof(*sym) + max_name_len)) {
					memset(sym, '\0', sizeof(*sym) + max_name_len);
					sym->SizeOfStruct = sizeof(*sym);
					sym->MaxNameLength = max_name_len;
					DWORD64 displacement;

					SymGetSymFromAddr64(process, address, &displacement, sym);
				}

				std::string name() { return std::string(sym->Name); }
				std::string undecorated_name() {
					std::vector<char> und_name(max_name_len);
					UnDecorateSymbolName(sym->Name, &und_name[0], max_name_len, UNDNAME_COMPLETE);
					return std::string(&und_name[0], strlen(&und_name[0]));
				}
			};

			bool show_stack(std::ostream &os, HANDLE hThread, CONTEXT& c) {
				HANDLE process = GetCurrentProcess();
				int frame_number = 0;
				DWORD offset_from_symbol = 0;
				IMAGEHLP_LINE64 line = { 0 };

				SymHandler handler(process);

				sym_options(SYMOPT_LOAD_LINES | SYMOPT_UNDNAME);

				void *base = load_modules_symbols(process, GetCurrentProcessId());

				STACKFRAME64 s = init_stack_frame(c);

				line.SizeOfStruct = sizeof line;

				IMAGE_NT_HEADERS *h = ImageNtHeader(base);
				DWORD image_type = h->FileHeader.Machine;

				do {
					if (StackWalk64(image_type, process, hThread, &s, &c, nullptr, SymFunctionTableAccess64, SymGetModuleBase64, nullptr))
					{
						os << std::setw(3) << "\n" << frame_number << "\t";
						if (s.AddrPC.Offset != 0) {
							os << symbol(process, s.AddrPC.Offset).undecorated_name();

							if (SymGetLineFromAddr64(process, s.AddrPC.Offset, &offset_from_symbol, &line))
								os << "\t" << line.FileName << "(" << line.LineNumber << ")";
						}
						else
							os << "(No Symbols: PC == 0)";
						++frame_number;
					}
				} while (s.AddrReturn.Offset != 0);
				return true;
			}

			class get_mod_info {
				HANDLE process;
				static const int buffer_length = 4096;
			public:
				get_mod_info(HANDLE h) : process(h) {}

				module_data operator()(HMODULE module) {
					module_data ret;
					char temp[buffer_length];
					MODULEINFO mi;

					GetModuleInformation(process, module, &mi, sizeof(mi));
					ret.base_address = mi.lpBaseOfDll;
					ret.load_size = mi.SizeOfImage;

					GetModuleFileNameExA(process, module, temp, sizeof(temp));
					ret.image_name = temp;
					GetModuleBaseNameA(process, module, temp, sizeof(temp));
					ret.module_name = temp;
					std::vector<char> img(ret.image_name.begin(), ret.image_name.end());
					std::vector<char> mod(ret.module_name.begin(), ret.module_name.end());
					SymLoadModule64(process, nullptr, &img[0], &mod[0], (DWORD64)ret.base_address, ret.load_size);
					return ret;
				}
			};

			void *load_modules_symbols(HANDLE process, DWORD pid) {
				ModuleList modules;

				DWORD cbNeeded;
				std::vector<HMODULE> module_handles(1);

				EnumProcessModules(process, &module_handles[0], static_cast<DWORD>(module_handles.size() * sizeof(HMODULE)), &cbNeeded);
				module_handles.resize(cbNeeded / sizeof(HMODULE));
				EnumProcessModules(process, &module_handles[0], static_cast<DWORD>(module_handles.size() * sizeof(HMODULE)), &cbNeeded);

				std::transform(module_handles.begin(), module_handles.end(), std::back_inserter(modules), get_mod_info(process));
				return modules[0].base_address;
			}
		}
	}
}

#endif //MSVC
