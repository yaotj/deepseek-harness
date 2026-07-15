package com.chinasofti.huateng.acc.es.server.netty.service;

import com.chinasofti.huateng.acc.es.server.enumns.TaskClassification;
import com.chinasofti.huateng.acc.es.server.mapper.TblTktEsTaskMapper;
import com.chinasofti.huateng.acc.es.server.model.TblTktEsAccount;
import com.chinasofti.huateng.acc.es.server.model.TblTktEsAssign;
import com.chinasofti.huateng.acc.es.server.model.TblTktEsTask;
import com.chinasofti.huateng.acc.es.server.netty.model.Constant;
import com.chinasofti.huateng.acc.es.server.netty.model.MessageBean;
import com.chinasofti.huateng.acc.es.server.netty.model.Messagehead;
import com.chinasofti.huateng.acc.es.server.service.IEsAccountService;
import com.chinasofti.huateng.acc.es.server.service.IEsAssignService;
import com.chinasofti.huateng.acc.es.server.service.IEsInfoService;
import com.chinasofti.huateng.acc.es.server.service.IEsTaskService;
import com.chinasofti.huateng.acc.es.server.util.ByteConvertUtil;
import com.chinasofti.huateng.acc.es.server.netty.data.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @program: cloud-acc-server
 * @description: 业务处理
 * @author: fc
 * @create: 2020-09-18 12:09
 */

@Slf4j
@Component
public class BusinessHandler {

    @Autowired
    private IEsInfoService esInfoService;

    @Autowired
    private IEsTaskService esTaskService;

    @Autowired
    private IEsAccountService esAccountService;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;
    
    @Autowired
    private IEsAssignService esAssignService;


