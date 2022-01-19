//
//  AMEVideoFrameParser.h
//
//  Created by jianguoxu on 2022/1/18.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface VideoColorFrameParseResult : NSObject

@property (nonatomic) bool isSuccess;
@property (strong, nonatomic) NSString* content;
@property (strong, nonatomic) NSString *errMsg;

@end

@interface AMEVideoFrameParser : NSObject

+ (VideoColorFrameParseResult*)parseFromYUV:(NSData *)yuvData width:(uint32_t)width height:(uint32_t)height;

@end

@interface ColorConverter : NSObject

+ (void)i420ToRGB:(int*)rgb src:(Byte*)src width:(uint32_t)width height:(uint32_t)height;

@end

NS_ASSUME_NONNULL_END
