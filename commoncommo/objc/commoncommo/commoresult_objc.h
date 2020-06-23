//
//  commoresult_objc.h
//  commoncommo
//
//  Created by Jeff Downs on 12/10/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef commoresult_objc_h
#define commoresult_objc_h

typedef NS_ENUM(NSInteger, CommoResult) {
    CommoResultSuccess,
    CommoResultIllegalArgument,
    CommoResultContactGone,
    CommoResultInvalidCert,
    CommoResultInvalidCACert,
    CommoResultInvalidCertPass,
    CommoResultInvalidCACertPass,
};

#endif /* commoresult_objc_h */