    /**
     * 设备签到
     * @param messageBean
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public Messagehead deviceSignIn(MessageBean messageBean){
        DeviceSignIn deviceSignIn=DeviceSignIn.toDeviceSignInStr(messageBean);
        //设备签到
        boolean signIn=esInfoService.esSignIn(deviceSignIn);
        TblTktEsAccount account = new TblTktEsAccount();
        account.setUsername(deviceSignIn.getOperatorCode().substring(0,5));
        account.setPassword(deviceSignIn.getOperatorPassword());
        Integer esUserType = esAccountService.getEsUserType(account);
        //账号密码错误
        if(Objects.isNull(esUserType)) {
            Messagehead errorResult = new Messagehead();
            errorResult.setDataLength("0026");
            errorResult.setTxnType(messageBean.getTxnType());
            errorResult.setNodeId(messageBean.getNodeId());
            errorResult.setSequence(messageBean.getSequence());
            errorResult.setMd5(messageBean.getMd5());
            errorResult.setRequestType(Constant.DataPackage.RESULT_TYPE);
            errorResult.setIsFileTransaction(messageBean.getIsFileTransaction());
            errorResult.setMack(Constant.MackStatus.OPERATOR_ERROR);
            return errorResult;
        }
        //设置应答消息
        MessageBean result= messageBean;
        result.setDataLength("0034");
        result.setRequestType(Constant.DataPackage.RESULT_TYPE);
        //发送节点标识
        result.setNodeId(deviceSignIn.getEsNodeId());
        if (signIn) {
            log.info("分拣机{}签到成功", deviceSignIn.getEsNodeId());
            result.setMack(Constant.MackStatus.NORMAL);
        } else {
            result.setMack(Constant.MackStatus.ES_NODE_ERROR);
        }
        DeviceSignInMack deviceSignInMack= DeviceSignInMack.builder().deviceStateInterval("600").operatorRank(String.valueOf(esUserType)).build();
        result.setDataBody(DeviceSignInMack.toBytesArray(deviceSignInMack));
        return result;
    }

    public Messagehead deviceStat(MessageBean messageBean) {
        DeviceStatReport stat = DeviceStatReport.message2DeviceStatReport(messageBean);
        esInfoService.updateEsStat(stat);
        Messagehead messagehead = new Messagehead();
        messagehead.setDataLength("0026");
        messagehead.setIsFileTransaction(messageBean.getIsFileTransaction());
        messagehead.setRequestType(Constant.DataPackage.RESULT_TYPE);
        messagehead.setMd5(messageBean.getMd5());
        messagehead.setNodeId(messageBean.getNodeId());
        messagehead.setSequence(messageBean.getSequence());
        messagehead.setTxnType(messageBean.getTxnType());
        messagehead.setMack(Constant.MackStatus.NORMAL);
        return messagehead;
    }

    /**
     * 设备工作任务报告
     * @param messageBean
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public Messagehead taskStatReport(MessageBean messageBean) {
        byte[] dataBody = messageBean.getDataBody();
        byte[] taskNoByte = new byte[8];
        System.arraycopy(dataBody,0,taskNoByte,0,8);
        String taskNo = new String(taskNoByte);
        String actionType = esTaskService.getTaskType(Integer.parseInt(taskNo));
        EsTaskReport report = EsTaskReport.message2TaskReport(messageBean,actionType);
        /**
         * 打印机执行任务后，给出报告，根据报告去修改相应的信息
         */
        log.info("进入report方法之前");
        boolean flag = esTaskService.report(report);
        Messagehead messagehead = new Messagehead();
        messagehead.setDataLength("0026");
        messagehead.setIsFileTransaction((byte)'0');
        messagehead.setRequestType(Constant.DataPackage.RESULT_TYPE);
        messagehead.setMd5(messageBean.getMd5());
        messagehead.setNodeId(messageBean.getNodeId());
        messagehead.setSequence(messageBean.getSequence());
        messagehead.setTxnType(messageBean.getTxnType());
        if (flag) {
            messagehead.setMack(Constant.MackStatus.NORMAL);
        } else {
            messagehead.setMack(Constant.MackStatus.FILE_NOT_EXIT);
        }
        return messagehead;
    }

    /**
     * 设备签退
     * @param messageBean
     * @return
     */
    public Messagehead deviceSignOut(MessageBean messageBean) {
        DeviceSignOut deviceSignOut = DeviceSignOut.message2DeviceSignOut(messageBean);
        boolean signOut = esInfoService.esSignOut(deviceSignOut);
        //设置应答消息
        Messagehead result = new Messagehead();
        result.setDataLength("0026");
        result.setIsFileTransaction(messageBean.getIsFileTransaction());
        result.setMd5(messageBean.getMd5());
        result.setNodeId(messageBean.getNodeId());
        result.setSequence(messageBean.getSequence());
        result.setRequestType(Constant.DataPackage.RESULT_TYPE);
        result.setTxnType(messageBean.getTxnType());
        if (signOut) {
            log.info("编码分拣机{}签退",deviceSignOut.getNodeId());
            result.setMack(Constant.MackStatus.NORMAL);
        } else {
            log.error("编码分拣机{}签退失败，请核实！",messageBean.getNodeId());
            result.setMack(Constant.MackStatus.ES_NODE_ERROR);
        }
        return result;
    }

    public MessageBean taskApply(MessageBean messageBean) {
        TaskApply apply = TaskApply.message2TaskApply(messageBean);
        MessageBean result = messageBean;
        //获取任务，最多50个
        List<TblTktEsTask> tasks = esTaskService.tasksByNodeIdAndDate(apply.getEsNodeId(),apply.getDate());
        int dataLength = 30 + tasks.size() * 100;
        if(dataLength > 1000 ) {
            result.setDataLength("" + dataLength);
        } else if (dataLength > 100) {
            result.setDataLength("0" + dataLength);
        } else {
            result.setDataLength("0030");
        }
        result.setRequestType(Constant.DataPackage.RESULT_TYPE);
        //发送节点标识
        result.setNodeId(messageBean.getNodeId());
        result.setMack(Constant.MackStatus.NORMAL);
        if (tasks.size() == 0) {
            TaskApplyMack applyMack = TaskApplyMack.builder().taskStat("0").taskNum("0").hasNext("0").build();
            result.setDataBody(TaskApplyMack.toBytesArray(applyMack));
        } else {
            List<WorkTask> workTasks = new ArrayList<>();
            SqlSession sqlSession = null;
            try {
                sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
                TblTktEsTaskMapper mapper = sqlSession.getMapper(TblTktEsTaskMapper.class);
                tasks.forEach(mapper::updateToExecuting);
                sqlSession.commit();
            }catch (Exception e) {
                log.info("出现异常:{}",e.getMessage());
                e.printStackTrace();
            } finally {
                if (!Objects.isNull(sqlSession)) {
                    sqlSession.close();
                }
            }
            for (TblTktEsTask task : tasks) {
                String taskType = task.getTaskType();
                WorkTask workTask;
                switch (taskType) {
                    case TaskClassification.PUBLISH :
                        workTasks.add( TicketPublishTask.builder()
                                .actionCode(TaskClassification.PUBLISH)
                                .taskNo(String.valueOf(task.getTaskNo()))
                                .changeSign("1")
                                .ticketType(task.getTicketType() + "")
                                .ticketMainType(task.getTicketMainType() + "")
                                .version(task.getVerNo())
                                .batchNo(task.getBatchNo())
                                .taskNum(task.getTaskNum() + "")
                                .beginNo(task.getBeginNo() + "")
                                .endNo(task.getEndNo() + "")
                                .isTest(task.getTestFlg())
                                .isNamed(task.getIsNamedCard())
                                .isMemorial(task.getIsMemorial())
                                .walletUnit(task.getWalletUnit())
                                .initAmt(task.getInitAmt() + "")
                                .rewordAmt(task.getRewardAmt() + "")
                                .invalidDays(task.getValidDays() + "")
                                .depAmt(task.getDepAmt() + "")
                                .beginDate(task.getBeginDate())
                                .ticketStatus(task.getTicketStatus())
                                .build());
                        break;
                    case TaskClassification.PRE_ASSIGN:
                        workTasks.add(TicketPreassignTask.builder()
                                .actionCode(TaskClassification.PRE_ASSIGN)
                                .taskNo(String.valueOf(task.getTaskNo()))
                                .changeSign("1")
                                .ticketType(task.getTicketType() + "")
                                .assignDate(task.getPlanDate())
                                .assignNum(task.getTaskNum() + "")
                                .batchNo(task.getBatchNo())
                                .initAmt(task.getInitAmt() + "")
                                .rewordAmt(task.getRewardAmt() + "")
                                .depAmt(task.getDepAmt() + "")
                                .invalidDays(task.getValidDays() + "")
                                .beginDate(task.getBeginDate())
                                .ticketStatus(task.getTicketStatus())
                                .build());
                        break;
                    case TaskClassification.HAND_CANCEL:
                        workTasks.add( TicketHandCancelTask.builder()
                                .actionCode(TaskClassification.HAND_CANCEL)
                                .taskNo(String.valueOf(task.getTaskNo()))
                                .changeSign("1")
                                .ticketType(task.getTicketType() + "")
                                .taskNum(task.getTaskNum() + "")
                                .batchNo(task.getBatchNo())
                                .build());
                        break;
                    case TaskClassification.CANCEL:
                        workTasks.add(TicketCancelTask.builder()
                                .actionCode(TaskClassification.CANCEL)
                                .taskNo(String.valueOf(task.getTaskNo()))
                                .changeSign("1")
                                .ticketType(task.getTicketType() + "")
                                .version(task.getVerNo())
                                .taskNum(task.getTaskNum() + "")
                                .useTimes(task.getTickUseTimes() + "")
                                .beginNo(task.getBeginNo() + "")
                                .endNo(task.getEndNo() + "")
                                .build());
                        break;
                    case TaskClassification.SORT:
                        //不做
                        workTasks.add(TicketSortTask.builder()
                                .actionCode(TaskClassification.SORT)
                                .taskNo(String.valueOf(task.getTaskNo()))
                                .changeSign("1")
                                .ticketType(task.getTicketType() + "")
                                .batchNo(task.getBatchNo())
                                .initDate(task.getPlanDate())
                                .version(task.getVerNo())
                                .useTimes(task.getTickUseTimes() + "")
                                .beginNo(task.getBeginNo() + "")
                                .endNo(task.getEndNo() + "")
                                .box1Face("")
                                .box2Face("")
                                .box3Face("")
                                .build());
                        break;
                    case TaskClassification.RECODE:
                        workTasks.add(TicketRecodeTask.builder()
                                .actionCode(TaskClassification.RECODE)
                                .taskNo(String.valueOf(task.getTaskNo()))
                                .changeSign("1")
                                .ticketType(task.getTicketType() + "")
                                .ticketMainType(task.getTicketMainType() + "")
                                .version(task.getVerNo())
                                .batchNo(task.getBatchNo())
                                .beginNo(task.getBeginNo() + "")
                                .endNo(task.getEndNo() + "")
                                .isTest(task.getTestFlg())
                                .isNamed(task.getIsNamedCard())
                                .isMemorial(task.getIsMemorial())
                                .walletUnit(task.getWalletUnit())
                                .initAmt(task.getInitAmt() + "")
                                .rewordAmt(task.getRewardAmt() + "")
                                .depAmt(task.getDepAmt() + "")
                                .invalidDays(task.getValidDays() + "")
                                .beginDate(task.getBeginDate())
                                .build());
                        break;
                    case TaskClassification.CUSTOM:
                        TblTktEsAssign assign = esAssignService.getAssignByTaskNo(task.getTaskNo());
                        workTasks.add(TicketCustomTask.builder()
                                .actionCode(TaskClassification.CUSTOM)
                                .taskNo(String.valueOf(task.getTaskNo()))
                                .changeSign("1")
                                .ticketType(task.getTicketType() + "")
                                .version(task.getVerNo())
                                .batchNo(task.getBatchNo())
                                .taskNum(task.getTaskNum() + "")
                                .customFileName("9060" + ByteConvertUtil.addZeroForNum(assign.getEsCode(),8) + ByteConvertUtil.addZeroForNum(String.valueOf(task.getTaskNo()),8))
                                .build());
                        break;
                    default:
                        log.info("该任务的动作标识有误");
                        throw new IllegalStateException("Unexpected value: " + taskType);
                }
            }
            Integer taskNum = Math.min(tasks.size(), 50);
            TaskApplyMack mack = TaskApplyMack.builder().taskStat("1").taskNum(taskNum + "").hasNext(tasks.size() == 50 ? "1" : "0").tasks(workTasks).build();
            log.info("返回的任务有:{}",tasks.stream().map(TblTktEsTask::getTaskNo).collect(Collectors.toList()) );
            result.setDataBody(TaskApplyMack.toBytesArray(mack));
            return result;
        }
        return result;
    }



}
