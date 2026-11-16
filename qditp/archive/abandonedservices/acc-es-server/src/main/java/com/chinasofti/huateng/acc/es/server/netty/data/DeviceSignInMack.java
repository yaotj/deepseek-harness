package com.chinasofti.huateng.acc.es.server.netty.data;

import com.chinasofti.huateng.acc.es.server.netty.model.Constant;
import com.chinasofti.huateng.acc.es.server.util.ByteConvertUtil;
import lombok.Builder;
import lombok.Data;

/**
 * @program: cloud-acc-server
 * @description: 消息签到应答
 * @author: fc
 * @create: 2020-09-18 14:07
 */
@Data
@Builder(toBuilder=true)
public class DeviceSignInMack {
    /**
     * 单位(秒),数值为0时不需要状态报告，长度为6
     */
   private String deviceStateInterval;

   private String operatorRank;


    public static byte[] toBytesArray(DeviceSignInMack deviceSignInMack){
        byte[] dataBody = new byte[Constant.DataLength.DATA_BODY_7000_SIGN_BYTES_MACK];
        byte[] deviceStateIntervals= ByteConvertUtil.addZeroForNum(deviceSignInMack.getDeviceStateInterval(),6).getBytes();
        System.arraycopy(deviceStateIntervals, 0, dataBody,0, 6);
        byte[] operatorRank = ByteConvertUtil.addZeroForNum(deviceSignInMack.getOperatorRank(),2).getBytes();
        System.arraycopy(operatorRank, 0, dataBody, 6, 2);
        return dataBody;
    }
}
