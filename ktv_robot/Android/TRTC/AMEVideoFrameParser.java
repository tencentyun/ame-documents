package com.tencent.ame;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 视频帧解析
 */
public class AMEVideoFrameParser {
    private static final String magic = "amed";

    // 解析视频帧
    public static VideoColorFrameParseResult parseFromYUV(byte[] yuvData, int width, int height) {
        int[][] rgbData = getVideoRGBFromYUV(yuvData, width, height);
        int colorBitNum = (int)(Math.log(ColorCodec.values().length)/Math.log(2));
        int byteColorNum = 8/colorBitNum;

        int startIdx = 0, endIdx = VideoColorCodecProtocol.HEADER_MAGIC.getLen()*byteColorNum;
        byte[] headerMagicDataBuf = parseColorGrid(rgbData, startIdx,  endIdx);
        String magicData = convertStr(headerMagicDataBuf);
        if (!magicData.equals(magic)) {
            return new VideoColorFrameParseResult(false, "", String.format("invalid magic: %s", magicData));
        }

        startIdx = endIdx;
        endIdx += VideoColorCodecProtocol.HEADER_VERSION.getLen()*byteColorNum;
        byte[] headerVersionDataBuf = parseColorGrid(rgbData, startIdx, endIdx);
        int versionData = convertInt(headerVersionDataBuf);
        if (versionData <= 0) {
            return new VideoColorFrameParseResult(false, "", String.format("invalid version: %s", versionData));
        }

        startIdx = endIdx;
        endIdx += VideoColorCodecProtocol.HEADER_BODY_LEN.getLen()*byteColorNum;
        byte[] headerBodyLenDataBuf = parseColorGrid(rgbData, startIdx, endIdx);
        int bodyLenData = convertInt(headerBodyLenDataBuf);
        int maxBodyLen = yuvData.length / 3 / byteColorNum;
        if (bodyLenData > maxBodyLen) {
            return new VideoColorFrameParseResult(false, "", String.format("invalid body len: %s", maxBodyLen));
        }

        startIdx = endIdx;
        endIdx += VideoColorCodecProtocol.HEADER_CHECKSUM.getLen()*byteColorNum;
        byte[] headerCheckSumDataBuf = parseColorGrid(rgbData, startIdx, endIdx);
        int checkSumData = convertInt(headerCheckSumDataBuf);

        startIdx = endIdx;
        endIdx += bodyLenData*byteColorNum;
        byte[] bodyDataBuf = parseColorGrid(rgbData, startIdx, endIdx);
        String bodyData = convertStr(bodyDataBuf);
        int actualSum = getBodySum(bodyData);
        if (actualSum != checkSumData) {
            return new VideoColorFrameParseResult(false, "", String.format("checksum not equal, expect:%" +
                    "s, actual: %s", checkSumData, actualSum));
        }

        return new VideoColorFrameParseResult(true, bodyData, "");
    }

    // 获取视频 RGB 数据
    private static int[][] getVideoRGBFromYUV(byte[] yuvData, int width, int height) {
        int[] rgbData = ColorConverter.I420ToRGB(yuvData, width, height);

        // 一维转二维
        int[][] videoRGB = new int[height][width*3];
        for(int i = 0; i < height; i++) {
            for(int j = 0; j < width*3; j++) {
                videoRGB[i][j] = rgbData[i * width * 3 + j];
            }
        }

        return videoRGB;
    }

    // 解析颜色格子
    private static byte[] parseColorGrid(int[][] videoRGB, int startIdx, int endIdx) {
        int x, y = 0;
        int width = videoRGB[0].length / 3;
        byte[] target = new byte[endIdx-startIdx];
        for(int i = startIdx; i < endIdx; i++) {
            int baseX = i / (width / VideoColorCodecProtocol.COLOR_GRID_LEN.getLen());
            int baseY = i % (width / VideoColorCodecProtocol.COLOR_GRID_LEN.getLen());

            x = baseX * VideoColorCodecProtocol.COLOR_GRID_LEN.getLen();
            y = baseY * VideoColorCodecProtocol.COLOR_GRID_LEN.getLen() * 3;
            Map<Integer, List<Integer>> colorCompareResult = new HashMap<>();
            for(int j = 0; j < VideoColorCodecProtocol.COLOR_GRID_LEN.getLen(); j++) {
                for(int z = 0; z < VideoColorCodecProtocol.COLOR_GRID_LEN.getLen()*3; z += 3) {
                    for (ColorCodec color : ColorCodec.values()){
                        int score = compareRGB(color.getRgb(), new int[]{
                                videoRGB[x+j][y+z],
                                videoRGB[x+j][y+z+1],
                                videoRGB[x+j][y+z+2]
                        });
                        List<Integer> scoreList = null;
                        if(colorCompareResult.containsKey(color.getNum())) {
                            scoreList = colorCompareResult.get(color.getNum());
                        } else {
                           scoreList = new ArrayList<>();
                        }
                        scoreList.add(score);
                        colorCompareResult.put(color.getNum(), scoreList);
                    }
                }
            }

            Map<Integer, Integer> colorCompareSortedResult = new HashMap<>();
            for (Map.Entry<Integer, List<Integer>> entry : colorCompareResult.entrySet()) {
                List<Integer> scoreList = entry.getValue();
                Collections.sort(scoreList);
                Integer avgSum = 0;
                for(int idx = 0; idx < VideoColorCodecProtocol.COLOR_GRID_LEN.getLen()*VideoColorCodecProtocol.COLOR_GRID_LEN.getLen()*0.9;idx++){
                    avgSum += scoreList.get(idx);
                }
                colorCompareSortedResult.put(entry.getKey(), avgSum);
            }

            List<Map.Entry<Integer, Integer>> list = new ArrayList<Map.Entry<Integer, Integer>>(colorCompareSortedResult.entrySet());
            Collections.sort(list, new Comparator<Map.Entry<Integer, Integer>>() {
                public int compare(Map.Entry<Integer, Integer> o1, Map.Entry<Integer, Integer> o2) {
                    return (o1.getValue() - o2.getValue());
                }
            });
            target[i - startIdx] = list.get(0).getKey().byteValue();
        }
        return target;
    }

