#ifndef PGSCTHREAD_PLATFORM_H_INCLUDED
#define PGSCTHREAD_PLATFORM_H_INCLUDED

#if __cplusplus >= 201103L || _MSC_VER >= 1900
#define PGSCT_NOTHROWS noexcept
#else
#define PGSCT_NOTHROWS throw ()
#endif


#ifdef WIN32
 #ifdef PGSCTHREAD_EXPORTS
  #define PGSCTHREAD_API __declspec(dllexport)
 #else
  #define PGSCTHREAD_API __declspec(dllimport)
 #endif
#else
 #define PGSCTHREAD_API
#endif

#endif
