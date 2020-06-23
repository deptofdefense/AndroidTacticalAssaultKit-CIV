//
//  MasterViewController.h
//  commoncommotest
//
//  Created by Jeff Downs on 11/16/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#import <UIKit/UIKit.h>

@class DetailViewController;

@interface MasterViewController : UITableViewController

@property (strong) NSMutableArray *contacts;
@property (strong) NSMutableArray *chatmsgs;

@property (strong, nonatomic) DetailViewController *detailViewController;

-(void)addChat:(NSString *)chat;

-(void)addContact:(NSString *)uid;
-(void)removeContact:(NSString *)uid;
-(void)interfaceStatus:(bool)up;

@end

