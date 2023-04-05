#ifndef COMMOUTILS_H_
#define COMMOUTILS_H_


#if defined(WIN32) && !defined(UWP)
 #ifdef COMMONCOMMO_EXPORTS
  #define COMMONCOMMO_API __declspec(dllexport)
 #else
  #define COMMONCOMMO_API __declspec(dllimport)
 #endif
 #define COMMO_DEPRECATED __declspec(deprecated)
#else
 #define COMMONCOMMO_API
 #define COMMO_DEPRECATED __attribute((deprecated))
#endif

#define COMMO_DISALLOW_COPY(ClsName)                          \
    ClsName(const ClsName &);                                 \
    ClsName &operator=(const ClsName &)                       \


namespace atakmap {
namespace commoncommo {

}
}


#endif /* COMMOUTILS_H_ */
