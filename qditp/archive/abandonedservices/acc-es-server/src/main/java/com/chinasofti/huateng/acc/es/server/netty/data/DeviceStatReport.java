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
    //编码分拣机标识
    private String nodeId;

    //操作员编号
    private String operator;

    //动作码
    private String actionCode;

    //任务标识
    private String taskNo;

    //设备工作状态
    private String stat;

    //任务数量
    private String taskNum;

    //完成数量
    private String completedNum;

    //任务起始时间
    private String beginTime;

    //任务结束时间
    private String endTime;

    //保留
    private String remain;


    public static DeviceStatReport message2DeviceStatReport(MessageBean messageBean) {
        DeviceStatReport statReport = new DeviceStatReport();
        //报文头
        statReport.setTxnType(messageBean.getTxnType());
        statReport.setNodeId(messageBean.getNodeId());
        statReport.setSequence(messageBean.getSequence());
        statReport.setRequestType(messageBean.getRequestType());
        statReport.setIsFileTransaction(messageBean.getIsFileTransaction());
        statReport.setMack(messageBean.getMack());
        //报文体
        byte[] dataBody = messageBean.getDataBody();

        //编码分拣机标识
        byte[] nodeId = new byte[8];
        System.arraycopy(dataBody,0,nodeId,0,8);
        statReport.setNodeId(new String(nodeId));
        //操作员编号
        byte[] operator = new byte[10];
        System.arraycopy(dataBody,8,operator,0,10);
        statReport.setOperator(new String(operator));
        //动作码
        byte[] actionCode= new byte[1];
        System.arraycopy(dataBody,18,actionCode,0,1);
        statReport.setActionCode(new String(actionCode));
        //任务标识号
        byte[] taskNo = new byte[8];
        System.arraycopy(dataBody,19,taskNo,0,8);
        statReport.setTaskNo(new String(taskNo));
        //设备工作状态 0 正常 1暂停 3故障
        byte[] stat = new byte[1];
        System.arraycopy(dataBody,27,stat,0,1);
        statReport.setStat(new String(stat));
        //任务数量
        byte[] taskNum = new byte[10];
        System.arraycopy(dataBody,28,taskNum,0,10);
        statReport.setTaskNum(new String(taskNum));
        //完成数量
        byte[] completedNum = new byte[8];
        System.arraycopy(dataBody,38,completedNum,0,8);
        statReport.setCompletedNum(new String (completedNum));
        //任务起始时间 14
        byte[] beginTime = new byte[14];
        System.arraycopy(dataBody,46,beginTime,0,14);
        statReport.setBeginTime(new String (beginTime));
        //任务结束时间 14
        byte[] endTime = new byte[14];
        System.arraycopy(dataBody,60,endTime,0,14);
        statReport.setEndTime(new String(endTime));
        //保留 8
        byte[] remain = new byte[8];
        System.arraycopy(dataBody,74,remain,0,8);
        statReport.setRemain(new String(remain));
        return statReport;

    }
}
