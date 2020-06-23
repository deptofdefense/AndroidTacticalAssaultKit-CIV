#ifndef IMPL_PROTOBUF_H
#define IMPL_PROTOBUF_H
// Convenience header to include protobuf files with any necessary
// preprocessor-fu to make them compile properly



// Set of Visual Studio warnings to turn off for Google protocol buffers
// See https://github.com/google/protobuf/blob/master/cmake/README.md
#ifdef WIN32
  #pragma warning(push)
  #pragma warning( disable : 4146 )  
  #pragma warning( disable : 4800 )  
#endif

#include "protobuf/cotevent.pb.h"
#include "protobuf/takmessage.pb.h"

#ifdef WIN32
  #pragma warning(pop)
#endif


#endif
