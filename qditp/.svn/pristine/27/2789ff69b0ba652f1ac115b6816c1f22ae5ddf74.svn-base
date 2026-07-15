package com.chinasofti.huateng.acc.es.server.netty.data;

import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class TaskApply extends DataHead{

    /**
     * 编码机标识
     */
    private String esNodeId;

    /**
     * 请求任务的日期
     */
    private String date;

    /**
     * 把信息转化未TaskApply对象
     * @param messageBean
     * @return
     */
    public static TaskApply message2TaskApply(MessageBean messageBean) {
        TaskApply apply = new TaskApply();
        apply.setTxnType(messageBean.getTxnType());
        apply.setNodeId(messageBean.getNodeId());
        apply.setSequence(messageBean.getSequence());
        apply.setRequestType(messageBean.getRequestType());
        apply.setIsFileTransaction(messageBean.getIsFileTransaction());
        apply.setMack(messageBean.getMack());
        byte[] dataBody = messageBean.getDataBody();
        byte[] esNodeId=new byte[8];
        System.arraycopy(dataBody,0,esNodeId,0,8);
        byte[] date = new byte[8];
        System.arraycopy(dataBody,8,date,0,8);
        apply.setEsNodeId(new String(esNodeId));
        apply.setDate(new String(date));
        return apply;
    }
}