    // 对比颜色差异值
    private static int compareRGB(int[] colorA, int[] colorB) {
        int absR = colorA[0]-colorB[0];
        int absG = colorA[1]-colorB[1];
        int absB = colorA[2]-colorB[2];
        return Double.valueOf(Math.sqrt(absR*absR+absG*absG+absB*absB)).intValue();
    }

    // 字节转字符串
    private static String convertStr(byte[] target) {
        int colorBitNum = (int)(Math.log(ColorCodec.values().length)/Math.log(2));
        int byteColorNum = 8/colorBitNum;

        byte[] result = new byte[target.length/byteColorNum];
        for(int i = 0; i < target.length; i = i+byteColorNum) {
            Integer cur = 0;
            for(int x = i+byteColorNum-1, y = 0; x >= i; x--, y++) {
                Integer combine = target[x] << (y*colorBitNum);
                cur += combine.intValue();
            }
            result[i/byteColorNum] = cur.byteValue();
        }
        return new String(result, StandardCharsets.UTF_8);
    }

    // 字节转整型
    private static int convertInt(byte[] target) {
        int colorBitNum = (int)(Math.log(ColorCodec.values().length)/Math.log(2));
        int result = 0;
        for(int i = target.length-1, j = 0; i >= 0; i--, j++) {
            Integer combine = target[i] << (j*colorBitNum);
            result += combine.intValue();
        }
        return result;
    }

    // 获取包体 CheckSum 值
    private static int getBodySum(String body) {
        int result = 0;
        for(byte b : body.getBytes(StandardCharsets.UTF_8)) {
            result += b;
        }
        return result;
    }
}

/**
 * 视频颜色帧解析结果
 */
class VideoColorFrameParseResult {
    private boolean isSuccess;
    private String content;
    private String errMsg;

    public VideoColorFrameParseResult(boolean isSuccess, String content, String errMsg) {
        this.isSuccess = isSuccess;
        this.content = content;
        this.errMsg = errMsg;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }
}

// 视频颜色编码协议
enum VideoColorCodecProtocol {
    HEADER_MAGIC(4),
    HEADER_VERSION(4),
    HEADER_BODY_LEN(4),
    HEADER_CHECKSUM(4),
    COLOR_GRID_LEN(4),
    BODY(0);

    VideoColorCodecProtocol(int len) {
        this.len = len;
    }

    private int len;

    public int getLen() {
        return len;
    }

    public void setLen(int len) {
        this.len = len;
    }
}

/**
 * 颜色编码
 */
enum ColorCodec {
    BLACK(0b00, new int[]{0, 0, 0}),
    PURPLE(0b01, new int[]{128, 0, 128}),
    YELLOW(0b10, new int[]{255, 255, 0}),
    WHITE(0b11, new int[]{255, 255, 255});

    ColorCodec(Integer num, int[] rgb) {
        this.num = num;
        this.rgb = rgb;
    }

    private Integer num;
    private int[] rgb;

    public Integer getNum() {
        return num;
    }

    public void setNum(Integer num) {
        this.num = num;
    }

    public int[] getRgb() {
        return rgb;
    }

    public void setRgb(int[] rgb) {
        this.rgb = rgb;
    }
}

/**
 * 颜色转换器
 */
class ColorConverter {
    private static final int R = 0;
    private static final int G = 1;
    private static final int B = 2;

    // I420是yuv420格式，是3个plane，排列方式为(Y)(U)(V)
    public static int[] I420ToRGB(byte[] src, int width, int height) {
        int numOfPixel = width * height;
        int positionOfU = numOfPixel / 4 + numOfPixel;
        int[] rgb = new int[numOfPixel * 3];
        for (int i = 0; i < height; i++) {
            int startY = i * width;
            int step = (i / 2) * (width / 2);
            int startU = numOfPixel + step;
            int startV = positionOfU + step;
            for (int j = 0; j < width; j++) {
                int y = startY + j;
                int u = startU + j / 2;
                int v = startV + j / 2;
                int index = y * 3;
                ColorConverter.RGB tmp = yuvToRGB(src[y], src[u], src[v]);
                rgb[index + R] = tmp.r;
                rgb[index + G] = tmp.g;
                rgb[index + B] = tmp.b;
            }
        }

        return rgb;
    }

    private static class RGB {
        public int r, g, b;
    }

    private static ColorConverter.RGB yuvToRGB(byte Y, byte U, byte V) {
        ColorConverter.RGB rgb = new ColorConverter.RGB();
        rgb.r = (int) ((Y & 0xff) + 1.4075 * ((V & 0xff) - 128));
        rgb.g = (int) ((Y & 0xff) - 0.3455 * ((U & 0xff) - 128) - 0.7169 * ((V & 0xff) - 128));
        rgb.b = (int) ((Y & 0xff) + 1.779 * ((U & 0xff) - 128));
        rgb.r = (rgb.r < 0 ? 0 : Math.min(rgb.r, 255));
        rgb.g = (rgb.g < 0 ? 0 : Math.min(rgb.g, 255));
        rgb.b = (rgb.b < 0 ? 0 : Math.min(rgb.b, 255));
        return rgb;
    }
}
