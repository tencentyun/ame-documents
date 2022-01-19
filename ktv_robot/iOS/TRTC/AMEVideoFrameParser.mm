//
//  AMEVideoFrameParser.m
//
//  Created by jianguoxu on 2022/1/18.
//

#import "AMEVideoFrameParser.h"
#include "math.h"

static const NSString* MAGIC = @"amed";
static const int HEADER_MAGIC_BYTES = 4;
static const int HEADER_VERSION_BYTES = 4;
static const int HEADER_BODY_LEN_BYTES = 4;
static const int HEADER_CHECKSUM_BYTES = 4;
static const int COLOR_GRID_LEN = 4;
static const int COLOR_CODEC_NUM[4] = {0b00, 0b01, 0b10, 0b11};
static const int COLOR_CODEC_RGB[4][3] = {
    {0, 0, 0},
    {128, 0, 128},
    {255, 255, 0},
    {255, 255, 255}
};

@implementation VideoColorFrameParseResult

@end

@implementation AMEVideoFrameParser

+ (VideoColorFrameParseResult*)parseFromYUV:(NSData *)yuvData width:(uint32_t)width height:(uint32_t)height {
    Byte *yuvDataBytes = (Byte*)[yuvData bytes];
    int *rgbData = new int[width * height * 3];
    [ColorConverter i420ToRGB:rgbData src:yuvDataBytes width:width height:height];
    
    int colorCodecLen = sizeof(COLOR_CODEC_NUM) / sizeof(int);
    int colorBitNum = log2(colorCodecLen);
    int byteColorNum = 8 / colorBitNum;
    VideoColorFrameParseResult *result = [VideoColorFrameParseResult new];
    
    int startIdx = 0, endIdx = HEADER_MAGIC_BYTES * byteColorNum;
    Byte headerMagicDataBuf[endIdx - startIdx];
    [self parseColorGrid:headerMagicDataBuf rgbData:rgbData width:width startIdx:startIdx endIdx:endIdx];
    NSString *magicData = [self convertStr:headerMagicDataBuf len:endIdx - startIdx colorBitNum:colorBitNum byteColorNum:byteColorNum];
    if (![MAGIC isEqualToString:magicData]) {
        delete[] rgbData;
        [result setErrMsg:[NSString stringWithFormat:@"invalid magic: %@", magicData]];
        [result setIsSuccess:false];
        return result;
    }
    
    startIdx = endIdx;
    endIdx += HEADER_VERSION_BYTES * byteColorNum;
    Byte headerVersionDataBuf[endIdx - startIdx];
    [self parseColorGrid:headerVersionDataBuf rgbData:rgbData width:width startIdx:startIdx endIdx:endIdx];
    int versionData = [self convertInt:headerVersionDataBuf len:endIdx - startIdx colorBitNum:colorBitNum];
    if (versionData <= 0) {
        delete[] rgbData;
        [result setErrMsg:[NSString stringWithFormat:@"invalid version: %d", versionData]];
        [result setIsSuccess:false];
        return result;
    }
    
    startIdx = endIdx;
    endIdx += HEADER_BODY_LEN_BYTES * byteColorNum;
    Byte headerBodyLenDataBuf[endIdx - startIdx];
    [self parseColorGrid:headerBodyLenDataBuf rgbData:rgbData width:width startIdx:startIdx endIdx:endIdx];
    int bodyLenData = [self convertInt:headerBodyLenDataBuf len:endIdx - startIdx colorBitNum:colorBitNum];
    int maxBodyLen = width * height / (COLOR_GRID_LEN * COLOR_GRID_LEN) / byteColorNum;
    if (bodyLenData > maxBodyLen) {
        delete[] rgbData;
        [result setErrMsg:[NSString stringWithFormat:@"invalid body len: %d", bodyLenData]];
        [result setIsSuccess:false];
        return result;
    }
    
    startIdx = endIdx;
    endIdx += HEADER_CHECKSUM_BYTES * byteColorNum;
    Byte headerCheckSumDataBuf[endIdx - startIdx];
    [self parseColorGrid:headerCheckSumDataBuf rgbData:rgbData width:width startIdx:startIdx endIdx:endIdx];
    int checkSumData = [self convertInt:headerCheckSumDataBuf len:endIdx - startIdx colorBitNum:colorBitNum];
    
    startIdx = endIdx;
    endIdx += bodyLenData * byteColorNum;
    Byte bodyDataBuf[endIdx - startIdx];
    [self parseColorGrid:bodyDataBuf rgbData:rgbData width:width startIdx:startIdx endIdx:endIdx];
    NSString *bodyData = [self convertStr:bodyDataBuf len:endIdx - startIdx colorBitNum:colorBitNum byteColorNum:byteColorNum];
    int actualSum = [self getBodySum:bodyData];
    if (actualSum != checkSumData) {
        delete[] rgbData;
        [result setErrMsg:[NSString stringWithFormat:@"checksum not equal, expect: %d, actual: %d", checkSumData, actualSum]];
        [result setIsSuccess:false];
        return result;
    }
    
    delete[] rgbData;
    [result setContent:bodyData];
    [result setIsSuccess:true];
    return result;
}

