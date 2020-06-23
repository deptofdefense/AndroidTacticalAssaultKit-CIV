//
//  DetailViewController.h
//  commoncommotest
//
//  Created by Jeff Downs on 11/16/15.
//  Copyright © 2015 Jeff Downs. All rights reserved.
//

#import <UIKit/UIKit.h>

@interface DetailViewController : UIViewController

@property (strong, nonatomic) id detailItem;
@property (weak, nonatomic) IBOutlet UILabel *detailDescriptionLabel;

@end

