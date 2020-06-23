//
//  contactuid_objc.h
//  commoncommo
//
//  Created by Jeff Downs on 12/10/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#ifndef contactuid_objc_h
#define contactuid_objc_h

@protocol CommoContactPresenceListener <NSObject>

-(void)contactAdded:(NSString *)uid;
-(void)contactRemoved:(NSString *)uid;

@end

#endif /* contact_objc_h */
