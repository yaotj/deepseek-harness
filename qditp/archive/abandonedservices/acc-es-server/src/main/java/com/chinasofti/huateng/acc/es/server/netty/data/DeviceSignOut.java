package com.chinasofti.huateng.acc.es.server.netty.data;

import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceSignOut extends DataHead{
    private String nodeId;
    private String operator;
    private String passWard;

    public static DeviceSignOut message2DeviceSignOut(MessageBean messageBean) {
        DeviceSignOut deviceSignout = new DeviceSignOut();
        deviceSignout.setTxnType(messageBean.getTxnType());
        deviceSignout.setNodeId(messageBean.getNodeId());
        deviceSignout.setSequence(messageBean.getSequence());
        deviceSignout.setRequestType(messageBean.getRequestType());
        deviceSignout.setIsFileTransaction(messageBean.getIsFileTransaction());
        deviceSignout.setMack(messageBean.getMack());
        byte[] dataBody = messageBean.getDataBody();
        byte[] nodeId = new byte[8];
        System.arraycopy(dataBody,0,nodeId,0,8);

        byte[] operator = new byte[10];
        System.arraycopy(dataBody,8,operator,0,10);

        byte[] passWard = new byte[32];
        System.arraycopy(dataBody,18,passWard,0,32);
        deviceSignout.setNodeId(new String(nodeId));
        deviceSignout.setOperator(new String(operator));
        deviceSignout.setPassWard(new String(passWard));
        return deviceSignout;
    }
}