+ (void)parseColorGrid:(Byte*)target rgbData:(int*)rgbData width:(uint32_t)width startIdx:(int)startIdx endIdx:(int)endIdx {
    int x, y = 0;
    int colorCodecLen = sizeof(COLOR_CODEC_NUM)/sizeof(int);
    
    for(int i = startIdx; i < endIdx; i++) {
        int baseX = i / (width / COLOR_GRID_LEN);
        int baseY = i % (width / COLOR_GRID_LEN);
        
        x = baseX * COLOR_GRID_LEN;
        y = baseY * COLOR_GRID_LEN * 3;
        int colorCompareResult[colorCodecLen][COLOR_GRID_LEN * COLOR_GRID_LEN];
        for (int j = 0; j < COLOR_GRID_LEN; j++) {
            for (int z = 0; z < COLOR_GRID_LEN * 3; z += 3) {
                int position = (x + j) * width * 3 + y + z;
                int compareTarget[3] = {
                    rgbData[position],
                    rgbData[position+1],
                    rgbData[position+2]
                };
                for (int n = 0; n < colorCodecLen; n++) {
                    int score = [self compareRGB:COLOR_CODEC_RGB[n] colorB:compareTarget];
                    int tempIdx = j * COLOR_GRID_LEN + z / 3;
                    colorCompareResult[n][tempIdx] = score;
                }
            }
        }
        int colorIndex = [self getSimilarColorIndex:&colorCompareResult[0][0] colorCodecLen:colorCodecLen colorGridNum:COLOR_GRID_LEN*COLOR_GRID_LEN];
        target[i - startIdx] = COLOR_CODEC_NUM[colorIndex];
    }
}

+ (int)convertInt:(Byte*)target len:(int)len colorBitNum:(int)colorBitNum {
    int result = 0;
    for (int i = len-1, j = 0; i >= 0 ; i--, j++) {
        result += target[i] << (j*colorBitNum);
    }
    return result;
}

+ (NSString*)convertStr:(Byte*)target len:(int)len colorBitNum:(int)colorBitNum byteColorNum:(int)byteColorNum {
    Byte result[len / byteColorNum];
    for (int i = 0; i < len; i += byteColorNum) {
        Byte cur = 0;
        for (int x = i + byteColorNum - 1, y = 0; x >= i; x--, y++) {
            cur += target[x] << (y * colorBitNum);
        }
        result[i / byteColorNum] = cur;
    }
    
    NSData *data = [[NSData alloc] initWithBytes:result length:len / byteColorNum];
    NSString *resultStr = [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    return resultStr;
}

+ (int)getBodySum:(NSString*)bodyData {
    int result = 0;
    NSData *rawData = [bodyData dataUsingEncoding: NSUTF8StringEncoding];
    Byte *rawBytes = (Byte *)[rawData bytes];
    
    for(int i = 0; i< [rawData length]; i++) {
        result += rawBytes[i];
    }
    
    return result;
}

+ (int)getSimilarColorIndex:(int*)colorCompareResult colorCodecLen:(int)colorCodecLen colorGridNum:(int)colorGridNum {
    for (int x = 0; x < colorCodecLen; x++) {
        for(int y = 0; y < colorGridNum - 1; y++) {
            for(int z = 0; z < colorGridNum-1-y; z++) {
                int idx = x * colorGridNum + y;
                if (*(colorCompareResult + idx) > *(colorCompareResult + idx + 1)) {
                    int temp = *(colorCompareResult + idx);
                    *(colorCompareResult + idx) = *(colorCompareResult + idx + 1);
                    *(colorCompareResult + idx + 1) = temp;
                }
            }
        }
    }
    
    int totalScore[colorCodecLen];
    int minScore = 0;
    int minScoreIdx = 0;
    for (int i = 0; i < colorCodecLen; i++) {
        totalScore[i] = 0;
        for (int j = 0; j < colorGridNum * 0.9; j++) {
            totalScore[i] += *(colorCompareResult + i * colorGridNum + j);
        }
        if (i == 0) {
            minScore =  totalScore[i];
        } else if (totalScore[i] < minScore) {
            minScore = totalScore[i];
            minScoreIdx = i;
        }
    }
    
    return minScoreIdx;
}

+ (int)compareRGB:(const int[3])baseColor colorB:(int[3])compareColor {
    int absR = baseColor[0]-compareColor[0];
    int absG = baseColor[1]-compareColor[1];
    int absB = baseColor[2]-compareColor[2];
    return (int)sqrt(absR*absR+absG*absG+absB*absB);
}

@end

@implementation ColorConverter

+ (void)i420ToRGB:(int*)rgb src:(Byte*)src width:(uint32_t)width height:(uint32_t)height {
    int numOfPixel = width * height;
    int positionOfU = numOfPixel / 4 + numOfPixel;
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
            int temp[3];
            [self yuvToRGB:temp y:src[y] u:src[u] v:src[v]];
            rgb[index + 0] = temp[0];
            rgb[index + 1] = temp[1];
            rgb[index + 2] = temp[2];
        }
    }
}

+ (void)yuvToRGB:(int*)rgb y:(Byte)y u:(Byte)u v:(Byte)v {
    rgb[0] = (int) ((y & 0xff) + 1.4075 * ((v & 0xff) - 128));
    rgb[1] = (int) ((y & 0xff) - 0.3455 * ((u & 0xff) - 128) - 0.7169 * ((v & 0xff) - 128));
    rgb[2] = (int) ((y & 0xff) + 1.779 * ((u & 0xff) - 128));
    rgb[0] = (rgb[0] < 0 ? 0 : rgb[0] > 255 ? 255 : rgb[0]);
    rgb[1] = (rgb[1] < 0 ? 0 : rgb[1] > 255 ? 255 : rgb[1]);
    rgb[2] = (rgb[2] < 0 ? 0 : rgb[2] > 255 ? 255 : rgb[2]);
}

@end
