//
//  AMEVideoRenderExample.m
//
//  Created by jianguoxu on 2022/1/18.
//

#import "AMEVideoRenderExample.h"
#import "AMEVideoFrameParser.h"

@interface AMEVideoRenderExample () <TRTCVideoRenderDelegate>

@end

@implementation AMEVideoRenderExample

#pragma mark - TRTCVideoRenderDelegate

- (void)onRenderVideoFrame:(TRTCVideoFrame *)frame userId:(NSString *)userId
                streamType:(TRTCVideoStreamType)streamType {
    NSLog(@"AMEVideoRenderExample frame width: %d, height: %d", frame.width, frame.height);
    VideoColorFrameParseResult *result = [AMEVideoFrameParser parseFromYUV:frame.data width:frame.width height:frame.height];
    if (result.isSuccess) {
        NSLog(@"AMEVideoRenderExample Content: %@", result.content);
    } else {
        NSLog(@"AMEVideoRenderExample Error: %@", result.errMsg);
    }
}

@end
