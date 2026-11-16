package com.chinasofti.huateng.acc.es.server.netty.data;
import com.chinasofti.huateng.acc.es.server.netty.model.Constant;
import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import lombok.*;

/**
 * @program: spring-cloud-acc
 * @description: 设备签到
 * @author: fc
 * @create: 2020-09-09 10:52
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DeviceSignIn extends DataHead{
    /**
     * 节点标识码
     */
    private String esNodeId;
    /**
     * 操作员编码5位后补空格
     */
    private String operatorCode;
    /**
     * 操作员密码
     */
    private String operatorPassword;


    /**
     * 7000 string转byte
     * @param deviceSignIn
     * @return
     */
    public byte[] toBytesArray(DeviceSignIn deviceSignIn){
        byte[] dataBody = new byte[Constant.DataLength.DATA_BODY_7000_SIGN_BYTES];
        System.arraycopy(deviceSignIn.getEsNodeId().getBytes(), 0, dataBody,0, 8);
        System.arraycopy(deviceSignIn.getOperatorCode().getBytes(), 0, dataBody, 8, 10);
        System.arraycopy(deviceSignIn.getOperatorPassword().getBytes(), 0, dataBody, 18, 32);
        return dataBody;
    }

    /**
     * 7000 byte转string
     * @param messageBean
     * @return
     */
    public static  DeviceSignIn toDeviceSignInStr(MessageBean messageBean){
        DeviceSignIn deviceSignIn=new DeviceSignIn();
        // 报文头
        deviceSignIn.setTxnType(messageBean.getTxnType());
        deviceSignIn.setNodeId(messageBean.getNodeId());
        deviceSignIn.setSequence(messageBean.getSequence());
        deviceSignIn.setRequestType(messageBean.getRequestType());
        deviceSignIn.setIsFileTransaction(messageBean.getIsFileTransaction());
        deviceSignIn.setMack(messageBean.getMack());

        byte[] dataBody=messageBean.getDataBody();
        byte[] esNodeId=new byte[8];
        System.arraycopy(dataBody, 0, esNodeId, 0, 8);
        deviceSignIn.setEsNodeId(new String(esNodeId));
        byte[] operatorCode=new byte[10];
        System.out.println(dataBody.length);
        System.arraycopy(dataBody, 8, operatorCode,0, 10);
        deviceSignIn.setOperatorCode(new String(operatorCode));
        byte[] operatorPassword=new byte[32];
        System.arraycopy(dataBody, 18, operatorPassword, 0, 32);
        deviceSignIn.setOperatorPassword(new String(operatorPassword));

        return deviceSignIn;
    }


    private static int intConvert(byte[] bytes, int startLength) {
        int int1 = bytes[startLength] & 0xff;
        int int2 = bytes[startLength + 1] & 0xff;
        int int3 = bytes[startLength + 2] & 0xff;
        int int4 = bytes[startLength + 3] & 0xff;
        return int1 + int2 * 256 + int3 * 65536 + int4 * 16777216;

    }
}
