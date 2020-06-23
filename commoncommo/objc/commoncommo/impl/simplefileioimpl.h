#ifndef simplefileioimpl_h
#define simplefileioimpl_h

#import "simplefileio_objc.h"
#include <simplefileio.h>


namespace atakmap {
    namespace commoncommo {
        namespace objcimpl {
            class SimpleFileIOImpl : public atakmap::commoncommo::SimpleFileIO
            {
            public:
                SimpleFileIOImpl(id<CommoSimpleFileIO> objcImpl);
                virtual ~SimpleFileIOImpl();
                
                virtual void fileTransferUpdate(const SimpleFileIOUpdate *update);

            private:
                id<CommoSimpleFileIO> objcImpl;
            };
        }
    }
}


@interface CommoSimpleFileIOUpdateImpl : NSObject <CommoSimpleFileIOUpdate> {
}

@property (readonly) int transferId;
@property (readonly) CommoSimpleFileIOStatus status;
@property (readonly) NSString *additionalInfo;
@property (readonly) int64_t totalBytesTransferred;
@property (readonly) int64_t totalBytesToTransfer;

-(instancetype)initWithNativeSimpleFileIOUpdate:(const atakmap::commoncommo::SimpleFileIOUpdate *) nativeImpl;
-(instancetype)init;

@end


#endif /*  */
