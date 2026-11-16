package com.chinasofti.huateng.acc.es.server.netty.data;

import com.chinasofti.huateng.acc.es.server.util.ByteConvertUtil;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Data
@Builder(toBuilder = true)
@Slf4j
public class TaskApplyMack {

    /**
     * 工作任务状态 1
     */
    private String taskStat;
    /**
     * 任务数 2
     */
    private String taskNum;
    /**
     * 后续任务 1
     */
    private String hasNext;
    /**
     * 工作任务
     */
    private List<WorkTask> tasks;

    public static byte[] toBytesArray(TaskApplyMack applyMack) {
        List<WorkTask> tasks = applyMack.getTasks();
        int bodyLength = 0;
        if (Objects.isNull(tasks)) {
            bodyLength = 4;
            tasks = Collections.EMPTY_LIST;
        } else {
            bodyLength = 4 + tasks.size() * 100;
        }
        byte[] byteBody = new byte[bodyLength];
        System.arraycopy(applyMack.getTaskStat().getBytes(),0,byteBody,0,1);
        byte[] taskNum = ByteConvertUtil.addZeroForNum(applyMack.getTaskNum(), 2).getBytes();
        System.arraycopy(taskNum,0,byteBody,1,2);
        System.arraycopy(applyMack.getHasNext().getBytes(),0,byteBody,3,1);
        int offset = 4;
        for (int i = 0 ; i < tasks.size(); i++) {
            WorkTask task = tasks.get(i);
            //任务发行
            if (task instanceof TicketPublishTask) {
                TicketPublishTask publishTask = (TicketPublishTask) task;
                //动作码 1
                System.arraycopy(publishTask.getActionCode().getBytes(),0,byteBody,offset,1);
                offset ++;
                byte[] taskNo = ByteConvertUtil.addZeroForNum(publishTask.getTaskNo(), 8).getBytes();
                //任务标识 8
                System.arraycopy(taskNo,0,byteBody,offset,8);
                offset += 8;
                // 任务变更标识 1
                System.arraycopy(publishTask.getChangeSign().getBytes(),0,byteBody,offset,1);
                offset ++;
                //车票类型 3
                byte[] ticketType = ByteConvertUtil.addZeroForNum(publishTask.getTicketType(), 3).getBytes();
                System.arraycopy(ticketType,0,byteBody,offset,3);
                offset += 3;
                //车票主类型
                byte[] mainType = ByteConvertUtil.addZeroForNum(publishTask.getTicketMainType(), 2).getBytes();
                System.arraycopy(mainType,0,byteBody,offset,2);
                offset += 2;
                //版本号 2
                byte[] version = ByteConvertUtil.addZeroForNum(publishTask.getVersion(), 6).getBytes();
                System.arraycopy(version,0,byteBody,offset,6);
                offset += 6;
                //批次标识
                byte[] batchNo = ByteConvertUtil.addZeroForNum(publishTask.getBatchNo(), 10).getBytes();
                System.arraycopy(batchNo,0,byteBody,offset,10);
                offset += 10;
                //任务数量
                byte[] num = ByteConvertUtil.addZeroForNum(publishTask.getTaskNum(), 10).getBytes();
                System.arraycopy(num,0,byteBody,offset,10);
                offset += 10;
                //最小序列号
                byte[] beginNo = ByteConvertUtil.addZeroForNum(publishTask.getBeginNo(), 10).getBytes();
                System.arraycopy(beginNo,0,byteBody,offset,10);
                offset += 10;
                //最大序列号
                byte[] endNo = ByteConvertUtil.addZeroForNum(publishTask.getEndNo(), 10).getBytes();
                System.arraycopy(endNo,0,byteBody,offset,10);
                offset += 10;
                //测试票标志
                System.arraycopy(publishTask.getIsTest().getBytes(),0,byteBody,offset,1);
                offset ++;
                //记名票标志
                System.arraycopy(publishTask.getIsNamed().getBytes(),0,byteBody,offset,1);
                offset ++;
                //纪念票标志
                System.arraycopy(publishTask.getIsMemorial().getBytes(),0,byteBody,offset,1);
                offset ++;
                //钱包单位
                byte[] walletUnit = ByteConvertUtil.addZeroForNum(publishTask.getWalletUnit(), 2).getBytes();
                System.arraycopy(walletUnit,0,byteBody,offset,2);
                offset += 2;
                //初始票值
                byte[] initAmt = ByteConvertUtil.addZeroForNum(publishTask.getInitAmt(), 9).getBytes();
                System.arraycopy(initAmt,0,byteBody,offset,9);
                offset += 9;
                //初始奖励值 5
                byte[] reward = ByteConvertUtil.addZeroForNum(publishTask.getRewordAmt(), 5).getBytes();
                System.arraycopy(reward,0,byteBody,offset,5);
                offset += 5;

                //压金 5
                byte[] depAmt = ByteConvertUtil.addZeroForNum(publishTask.getDepAmt(), 5).getBytes();
                System.arraycopy(depAmt,0,byteBody,offset,5);
                offset += 5;

                //有效天
                byte[] validDays = ByteConvertUtil.addZeroForNum(publishTask.getInvalidDays(), 6).getBytes();
                System.arraycopy(validDays,0,byteBody,offset,6);
                offset += 6;
                //有效期开始日期
                byte[] beginDay = ByteConvertUtil.addZeroForNum(publishTask.getBeginDate(), 8).getBytes();
                System.arraycopy(beginDay,0,byteBody,offset,8);
                offset += 8;
                System.arraycopy(publishTask.getTicketStatus().getBytes(),0,byteBody,offset,1);
                offset += 1;
            } else if (task instanceof TicketPreassignTask) {
                TicketPreassignTask preassignTask = (TicketPreassignTask) task;
                //动作码 1
                byte[] actionCode = preassignTask.getActionCode().getBytes();
                System.arraycopy(actionCode,0,byteBody,offset,1);
                offset ++;
                //任务标识 8
                byte[] taskNo = ByteConvertUtil.addZeroForNum(preassignTask.getTaskNo(), 8).getBytes();
                System.arraycopy(taskNo,0,byteBody,offset,8);
                offset += 8;
                //任务变更标志 1
                byte[] changeSign = preassignTask.getChangeSign().getBytes();
                System.arraycopy(changeSign,0,byteBody,offset,1);
                offset ++;
                //车票类型 3
                byte[] ticketType = ByteConvertUtil.addZeroForNum(preassignTask.getTicketType(), 3).getBytes();
                System.arraycopy(ticketType,0,byteBody,offset,3);
                offset += 3;
                //赋值日期 8
                byte[] assignDate = ByteConvertUtil.addZeroForNum(preassignTask.getAssignDate(), 8).getBytes();
                System.arraycopy(assignDate,0,byteBody,offset,8);
                offset += 8;
                //赋值数量 10
                byte[] assignNum = ByteConvertUtil.addZeroForNum(preassignTask.getAssignNum(), 10).getBytes();
                System.arraycopy(assignNum,0,byteBody,offset,10);
                offset += 10;
                //批次标识 10
                byte[] batchNo = ByteConvertUtil.addZeroForNum(preassignTask.getBatchNo(), 10).getBytes();
                System.arraycopy(batchNo,0,byteBody,offset,10);
                offset += 10;
                //初始票值 9
                byte[] intAmt = ByteConvertUtil.addZeroForNum(preassignTask.getInitAmt(), 9).getBytes();
                System.arraycopy(intAmt,0,byteBody,offset,9);
                offset += 9;
                //初始奖励值 5
                byte[] reward = ByteConvertUtil.addZeroForNum(preassignTask.getRewordAmt(), 5).getBytes();
                System.arraycopy(reward,0,byteBody,offset,5);
                offset += 5;
                //压金 5
                byte[] depAmt = ByteConvertUtil.addZeroForNum(preassignTask.getDepAmt(), 5).getBytes();
                System.arraycopy(depAmt,0,byteBody,offset,5);
                offset += 5;
                //有效期 6
                byte[] invalidays = ByteConvertUtil.addZeroForNum(preassignTask.getInvalidDays(), 6).getBytes();
                System.arraycopy(invalidays,0,byteBody,offset,6);
                offset += 6;
                //有效期开始时间 8
                byte[] bytes = ByteConvertUtil.addZeroForNum(preassignTask.getBeginDate(), 8).getBytes();
                System.arraycopy(bytes,0,byteBody,offset,8);
                offset += 8;
                //票卡状态
                System.arraycopy(preassignTask.getTicketStatus().getBytes(),0,byteBody,offset,1);
                offset += 1;
                //保留 25
                byte[] remain = ByteConvertUtil.addZeroForNum("0", 25).getBytes();
                System.arraycopy(remain,0,byteBody,offset,25);
                offset += 25;
            } else if (task instanceof TicketHandCancelTask) {
                TicketHandCancelTask handCancelTask = (TicketHandCancelTask) task;
                //动作码 1
                byte[] actionCode = handCancelTask.getActionCode().getBytes();
                System.arraycopy(actionCode,0,byteBody,offset,1);
                offset += 1;
                //任务标识 8
                byte[] taskNo = ByteConvertUtil.addZeroForNum(handCancelTask.getTaskNo(),8).getBytes();
                System.arraycopy(taskNo,0,byteBody,offset,8);
                offset += 8;
                //任务变更标志 1
                byte[] changeSine = handCancelTask.getChangeSign().getBytes();
                System.arraycopy(changeSine,0,byteBody,offset,1);
                offset ++;
                //车票类型 3
                byte[] ticketType = ByteConvertUtil.addZeroForNum(handCancelTask.getTicketType(),3).getBytes();
                System.arraycopy(ticketType,0,byteBody,offset,3);
                offset += 3;
                //任务数量 10
                byte[] handNum = ByteConvertUtil.addZeroForNum(handCancelTask.getTaskNum(),10).getBytes();
                System.arraycopy(handNum,0,byteBody,offset,10);
                offset += 10;
                //批次编号 10
                byte[] batchNo = ByteConvertUtil.addZeroForNum(handCancelTask.getBatchNo(),10).getBytes();
                System.arraycopy(batchNo,0,byteBody,offset,10);
                offset += 10;
                //保留 67
                byte[] remain = ByteConvertUtil.addZeroForNum("", 67).getBytes();
                System.arraycopy(remain,0,byteBody,offset,67);
                offset += 67;
            } else if (task instanceof TicketCancelTask) {
                TicketCancelTask cancelTask = (TicketCancelTask) task;
                //动作码 1
                byte[] actionCode = cancelTask.getActionCode().getBytes();
                System.arraycopy(actionCode,0,byteBody,offset,1);
                offset ++;
                //任务标识8
                byte[] taskNo = ByteConvertUtil.addZeroForNum(cancelTask.getTaskNo(), 8).getBytes();
                System.arraycopy(taskNo,0,byteBody,offset,8);
                offset += 8;
                //任务变更标识 1
                byte[] changeSign = cancelTask.getChangeSign().getBytes();
                System.arraycopy(changeSign,0,byteBody,offset,1);
                offset ++;
                //车票类型3
                byte[] ticketType = ByteConvertUtil.addZeroForNum(cancelTask.getTicketType(), 3).getBytes();
                System.arraycopy(ticketType,0,byteBody,offset,3);
                offset += 3;
                //版本号 6
                byte[] version = ByteConvertUtil.addZeroForNum(cancelTask.getVersion(), 6).getBytes();
                System.arraycopy(version,0,byteBody,offset,6);
                offset += 6;
                //任务数量10
                byte[] cancelNum = ByteConvertUtil.addZeroForNum(cancelTask.getTaskNum(), 10).getBytes();
                System.arraycopy(cancelNum,0,byteBody,offset,10);
                offset += 10;
                //使用次数10
                byte[] useTime = ByteConvertUtil.addZeroForNum(cancelTask.getUseTimes(), 10).getBytes();
                System.arraycopy(useTime,0,byteBody,offset,10);
                offset += 10;
                //最小序列号 10
                byte[] beginNo = ByteConvertUtil.addZeroForNum(cancelTask.getBeginNo(), 10).getBytes();
                System.arraycopy(beginNo,0,byteBody,offset,10);
                offset += 10;
                //最大序列号 10
                byte[] endNO = ByteConvertUtil.addZeroForNum(cancelTask.getEndNo(), 10).getBytes();
                System.arraycopy(endNO,0,byteBody,offset,10);
                offset += 10;
                //保留41
                byte[] remain = ByteConvertUtil.addZeroForNum("", 41).getBytes();
                System.arraycopy(remain,0,byteBody,offset,41);
                offset += 41;

            } else if (task instanceof  TicketSortTask) {
                TicketSortTask sortTask = (TicketSortTask) task;

                // 动作码 1
                byte[] actionCode = sortTask.getActionCode().getBytes();
                System.arraycopy(actionCode,0,byteBody,offset,1);
                offset ++;
                //任务标识 8
                byte[] taskNo = ByteConvertUtil.addZeroForNum(sortTask.getTaskNo(), 8).getBytes();
                System.arraycopy(taskNo,0,byteBody,offset,8);
                offset += 8;
                //任务变更标识 1
                byte[] changeSign = sortTask.getChangeSign().getBytes();
                System.arraycopy(changeSign,0,byteBody,offset,1);
                offset ++;
                //车票类型 3
                byte[] ticketType = ByteConvertUtil.addZeroForNum(sortTask.getTicketType(), 3).getBytes();
                System.arraycopy(ticketType,0,byteBody,offset,3);
                offset += 3;
                //车票发行批次 10
                byte[] batchNo = ByteConvertUtil.addZeroForNum(sortTask.getBatchNo(), 10).getBytes();
                System.arraycopy(batchNo,0,byteBody,offset,10);
                offset += 10;
                //车票初始化日期 8
                byte[] initDate = ByteConvertUtil.addZeroForNum(sortTask.getInitDate(), 8).getBytes();
                System.arraycopy(initDate,0,byteBody,offset,8);
                offset += 8;
                //版本号 6
                byte[] version = ByteConvertUtil.addZeroForNum(sortTask.getVersion(), 6).getBytes();
                System.arraycopy(version,0,byteBody,offset,6);
                offset += 6;
                //使用次数 10
                byte[] useTime = ByteConvertUtil.addZeroForNum(sortTask.getUseTimes(), 10).getBytes();
                System.arraycopy(useTime,0,byteBody,offset,10);
                offset += 10;
                //最小序列号 10
                byte[] beginNo = ByteConvertUtil.addZeroForNum(sortTask.getBeginNo(), 10).getBytes();
                System.arraycopy(beginNo,0,byteBody,offset,10);
                offset += 10;

                //最大序列号 10
                byte[] endNo = ByteConvertUtil.addZeroForNum(sortTask.getEndNo(), 10).getBytes();
                System.arraycopy(endNo,0,byteBody,offset,10);
                offset += 10;
                //票箱1金额 9
                byte[] box1 = ByteConvertUtil.addZeroForNum(sortTask.getBox1Face(), 9).getBytes();
                System.arraycopy(box1,0,byteBody,offset,9);
                offset += 9;
                //票箱2金额 9
                byte[] box2 = ByteConvertUtil.addZeroForNum(sortTask.getBox2Face(), 9).getBytes();
                System.arraycopy(box2,0,byteBody,offset,9);
                offset += 9;
                //票箱3金额 9
                byte[] box3 = ByteConvertUtil.addZeroForNum(sortTask.getBox3Face(), 9).getBytes();
                System.arraycopy(box3,0,byteBody,offset,9);
                offset += 9;
                //保留 6
                byte[] remain = ByteConvertUtil.addZeroForNum("", 6).getBytes();
                System.arraycopy(remain,0,byteBody,offset,6);
                offset += 6;
            } else if (task instanceof TicketRecodeTask) {
                TicketRecodeTask recodeTask = (TicketRecodeTask) task;

                //动作码 1
                byte[] actionCode = recodeTask.getActionCode().getBytes();
                System.arraycopy(actionCode,0,byteBody,offset,1);
                offset ++;
                //任务标识 8
                byte[] taskNo = ByteConvertUtil.addZeroForNum(recodeTask.getTaskNo(), 8).getBytes();
                System.arraycopy(taskNo,0,byteBody,offset,8);
                offset += 8;
                //任务变更标志 1
                byte[] changeSign = recodeTask.getChangeSign().getBytes();
                System.arraycopy(changeSign,0,byteBody,offset,1);
                offset ++;
                //车票类型 3
                byte[] ticketType = ByteConvertUtil.addZeroForNum(recodeTask.getTicketType(), 3).getBytes();
                System.arraycopy(ticketType,0,byteBody,offset,3);
                offset += 3;
                //车票主类型 2
                byte[] ticketMainType = ByteConvertUtil.addZeroForNum(recodeTask.getTicketMainType(), 2).getBytes();
                System.arraycopy(ticketMainType,0,byteBody,offset,2);
                offset += 2;
                //版本号 6
                byte[] version = ByteConvertUtil.addZeroForNum(recodeTask.getVersion(), 6).getBytes();
                System.arraycopy(version,0,byteBody,offset,6);
                offset += 6;
                //批次标识10
                byte[] batchNo = ByteConvertUtil.addZeroForNum(recodeTask.getBatchNo(), 10).getBytes();
                System.arraycopy(batchNo,0,byteBody,offset,10);
                offset += 10;
                //最小序列号 10
                byte[] beginNo = ByteConvertUtil.addZeroForNum(recodeTask.getBeginNo(), 10).getBytes();
                System.arraycopy(beginNo,0,byteBody,offset,10);
                offset += 10;
                //最大序列号 10
                byte[] endNo = ByteConvertUtil.addZeroForNum(recodeTask.getEndNo(), 10).getBytes();
                System.arraycopy(endNo,0,byteBody,offset,10);
                offset += 10;
                //测试票标志 1
                byte[] test = recodeTask.getIsTest().getBytes();
                System.arraycopy(test,0,byteBody,offset,1);
                offset ++;
                //记名票标志 1
                byte[] name = recodeTask.getIsNamed().getBytes();
                System.arraycopy(name,0,byteBody,offset,1);
                offset ++;
                //纪念票标志 1
                byte[] memory = recodeTask.getIsMemorial().getBytes();
                System.arraycopy(memory,0,byteBody,offset,1);
                offset ++;
                //钱包单位 2
                byte[] wallet = ByteConvertUtil.addZeroForNum(recodeTask.getWalletUnit(), 2).getBytes();
                System.arraycopy(wallet,0,byteBody,offset,2);
                offset += 2;
                //初始化票值 9
                byte[] initAmt = ByteConvertUtil.addZeroForNum(recodeTask.getInitAmt(), 9).getBytes();
                System.arraycopy(initAmt,0,byteBody,offset,9);
                offset += 9;
                //初始化奖励值 5
                byte[] reward = ByteConvertUtil.addZeroForNum(recodeTask.getRewordAmt(), 5).getBytes();
                System.arraycopy(reward,0,byteBody,offset,5);
                offset += 5;

                //压金 5
                byte[] depAmt = ByteConvertUtil.addZeroForNum(recodeTask.getDepAmt(), 5).getBytes();
                System.arraycopy(depAmt,0,byteBody,offset,5);
                offset += 5;

                //有效期 6
                byte[] validDays = ByteConvertUtil.addZeroForNum(recodeTask.getInvalidDays(), 6).getBytes();
                System.arraycopy(validDays,0,byteBody,offset,6);
                offset += 6;
                //有效期开始 8
                byte[] beginDate = ByteConvertUtil.addZeroForNum(recodeTask.getBeginDate(), 8).getBytes();
                System.arraycopy(beginDate,0,byteBody,offset,8);
                offset += 8;

                //票卡状态
                System.arraycopy(recodeTask.getTicketStatus().getBytes(),0,byteBody,offset,1);
                offset += 1;

                //保留 10
                byte[] remain = ByteConvertUtil.addZeroForNum("", 10).getBytes();
                System.arraycopy(remain,0,byteBody,offset,10);
                offset += 10;
            } else if (task instanceof TicketCustomTask) {
                TicketCustomTask customTask = (TicketCustomTask) task;
                // 动作码 1
                byte[] actionCode = customTask.getActionCode().getBytes();
                System.arraycopy(actionCode,0,byteBody,offset,1);
                offset ++;
                //任务标识8
                byte[] taskNo = ByteConvertUtil.addZeroForNum(customTask.getTaskNo(), 8).getBytes();
                System.arraycopy(taskNo,0,byteBody,offset,8);
                offset += 8;
                //任务变更标识 1
                byte[] changeSign = customTask.getChangeSign().getBytes();
                System.arraycopy(changeSign,0,byteBody,offset,1);
                offset ++;
                //车票类型 3
                byte[] ticketType = ByteConvertUtil.addZeroForNum(customTask.getTicketType(), 3).getBytes();
                System.arraycopy(ticketType,0,byteBody,offset,3);
                offset += 3;
                //版本号 6
                byte[] version = ByteConvertUtil.addZeroForNum(customTask.getVersion(), 6).getBytes();
                System.arraycopy(version,0,byteBody,offset,6);
                offset += 6;
                //批次标识 10
                byte[] batchNo = ByteConvertUtil.addZeroForNum(customTask.getBatchNo(), 10).getBytes();
                System.arraycopy(batchNo,0,byteBody,offset,10);
                offset += 10;
                //任务数量
                byte[] customNum = ByteConvertUtil.addZeroForNum(customTask.getTaskNum(), 10).getBytes();
                System.arraycopy(customNum,0,byteBody,offset,10);
                offset += 10;
                //个性化任务文件名
                byte[] fileName = ByteConvertUtil.addZeroForNum(customTask.getCustomFileName(), 20).getBytes();
                System.arraycopy(fileName,0,byteBody,offset,20);
                offset += 20;
                //保留 41
                byte[] remain = ByteConvertUtil.addZeroForNum("", 41).getBytes();
                System.arraycopy(remain,0,byteBody,offset,41);
                offset += 41;
            } else {
                log.error("没有对应的任务。。。。请确认。。。。");
                throw new IllegalArgumentException("No corresponding task");
            }
        }
        return byteBody;
    }

}

