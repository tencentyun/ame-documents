## AME 机器人视频流解析

## 传递数据格式

```json
{
	"musicPlayProgress": 278921,	// 音乐播放进度，单位：毫秒
	"customMsg": {}			// 自定义消息，通过云 API 的同步操作指令接口设置
}
```

## 终端解析

### Android

#### TRTC

##### Step 1：导入解析代码

将 Android/TRTC 目录里面的 `AMEVideoFrameParser.java` 源文件导入到工程中，修改里面的包路径。

##### Step 2：创建视频渲染监听器

可以参考 Android/TRTC 目录里面提供的`AMEVideoRenderExample.java` 源文件，调用解析类解析视频帧的 YUV 数据，获取里面实际传递的内容：

```
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
```

实际解析到的结果如下：

```
2022-01-17 21:34:32.895 14363-15330/com.tencent.trtc.api_example D/AMEVideoRenderExample: frame.width:320, frame.height:320
2022-01-17 21:34:32.935 14363-15330/com.tencent.trtc.api_example D/AMEVideoRenderExample: Content: {"musicPlayProgress":279119}
2022-01-17 21:34:32.986 14363-15330/com.tencent.trtc.api_example D/AMEVideoRenderExample: frame.width:320, frame.height:320
2022-01-17 21:34:33.033 14363-15330/com.tencent.trtc.api_example D/AMEVideoRenderExample: Content: {"musicPlayProgress":279185}
```

##### Step 3：设置视频渲染监听器

在 AME 侧创建完一个机器人后会绑定一个用户 ID，所以在终端需要监听改用户的视频流渲染：

```
mTRTCCloud.setRemoteVideoRenderListener(
                "userId",
                TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_I420,
                TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY,
                new AMEVideoRenderExample()			// Step 2 创建的监听器
 );
```


### IOS

todo

