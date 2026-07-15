package com.chinasofti.huateng.acc.es.server.netty.data;

import com.chinasofti.huateng.acc.es.server.enumns.TaskClassification;
import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import com.chinasofti.huateng.acc.es.server.netty.model.Messagehead;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@Slf4j
public class EsTaskReport extends Messagehead {

    //任务标识
    private String taskNo;
    //任务执行结果
    private String resultStat;


    public static EsTaskReport message2TaskReport(MessageBean messageBean, final String taskType) {
        byte[] dataBody = messageBean.getDataBody();
        log.info("7004收到的数据{}" + new String(dataBody));
        EsTaskReport taskReport;
        switch (taskType) {
            case TaskClassification.PUBLISH : {
                taskReport = new PublishTaskReport();
                headMessage(taskReport, messageBean);
                PublishTaskReport result = (PublishTaskReport) taskReport;

                //143
                //9
                //任务标识号 8
                byte[] taskNo = new byte[8];
                System.arraycopy(dataBody, 0, taskNo, 0, 8);
                result.setTaskNo(new String(taskNo));
                //任务执行结果 1
                byte[] taskResult = new byte[1];
                System.arraycopy(dataBody, 8, taskResult, 0, 1);
                result.setResultStat(new String(taskResult));
                //134
                //编码分拣机 8
                byte[] nodeId = new byte[8];
                System.arraycopy(dataBody, 9, nodeId, 0, 8);
                result.setEsNodeId(new String(nodeId));
                //操作员编号 10
                byte[] operator = new byte[10];
                System.arraycopy(dataBody, 17, operator, 0, 10);
                result.setOperator(new String(operator));
                //车票类型 3
                byte[] ticketType = new byte[3];
                System.arraycopy(dataBody, 27, ticketType, 0, 3);
                result.setTicketType(new String(ticketType));
                //版本号 6
                byte[] version = new byte[6];
                System.arraycopy(dataBody, 30, version, 0, 6);
                result.setVersion(new String(version));
                //批次标识 10
                byte[] batchNo = new byte[10];
                System.arraycopy(dataBody, 36, batchNo, 0, 10);
                result.setBatchNo(new String(batchNo));
                //起始序列号 10
                byte[] beginNo = new byte[10];
                System.arraycopy(dataBody, 46, batchNo, 0, 10);
                log.info("传递的起始序列号是{}",new String(beginNo));
                String beginNoStr = StringUtils.isNumeric(new String(beginNo)) ? new String(beginNo) : "0";
                log.info("解析以后的起始序列号是{}",beginNoStr);
                result.setBeginNo(beginNoStr);
                //任务数量 10
                byte[] taskNum = new byte[10];
                System.arraycopy(dataBody, 56, taskNum, 0, 10);
                result.setTaskNum(new String(taskNum));
                //完成数量 10
                byte[] completedNum = new byte[10];
                System.arraycopy(dataBody, 66, completedNum, 0, 10);
                result.setCompletedNum(new String(completedNum));
                //废票数量 10
                byte[] wastedNum = new byte[10];
                System.arraycopy(dataBody, 76, wastedNum, 0, 10);
                result.setWastedNum(new String(wastedNum));
                //任务起始时间 14
                byte[] beginTime = new byte[14];
                System.arraycopy(dataBody, 86, beginTime, 0, 14);
                result.setBeginTime(new String(beginTime));
                //任务结束时间 14
                byte[] endTime = new byte[14];
                System.arraycopy(dataBody, 100, endTime, 0, 14);
                result.setEndTime(new String(endTime));
                //任务报告文件名 20
                byte[] fileName = new byte[20];
                System.arraycopy(dataBody, 114, fileName, 0, 20);
                result.setFileName(new String(fileName));
                //保留 9
                byte[] remain = new byte[9];
                System.arraycopy(dataBody, 134, remain, 0, 9);
                result.setRemain(new String(remain));
                return result;
            }
            case TaskClassification.PRE_ASSIGN: {
                taskReport = new PreAssignTaskReport();
                headMessage(taskReport, messageBean);
                PreAssignTaskReport pre = (PreAssignTaskReport) taskReport;
                //9
                //任务标识号 8
                byte[] taskNo = new byte[8];
                System.arraycopy(dataBody, 0, taskNo, 0, 8);
                pre.setTaskNo(new String(taskNo));
                //任务执行结果 1
                byte[] taskResult = new byte[1];
                System.arraycopy(dataBody, 8, taskResult, 0, 1);
                pre.setResultStat(new String(taskResult));
                //134
                //编码分拣机 8
                byte[] nodeId = new byte[8];
                System.arraycopy(dataBody, 9, nodeId, 0, 8);
                pre.setEsNodeId(new String(nodeId));
                //操作员编号 10
                byte[] operator = new byte[10];
                System.arraycopy(dataBody, 17, operator, 0, 10);
                pre.setOperator(new String(operator));
                //车票类型 3
                byte[] ticketType = new byte[3];
                System.arraycopy(dataBody, 27, ticketType, 0, 3);
                pre.setTicketType(new String(ticketType));
                //任务数量 10
                byte[] taskNum = new byte[10];
                System.arraycopy(dataBody, 30, taskNum, 0, 10);
                pre.setTaskNum(new String(taskNum));
                //完成数量 10
                byte[] completedNum = new byte[10];
                System.arraycopy(dataBody, 40, completedNum, 0, 10);
                pre.setCompletedNum(new String(completedNum));
                //失败数量 10
                byte[] errorNum = new byte[10];
                System.arraycopy(dataBody, 50, errorNum, 0, 10);
                pre.setErrorNum(new String(errorNum));
                //任务起始时间 14
                byte[] beginTime = new byte[14];
                System.arraycopy(dataBody, 60, beginTime, 0, 14);
                pre.setBeginTime(new String(beginTime));
                //任务结束时间 14
                byte[] endTime = new byte[14];
                System.arraycopy(dataBody, 74, endTime, 0, 14);
                pre.setEndTime(new String(endTime));
                //任务报告文件名 20
                byte[] fileName = new byte[20];
                System.arraycopy(dataBody, 88, fileName, 0, 20);
                pre.setFileName(new String(fileName));
                //保留 9
                byte[] remain = new byte[35];
                System.arraycopy(dataBody, 108, remain, 0, 35);
                pre.setRemain(new String(remain));
                return pre;
            }
            case TaskClassification.HAND_CANCEL:{
                log.info("进去缴销任务报告");
                log.info("任务的返回是{}",new String(dataBody));
                taskReport = new HandCancelTaskReport();
                headMessage(taskReport,messageBean);
                HandCancelTaskReport result = (HandCancelTaskReport) taskReport;
                //9
                //任务标识号 8
                byte[] taskNo = new byte[8];
                System.arraycopy(dataBody, 0, taskNo, 0, 8);
                result.setTaskNo(new String(taskNo));
                //任务执行结果 1
                byte[] taskResult = new byte[1];
                System.arraycopy(dataBody, 8, taskResult, 0, 1);
                result.setResultStat(new String(taskResult));
                //134
                //编码分拣机 8
                byte[] nodeId = new byte[8];
                System.arraycopy(dataBody, 9, nodeId, 0, 8);
                result.setEsNodeId(new String(nodeId));
                //操作员编号 10
                byte[] operator = new byte[10];
                System.arraycopy(dataBody, 17, operator, 0, 10);
                result.setOperator(new String(operator));
                //车票类型 3
                byte[] ticketType = new byte[3];
                System.arraycopy(dataBody, 27, ticketType, 0, 3);
                result.setTicketType(new String(ticketType));
                //任务数量 10
                byte[] taskNum = new byte[10];
                System.arraycopy(dataBody, 30, taskNum, 0, 10);
                String taskNumStr = new String(taskNum);
                log.info("任务完成数量是{}",taskNumStr);
                result.setTaskNum(taskNumStr);
                //完成数量 10
                byte[] completedNum = new byte[10];
                System.arraycopy(dataBody, 40, completedNum, 0, 10);
                result.setCompletedNum(new String(completedNum));
                //失败数量 10
                byte[] errorNum = new byte[10];
                System.arraycopy(dataBody, 50, errorNum, 0, 10);
                result.setErrorNum(new String(errorNum));
                //任务起始时间 14
                byte[] beginTime = new byte[14];
                System.arraycopy(dataBody, 60, beginTime, 0, 14);
                result.setBeginTime(new String(beginTime));
                //任务结束时间 14
                byte[] endTime = new byte[14];
                System.arraycopy(dataBody, 74, endTime, 0, 14);
                result.setEndTime(new String(endTime));
                //任务报告文件名 20
                byte[] fileName = new byte[20];
                System.arraycopy(dataBody, 88, fileName, 0, 20);
                result.setFileName(new String(fileName));
                //保留 9
                byte[] remain = new byte[35];
                System.arraycopy(dataBody, 108, remain, 0, 35);
                result.setRemain(new String(remain));
                return result;
            }
            case TaskClassification.CANCEL:{
                taskReport = new CancelTaskReport();
                headMessage(taskReport,messageBean);
                CancelTaskReport result  = (CancelTaskReport) taskReport;
                //143
                //9
                //任务标识号 8
                byte[] taskNo = new byte[8];
                System.arraycopy(dataBody, 0, taskNo, 0, 8);
                result.setTaskNo(new String(taskNo));
                //任务执行结果 1
                byte[] taskResult = new byte[1];
                System.arraycopy(dataBody, 8, taskResult, 0, 1);
                result.setResultStat(new String(taskResult));
                //134
                //编码分拣机 8
                byte[] nodeId = new byte[8];
                System.arraycopy(dataBody, 9, nodeId, 0, 8);
                result.setEsNodeId(new String(nodeId));
                //操作员编号 10
                byte[] operator = new byte[10];
                System.arraycopy(dataBody, 17, operator, 0, 10);
                result.setOperator(new String(operator));
                //车票类型 3
                byte[] ticketType = new byte[3];
                System.arraycopy(dataBody, 27, ticketType, 0, 3);
                result.setTicketType(new String(ticketType));
                //版本号 6
                byte[] version = new byte[6];
                System.arraycopy(dataBody, 30, version, 0, 6);
                result.setVersion(new String(version));
                //任务数量 10
                byte[] taskNum = new byte[10];
                System.arraycopy(dataBody, 36, taskNum, 0, 10);
                String taskNumStr = new String(taskNum);
                result.setTaskNum(taskNumStr);
                //完成数量 10
                byte[] completedNum = new byte[10];
                System.arraycopy(dataBody, 46, completedNum, 0, 10);
                result.setCompletedNum(new String(completedNum));
                //废票数量 10
                byte[] cancelNum = new byte[10];
                System.arraycopy(dataBody, 56, cancelNum, 0, 10);
                result.setErrorNum(new String(cancelNum));
                //任务起始时间 14
                byte[] beginTime = new byte[14];
                System.arraycopy(dataBody, 66, beginTime, 0, 14);
                result.setBeginTime(new String(beginTime));
                //任务结束时间 14
                byte[] endTime = new byte[14];
                System.arraycopy(dataBody, 80, endTime, 0, 14);
                result.setEndTime(new String(endTime));
                //任务报告文件名 20
                byte[] fileName = new byte[20];
                System.arraycopy(dataBody, 94, fileName, 0, 20);
                result.setFileName(new String(fileName));
                //保留 9
                byte[] remain = new byte[29];
                System.arraycopy(dataBody, 114, remain, 0, 29);
                result.setRemain(new String(remain));
                return result;
            }
            case TaskClassification.SORT: {
                taskReport = new SortTaskReport();
                headMessage(taskReport,messageBean);
                SortTaskReport result = (SortTaskReport) taskReport;
                //9
                //任务标识号 8
                byte[] taskNo = new byte[8];
                System.arraycopy(dataBody, 0, taskNo, 0, 8);
                result.setTaskNo(new String(taskNo));
                //任务执行结果 1
                byte[] taskResult = new byte[1];
                System.arraycopy(dataBody, 8, taskResult, 0, 1);
                result.setResultStat(new String(taskResult));
                //134
                //编码分拣机 8
                byte[] nodeId = new byte[8];
                System.arraycopy(dataBody, 9, nodeId, 0, 8);
                result.setEsNodeId(new String(nodeId));
                //操作员编号 10
                byte[] operator = new byte[10];
                System.arraycopy(dataBody, 17, operator, 0, 10);
                result.setOperator(new String(operator));
                //车票类型 3
                byte[] ticketType = new byte[3];
                System.arraycopy(dataBody, 27, ticketType, 0, 3);
                result.setTicketType(new String(ticketType));
                //任务数量 10
                byte[] taskNum = new byte[10];
                System.arraycopy(dataBody, 30, taskNum, 0, 10);
                result.setTaskNum(new String(taskNum));
                //完成数量 10
                byte[] completedNum = new byte[10];
                System.arraycopy(dataBody, 40, completedNum, 0, 10);
                result.setCompletedNum(new String(completedNum));
                //失败数量 10
                byte[] sortNum = new byte[10];
                System.arraycopy(dataBody, 50, sortNum, 0, 10);
                result.setSortNum(new String(sortNum));
                //任务起始时间 14
                byte[] beginTime = new byte[14];
                System.arraycopy(dataBody, 60, beginTime, 0, 14);
                result.setBeginTime(new String(beginTime));
                //任务结束时间 14
                byte[] endTime = new byte[14];
                System.arraycopy(dataBody, 74, endTime, 0, 14);
                result.setEndTime(new String(endTime));
                //任务报告文件名 20
                byte[] fileName = new byte[20];
                System.arraycopy(dataBody, 88, fileName, 0, 20);
                result.setFileName(new String(fileName));
                //保留 9
                byte[] remain = new byte[35];
                System.arraycopy(dataBody, 108, remain, 0, 35);
                result.setRemain(new String(remain));
                return result;
            }
            case TaskClassification.RECODE: {
                taskReport = new RecodeTaskReport();
                headMessage(taskReport,messageBean);
                RecodeTaskReport result = (RecodeTaskReport) taskReport;
                //143
                //9
                //任务标识号 8
                byte[] taskNo = new byte[8];
                System.arraycopy(dataBody, 0, taskNo, 0, 8);
                result.setTaskNo(new String(taskNo));
                //任务执行结果 1
                byte[] taskResult = new byte[1];
                System.arraycopy(dataBody, 8, taskResult, 0, 1);
                result.setResultStat(new String(taskResult));
                //134
                //编码分拣机 8
                byte[] nodeId = new byte[8];
                System.arraycopy(dataBody, 9, nodeId, 0, 8);
                result.setEsNodeId(new String(nodeId));
                //操作员编号 10
                byte[] operator = new byte[10];
                System.arraycopy(dataBody, 17, operator, 0, 10);
                result.setOperator(new String(operator));
                //车票类型 3
                byte[] ticketType = new byte[3];
                System.arraycopy(dataBody, 27, ticketType, 0, 3);
                result.setTicketType(new String(ticketType));
                //版本号 6
                byte[] version = new byte[6];
                System.arraycopy(dataBody, 30, version, 0, 6);
                result.setVersion(new String(version));
                //批次标识 10
                byte[] batchNo = new byte[10];
                System.arraycopy(dataBody, 36, batchNo, 0, 10);
                result.setBatchNo(new String(batchNo));
                //起始序列号 10
                byte[] beginNo = new byte[10];
                System.arraycopy(dataBody, 46, batchNo, 0, 10);
                result.setBatchNo(new String(beginNo));
                //任务数量 10
                byte[] taskNum = new byte[10];
                System.arraycopy(dataBody, 56, taskNum, 0, 10);
                result.setTaskNum(new String(taskNum));
                //完成数量 10
                byte[] completedNum = new byte[10];
                System.arraycopy(dataBody, 66, completedNum, 0, 10);
                result.setCompleteNum(new String(completedNum));
                //废票数量 10
                byte[] wastedNum = new byte[10];
                System.arraycopy(dataBody, 76, wastedNum, 0, 10);
                result.setWastedNum(new String(wastedNum));
                //任务起始时间 14
                byte[] beginTime = new byte[14];
                System.arraycopy(dataBody, 86, beginTime, 0, 14);
                result.setBeginTime(new String(beginTime));
                //任务结束时间 14
                byte[] endTime = new byte[14];
                System.arraycopy(dataBody, 100, endTime, 0, 14);
                result.setEndTime(new String(endTime));
                //任务报告文件名 20
                byte[] fileName = new byte[20];
                System.arraycopy(dataBody, 114, fileName, 0, 20);
                result.setFileName(new String(fileName));
                //保留 9
                byte[] remain = new byte[9];
                System.arraycopy(dataBody, 134, remain, 0, 9);
                result.setRemain(new String(remain));
                return result;
            }

            case TaskClassification.CUSTOM: {
                taskReport = new CustomTaskReport();
                headMessage(taskReport,messageBean);
                CustomTaskReport result = (CustomTaskReport) taskReport;
                //143
                //9
                //任务标识号 8
                byte[] taskNo = new byte[8];
                System.arraycopy(dataBody, 0, taskNo, 0, 8);
                result.setTaskNo(new String(taskNo));
                //任务执行结果 1
                byte[] taskResult = new byte[1];
                System.arraycopy(dataBody, 8, taskResult, 0, 1);
                result.setResultStat(new String(taskResult));
                //134
                //编码分拣机 8
                byte[] nodeId = new byte[8];
                System.arraycopy(dataBody, 9, nodeId, 0, 8);
                result.setNodeId(new String(nodeId));
                //操作员编号 10
                byte[] operator = new byte[10];
                System.arraycopy(dataBody, 17, operator, 0, 10);
                result.setOperator(new String(operator));
                //车票类型 3
                byte[] ticketType = new byte[3];
                System.arraycopy(dataBody, 27, ticketType, 0, 3);
                result.setTicketType(new String(ticketType));
                //版本号 6
                byte[] version = new byte[6];
                System.arraycopy(dataBody, 30, version, 0, 6);
                result.setVersion(new String(version));
                //批次标识 10
                byte[] batchNo = new byte[10];
                System.arraycopy(dataBody, 36, batchNo, 0, 10);
                result.setBatchNo(new String(batchNo));
                //起始序列号 10
                byte[] beginNo = new byte[10];
                System.arraycopy(dataBody, 46, batchNo, 0, 10);
                result.setBeginNo(new String(beginNo));
                //任务数量 10
                byte[] taskNum = new byte[10];
                System.arraycopy(dataBody, 56, taskNum, 0, 10);
                result.setTaskNum(new String(taskNum));
                //完成数量 10
                byte[] completedNum = new byte[10];
                System.arraycopy(dataBody, 66, completedNum, 0, 10);
                result.setCompletedNum(new String(completedNum));
                //废票数量 10
                byte[] wastedNum = new byte[10];
                System.arraycopy(dataBody, 76, wastedNum, 0, 10);
                result.setWastedNum(new String(wastedNum));
                //任务起始时间 14
                byte[] beginTime = new byte[14];
                System.arraycopy(dataBody, 86, beginTime, 0, 14);
                result.setBeginTime(new String(beginTime));
                //任务结束时间 14
                byte[] endTime = new byte[14];
                System.arraycopy(dataBody, 100, endTime, 0, 14);
                result.setEndTime(new String(endTime));
                //任务报告文件名 20
                byte[] fileName = new byte[20];
                System.arraycopy(dataBody, 114, fileName, 0, 20);
                result.setFileName(new String(fileName));
                //保留 9
                byte[] remain = new byte[9];
                System.arraycopy(dataBody, 134, remain, 0, 9);
                result.setRemain(new String(remain));
                return result;
            }
            default:
                log.error("任务类型不对，类型taskType是{}",taskType);
                throw new IllegalArgumentException("任务类型有误，请检查数据");
        }
    }

    private static void headMessage(EsTaskReport taskReport,MessageBean messageBean) {
        // 报文头
        taskReport.setTxnType(messageBean.getTxnType());
        taskReport.setNodeId(messageBean.getNodeId());
        taskReport.setSequence(messageBean.getSequence());
        taskReport.setRequestType(messageBean.getRequestType());
        taskReport.setIsFileTransaction(messageBean.getIsFileTransaction());
        taskReport.setMack(messageBean.getMack());
    }
}

