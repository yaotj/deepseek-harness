package com.chinasofti.huateng.acc.es.server.netty.data;

import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class DeviceStatReport extends DataHead{
    private String nodeId;

    private String operator;

    private String actionCode;

    private String taskNo;

    private String stat;

    private String taskNum;

    private String completedNum;

    private String beginTime;

    private String endTime;

    private String remain;


    public static DeviceStatReport message2DeviceStatReport(MessageBean messageBean) {
        DeviceStatReport statReport = new DeviceStatReport();
        statReport.setTxnType(messageBean.getTxnType());
        statReport.setNodeId(messageBean.getNodeId());
        statReport.setSequence(messageBean.getSequence());
        statReport.setRequestType(messageBean.getRequestType());
        statReport.setIsFileTransaction(messageBean.getIsFileTransaction());
        statReport.setMack(messageBean.getMack());
        byte[] dataBody = messageBean.getDataBody();

        byte[] nodeId = new byte[8];
        System.arraycopy(dataBody,0,nodeId,0,8);
        statReport.setNodeId(new String(nodeId));
        byte[] operator = new byte[10];
        System.arraycopy(dataBody,8,operator,0,10);
        statReport.setOperator(new String(operator));
        byte[] actionCode= new byte[1];
        System.arraycopy(dataBody,18,actionCode,0,1);
        statReport.setActionCode(new String(actionCode));
        byte[] taskNo = new byte[8];
        System.arraycopy(dataBody,19,taskNo,0,8);
        statReport.setTaskNo(new String(taskNo));
        byte[] stat = new byte[1];
        System.arraycopy(dataBody,27,stat,0,1);
        statReport.setStat(new String(stat));
        byte[] taskNum = new byte[10];
        System.arraycopy(dataBody,28,taskNum,0,10);
        statReport.setTaskNum(new String(taskNum));
        byte[] completedNum = new byte[8];
        System.arraycopy(dataBody,38,completedNum,0,8);
        statReport.setCompletedNum(new String (completedNum));
        byte[] beginTime = new byte[14];
        System.arraycopy(dataBody,46,beginTime,0,14);
        statReport.setBeginTime(new String (beginTime));
        byte[] endTime = new byte[14];
        System.arraycopy(dataBody,60,endTime,0,14);
        statReport.setEndTime(new String(endTime));
        byte[] remain = new byte[8];
        System.arraycopy(dataBody,74,remain,0,8);
        statReport.setRemain(new String(remain));
        return statReport;

    }
}
