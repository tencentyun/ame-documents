package com.tencent.ame;

import android.util.Log;

import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;

public class AMEVideoRenderExample implements TRTCCloudListener.TRTCVideoRenderListener {
    @Override
    public void onRenderVideoFrame(String userId, int streamType, TRTCCloudDef.TRTCVideoFrame frame) {
        Log.d("AMEVideoRenderExample", "frame.width:" + frame.width + ", frame.height:" + frame.height);

        VideoColorFrameParseResult result = AMEVideoFrameParser.parseFromYUV(frame.data, frame.width, frame.height);
        if (result.isSuccess()) {
            Log.d("AMEVideoRenderExample", "Content: " + result.getContent());
        } else {
            Log.d("AMEVideoRenderExample", "Error: " + result.getErrMsg());
        }
    }
}
