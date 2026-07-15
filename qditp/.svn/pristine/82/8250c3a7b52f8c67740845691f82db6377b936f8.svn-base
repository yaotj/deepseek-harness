package com.chinasofti.huateng.acc.es.server.service.impl;

import cn.hutool.extra.ftp.Ftp;
import cn.hutool.extra.ftp.FtpMode;
import com.chinasofti.huateng.acc.es.server.config.FtpComponent;
import com.chinasofti.huateng.acc.es.server.enumns.*;
import com.chinasofti.huateng.acc.es.server.mapper.TblTktEsAssignMapper;
import com.chinasofti.huateng.acc.es.server.mapper.TblTktEsTaskMapper;
import com.chinasofti.huateng.acc.es.server.model.*;
import com.chinasofti.huateng.acc.es.server.netty.data.*;
import com.chinasofti.huateng.acc.es.server.service.*;
import com.chinasofti.huateng.acc.es.server.util.ByteConvertUtil;
import com.chinasofti.huateng.acc.es.server.util.DateUtil;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EsTaskServiceImpl implements IEsTaskService {

    private static final String CUSTOM_PER_FIX = "9060";

    @Autowired
    private TblTktEsTaskMapper esTaskMapper;

    @Autowired
    private ITaskPlanService taskPlanService;

    @Autowired
    private IEsProcService esProcService;

    @Autowired
    private IEsReportService esReportService;

    @Autowired
    private TblTktEsAssignMapper esAssignMapper;

    @Autowired
    private ITblTktPrePersonService personService;

    @Autowired
    private IFileService fileService;

    @Value("${spring.application.name}")
    private String serverName;

    @Value("${pageConfig.defaultSize:10}")
    private int defaultPageSize;

    @Autowired
    private FtpComponent ftpComponent;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVO<?> save(TblTktEsTask esTask) {
        esTask.setTaskAssnStat(TaskAssignStat.UN_ASSIGNED.code());
        esTask.setTaskExecStat(TaskExecuteStat.UN_EXECUTED.code());
        esTask.setGenTms(null);
        esTask.setLastUpdTms(null);
        esTask.setLastUpdId(serverName);
        esTask.setApprStat(TaskApplyStat.AGREED.getCode());
        // 校验拆分任务数量不能超过计划数量
        TblTktTaskPlan plan = taskPlanService.selectByPlanNo(esTask.getPlanNo());
        int allTaskNum = Objects.isNull(esTaskMapper.sumTaskByPlanNo(esTask.getPlanNo())) ? 0 : esTaskMapper.sumTaskByPlanNo(esTask.getPlanNo());
        TblTktTaskPlan taskPlan = new TblTktTaskPlan();
        taskPlan.setPlanNo(esTask.getPlanNo());
        if (esTask.getTaskNum() + allTaskNum > plan.getActNum()) {
            return ResultMapper.error("task total number is greater than plan number, save failed");
        } else if (esTask.getTaskNum() + allTaskNum < plan.getActNum()) {
            taskPlan.setTaskPlanStat(PlanStat.SPLITING.code());
        } else {
            taskPlan.setTaskPlanStat(PlanStat.SPLITED.code());
        }
        // 更新计划已分配数量和计划状态
        taskPlan.setAssignNum(esTask.getTaskNum() + allTaskNum);
        taskPlanService.updateByPlanNoSelective(taskPlan);
        // 保存拆分任务
        esTaskMapper.insert(esTask);
        return ResultMapper.ok();
    }

    @Override
    public ResultVO<?> selectByTaskNo(Integer taskNo) {
        return ResultMapper.ok(esTaskMapper.selectByPrimaryKey(taskNo));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVO<?> updateTask(TblTktEsTask esTask) {
        TblTktEsTask task = esTaskMapper.selectByPrimaryKey(esTask.getTaskNo());
        esTask.setTaskAssnStat(task.getTaskAssnStat());
        int sum = Objects.isNull(esTaskMapper.sumTaskByPlanNo(esTask.getPlanNo())) ? 0 : esTaskMapper.sumTaskByPlanNo(esTask.getPlanNo());
        TblTktTaskPlan plan = taskPlanService.selectByPlanNo(esTask.getPlanNo());
        TblTktTaskPlan taskPlan = new TblTktTaskPlan();
        if (esTask.getTaskNum() + sum - task.getTaskNum() > plan.getActNum()) {
            return ResultMapper.error("task number is greater than plan number, update failed");
        } else if (esTask.getTaskNum() + sum - task.getTaskNum() < plan.getActNum()) {
            taskPlan.setTaskPlanStat(PlanStat.SPLITING.code());
        } else {
            taskPlan.setTaskPlanStat(PlanStat.SPLITED.code());
        }
        taskPlan.setAssignNum(esTask.getTaskNum() + sum - task.getTaskNum());
        taskPlan.setPlanNo(esTask.getPlanNo());
        taskPlanService.updateByPlanNoSelective(taskPlan);
        esTaskMapper.updateByPrimaryKey(esTask);
        return ResultMapper.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVO<?> deleteById(Integer taskNo, String esCode) {
        TblTktEsTask esTask = esTaskMapper.selectByPrimaryKey(taskNo);
        TblTktTaskPlan taskPlan = new TblTktTaskPlan();
        taskPlan.setPlanNo(esTask.getPlanNo());
        taskPlan.setAssignNum(-esTask.getTaskNum());
        TblTktTaskPlan tblTktTaskPlan = taskPlanService.selectByPlanNo(esTask.getPlanNo());
        if (tblTktTaskPlan.getAssignNum().equals(esTask.getTaskNum())) {
            taskPlan.setTaskPlanStat(PlanStat.AGREE.code());
        } else {
            taskPlan.setTaskPlanStat(PlanStat.SPLITING.code());
        }

        if (TaskAssignStat.ASSIGNED.code().equals(esTask.getTaskAssnStat())) {
            // 删除任务对应的分配记录
            esAssignMapper.deleteByPrimaryKey(taskNo, esCode);
        }
        taskPlanService.assignTask(taskPlan);
        esTaskMapper.deleteByPrimaryKey(taskNo);
        return ResultMapper.ok();
    }

    @Override
    public ResultVO<?> selectTasksByPage(Integer pageNum, Integer pageSize, TblTktEsTask task) {
        TblTktEsTask query = task == null ? new TblTktEsTask() : task;
        PageInfo<Object> objectPageInfo = PageHelper.startPage(pageNum, pageSize).doSelectPageInfo(() -> {
            esTaskMapper.queryAllTasks(query);
        });
        return ResultMapper.ok(objectPageInfo);
    }

    @Override
    public int changeAssignStat(Integer taskNo, String assigned) {

        return esTaskMapper.updateTaskAssignStat(taskNo, assigned);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVO<?> save2(TblTktEsTask esTask) {
        esTask.setLastUpdId(serverName);
        esTask.setLastUpdTms(null);
        esTask.setGenTms(null);
        esTask.setTaskExecStat(TaskExecuteStat.UN_EXECUTED.code());
        esTask.setTaskAssnStat(TaskAssignStat.UN_ASSIGNED.code());
        esTask.setApprStat(TaskApplyStat.APPLYING.getCode());
        esTaskMapper.insert(esTask);
        return ResultMapper.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultVO<?> delete2(Integer taskNo, String esCode) {
        TblTktEsTask esTask = esTaskMapper.selectByPrimaryKey(taskNo);
        if (TaskAssignStat.ASSIGNED.code().equals(esTask.getTaskAssnStat())) {
            // 删除任务对应的分配记录
            esAssignMapper.deleteByPrimaryKey(taskNo, esCode);
        }
        esTaskMapper.deleteByPrimaryKey(taskNo);
        return ResultMapper.ok();
    }

    @Override
    public ResultVO<?> update2(TblTktEsTask task) {
        TblTktEsTask oldTask = esTaskMapper.selectByPrimaryKey(task.getTaskNo());
        task.setTaskAssnStat(oldTask.getTaskAssnStat());
        esTaskMapper.updateByPrimaryKey(task);
        return ResultMapper.ok();
    }

    @Override
    public ResultVO<?> page2(Integer pageNum, Integer pageSize, TblTktEsTask task) {
        TblTktEsTask query = task == null ? new TblTktEsTask() : task;
        PageInfo<TblTktEsTask> info = PageHelper.startPage(pageNum, pageSize).doSelectPageInfo(() -> {
            esTaskMapper.select2(query);
        });
        return ResultMapper.ok(info);
    }

    @Override
    public List<TblTktEsTask> tasksByNodeIdAndDate(String esNodeId, String date) {
        List<TblTktEsTask> tblTktEsTasks = esTaskMapper.tasksByDateAndEsNodeId(date, esNodeId);
        return tblTktEsTasks.size() > 50 ? tblTktEsTasks.stream().limit(50).sorted(Comparator.comparing(TblTktEsTask::getTaskNo)).collect(Collectors.toList()) : tblTktEsTasks;
    }

    @Override
    public String getTaskType(Integer taskNo) {
        return esTaskMapper.getTaskTypeByTaskNo(taskNo);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean report(EsTaskReport report) {
        log.info("接收到任务回执 report");
        TblTktEsTask task = esTaskMapper.selectByPrimaryKey(Integer.parseInt(report.getTaskNo()));
        if (report instanceof PublishTaskReport) {
            log.info("处理制票任务回执");
            PublishTaskReport publishTaskReport = (PublishTaskReport) report;
            // 更新任务执行结果
            int i = esTaskMapper.reportPublishTaskReport(publishTaskReport);
            // 保存任务报告和文件处理记录
            TblTktEsReport tblTktEsReport = new TblTktEsReport(Integer.valueOf(publishTaskReport.getTaskNo()), task.getTaskType(), publishTaskReport.getEsNodeId(), "", Integer.valueOf(publishTaskReport.getTaskNum()), task.getBeginNo(), task.getEndNo(), Integer.valueOf(publishTaskReport.getCompletedNum()), Integer.valueOf(publishTaskReport.getWastedNum()), publishTaskReport.getBeginTime(), publishTaskReport.getEndTime(), publishTaskReport.getOperator(), null);
            esReportService.saveReports(tblTktEsReport);
            TblTktEsProc esProc = new TblTktEsProc(Integer.valueOf(publishTaskReport.getTaskNo()), publishTaskReport.getEsNodeId(), 9050, DateUtil.dateString8(), publishTaskReport.getFileName(), Integer.valueOf(publishTaskReport.getTaskNum()), Integer.valueOf(publishTaskReport.getCompletedNum()), "0", publishTaskReport.getOperator(), null);
            esProcService.save(esProc);
            log.info("start analyzing report file");
            esReportService.analysisFile(publishTaskReport.getFileName(), publishTaskReport.getTaskNo(), publishTaskReport.getTaskNum(), task);

        } else if (report instanceof PreAssignTaskReport) {
            log.info("handle pre-assign task report");
            PreAssignTaskReport preAssignTaskReport = (PreAssignTaskReport) report;
            // 更新任务执行结果
            int i = esTaskMapper.reportPreAssignTaskReport(preAssignTaskReport);
            TblTktEsReport tblTktEsReport = new TblTktEsReport(Integer.parseInt(preAssignTaskReport.getTaskNo()), task.getTaskType(), preAssignTaskReport.getEsNodeId(), "", Integer.parseInt(preAssignTaskReport.getTaskNum()), task.getBeginNo(), task.getEndNo(), Integer.valueOf(preAssignTaskReport.getCompletedNum()), Integer.valueOf(preAssignTaskReport.getErrorNum()), preAssignTaskReport.getBeginTime(), preAssignTaskReport.getEndTime(), preAssignTaskReport.getOperator(), null);
            esReportService.saveReports(tblTktEsReport);
            TblTktEsProc esProc = new TblTktEsProc(Integer.valueOf(preAssignTaskReport.getTaskNo()), preAssignTaskReport.getEsNodeId(), 9050, DateUtil.dateString8(), preAssignTaskReport.getFileName(), Integer.valueOf(preAssignTaskReport.getTaskNum()), Integer.valueOf(preAssignTaskReport.getCompletedNum()), "0", preAssignTaskReport.getOperator(), null);
            esProcService.save(esProc);
            esReportService.analysisFile(preAssignTaskReport.getFileName(), preAssignTaskReport.getTaskNo(), preAssignTaskReport.getTaskNum(), task);

        } else if (report instanceof HandCancelTaskReport) {
            log.info("handle hand-cancel task report");
            HandCancelTaskReport handCancelTaskReport = (HandCancelTaskReport) report;
            // 更新任务执行结果
            int i = esTaskMapper.reportHandCancelTaskReport(handCancelTaskReport);
            TblTktEsReport tblTktEsReport = new TblTktEsReport(Integer.valueOf(handCancelTaskReport.getTaskNo()), task.getTaskType(), handCancelTaskReport.getEsNodeId(), "", Integer.valueOf(handCancelTaskReport.getTaskNum()), task.getBeginNo(), task.getEndNo(), Integer.valueOf(handCancelTaskReport.getCompletedNum()), Integer.valueOf(handCancelTaskReport.getErrorNum()), handCancelTaskReport.getBeginTime(), handCancelTaskReport.getEndTime(), handCancelTaskReport.getOperator(), null);
            esReportService.saveReports(tblTktEsReport);
            TblTktEsProc esProc = new TblTktEsProc(Integer.valueOf(handCancelTaskReport.getTaskNo()), handCancelTaskReport.getEsNodeId(), 9050, DateUtil.dateString8(), handCancelTaskReport.getFileName(), Integer.valueOf(handCancelTaskReport.getTaskNum()), Integer.valueOf(handCancelTaskReport.getCompletedNum()), "0", handCancelTaskReport.getOperator(), null);
            esProcService.save(esProc);
            esReportService.analysisFile(handCancelTaskReport.getFileName(), handCancelTaskReport.getTaskNo(), handCancelTaskReport.getTaskNum(), task);

        } else if (report instanceof CancelTaskReport) {
            log.info("handle cancel task report");
            CancelTaskReport cancelTaskReport = (CancelTaskReport) report;
            // 更新任务执行结果
            int i = esTaskMapper.reportCancelTaskReport(cancelTaskReport);
            TblTktEsReport tblTktEsReport = new TblTktEsReport(Integer.valueOf(cancelTaskReport.getTaskNo()), task.getTaskType(), cancelTaskReport.getEsNodeId(), "", Integer.valueOf(cancelTaskReport.getTaskNum()), task.getBeginNo(), task.getEndNo(), Integer.valueOf(cancelTaskReport.getCompletedNum()), Integer.valueOf(cancelTaskReport.getErrorNum()), cancelTaskReport.getBeginTime(), cancelTaskReport.getEndTime(), cancelTaskReport.getOperator(), null);
            esReportService.saveReports(tblTktEsReport);
            TblTktEsProc esProc = new TblTktEsProc(Integer.valueOf(cancelTaskReport.getTaskNo()), cancelTaskReport.getEsNodeId(), 9050, DateUtil.dateString8(), cancelTaskReport.getFileName(), Integer.valueOf(cancelTaskReport.getTaskNum()), Integer.valueOf(cancelTaskReport.getCompletedNum()), "0", cancelTaskReport.getOperator(), null);
            esProcService.save(esProc);
            esReportService.analysisFile(cancelTaskReport.getFileName(), cancelTaskReport.getTaskNo(), cancelTaskReport.getTaskNum(), task);

        } else if (report instanceof SortTaskReport) {
//            SortTaskReport sortTaskReport = (SortTaskReport) report;
//            int i = esTaskMapper.reportSortTaskReport(sortTaskReport);

        } else if (report instanceof RecodeTaskReport) {
            log.info("handle recode task report");
            RecodeTaskReport recodeTaskReport = (RecodeTaskReport) report;
            int i = esTaskMapper.reportRecodeTaskReport(recodeTaskReport);
            TblTktEsReport tblTktEsReport = new TblTktEsReport(Integer.valueOf(recodeTaskReport.getTaskNo()), task.getTaskType(), recodeTaskReport.getEsNodeId(), "", Integer.valueOf(recodeTaskReport.getTaskNum()), task.getBeginNo(), task.getEndNo(), Integer.valueOf(recodeTaskReport.getCompleteNum()), Integer.valueOf(recodeTaskReport.getWastedNum()), recodeTaskReport.getBeginTime(), recodeTaskReport.getEndTime(), recodeTaskReport.getOperator(), null);
            esReportService.saveReports(tblTktEsReport);
            TblTktEsProc esProc = new TblTktEsProc(Integer.valueOf(recodeTaskReport.getTaskNo()), recodeTaskReport.getEsNodeId(), 9050, DateUtil.dateString8(), recodeTaskReport.getFileName(), Integer.valueOf(recodeTaskReport.getTaskNum()), Integer.valueOf(recodeTaskReport.getCompleteNum()), "0", recodeTaskReport.getOperator(), null);
            esProcService.save(esProc);
            esReportService.analysisFile(recodeTaskReport.getFileName(), recodeTaskReport.getTaskNo(), recodeTaskReport.getTaskNum(), task);
        } else {
            log.info("处理自定义任务回执");
            CustomTaskReport customTaskReport = (CustomTaskReport) report;
            String beginNoStr = "".equals(customTaskReport.getBeginNo().trim()) ? "0" : customTaskReport.getBeginNo().trim();
            int i = esTaskMapper.reportCustomTaskReport(customTaskReport);
            TblTktEsReport tblTktEsReport = new TblTktEsReport(Integer.valueOf(customTaskReport.getTaskNo().trim()), task.getTaskType(), customTaskReport.getNodeId(), "", Integer.valueOf(customTaskReport.getTaskNum().trim()), Integer.valueOf(beginNoStr), Integer.parseInt(beginNoStr) + Integer.parseInt(customTaskReport.getTaskNum().trim()), Integer.valueOf(customTaskReport.getCompletedNum().trim()), Integer.valueOf(customTaskReport.getWastedNum().trim()), customTaskReport.getBeginTime(), customTaskReport.getEndTime(), customTaskReport.getOperator().trim(), null);
            esReportService.saveReports(tblTktEsReport);
            TblTktEsProc tblTktEsProc = new TblTktEsProc(Integer.valueOf(customTaskReport.getTaskNo().trim()), customTaskReport.getNodeId(), 9050, DateUtil.dateString8(), customTaskReport.getFileName().trim(), Integer.valueOf(customTaskReport.getTaskNum().trim()), Integer.valueOf(customTaskReport.getCompletedNum().trim()), "0", customTaskReport.getOperator().trim(), null);
            esProcService.save(tblTktEsProc);
            esReportService.analysisCustomFile(customTaskReport.getFileName(), customTaskReport.getTaskNo(), customTaskReport.getTaskNum(), task);

        }
        return true;

    }

    @Override
    public ResultVO<?> getCustomTasks(Integer pageNum, Integer pageSize, TblTktEsTask task) {
        TblTktEsTask query = task == null ? new TblTktEsTask() : task;
        query.setTaskType(TaskType.CUSTOM);
        PageInfo<TblTktEsTask> customTasks = PageHelper.startPage(pageNum, pageSize).doSelectPageInfo(() -> {
            esTaskMapper.allCustomTask(query);
        });
        return ResultMapper.ok(customTasks);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<?> saveCustom(TblTktEsTask task) {
        // 上传并解析个性化任务文件
        ResultVO<String> name = fileService.upload(task.getFile());
        String fileName = name.getData();
        task.setLastUpdId(serverName);
        task.setLastUpdTms(null);
        task.setGenTms(null);
        task.setTaskExecStat(TaskExecuteStat.UN_EXECUTED.code());
        task.setTaskAssnStat(TaskAssignStat.UN_ASSIGNED.code());
        task.setApprStat(TaskApplyStat.APPLYING.getCode());
        task.setBeginNo(task.getBeginNo());
        task.setEndNo(task.getEndNo());
        task.setFileName(fileName);
        esTaskMapper.insert(task);
        ResultVO<?> resultVO = personService.insertPersonInfoBatch(task.getTaskNo(), fileName);
        if ("500".equals(resultVO.getCode())) {
            throw new RuntimeException(resultVO.getMsg());
        }
        return ResultMapper.ok();
    }

    public List<CustomTask> transfer(List<CustomTask> customTasks) {
        return customTasks.stream().filter(customTask -> !Objects.isNull(customTask.getPhyCode())).peek(customTask -> {
            customTask.setIdType(PidCdEnum.getPidCode(customTask.getIdType()));
            customTask.setPersonType(PersonTypeEnum.getValue(customTask.getPersonType()));
        }).collect(Collectors.toList());
    }

    @Override
    @Async
    public void customFile(TblTktEsAssign esAssign) {
        TblTktEsTask task = esTaskMapper.selectByPrimaryKey(esAssign.getTaskNo());
        String fileName = CUSTOM_PER_FIX + esAssign.getEsCode() + ByteConvertUtil.addZeroForNum(esAssign.getTaskNo().toString(), 8);
        String fullFileName = ftpComponent.getCustomLocalDir() + fileName;
        String station1 = "0";
        String station2 = "0";
        String area1 = "0";
        String area2 = "0";
        String line1 = "0";
        String line2 = "0";
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fullFileName)))) {
            StringBuffer str = new StringBuffer();
            String firstLine = str.append("PH").append(ByteConvertUtil.addZeroForNum(esAssign.getEsCode(), 8)).append('7').append(ByteConvertUtil.addZeroForNum(String.valueOf(esAssign.getTaskNo()), 8)).append(ByteConvertUtil.addZeroForNum(String.valueOf(task.getTicketType()), 3)).append("00").append(ByteConvertUtil.addZeroForNum(task.getVerNo(), 6)).append(ByteConvertUtil.addZeroForNum(task.getBatchNo(), 10)).append(ByteConvertUtil.addZeroForNum(String.valueOf(task.getBeginNo()), 10)).append(ByteConvertUtil.addZeroForNum(String.valueOf(task.getEndNo()), 10)).append(ByteConvertUtil.addZeroForNum("2", 8)).append("00").append("0000").append(ByteConvertUtil.addZeroForNum(station1, 4)).append(ByteConvertUtil.addZeroForNum(station2, 4)).append(ByteConvertUtil.addZeroForNum(area1, 4)).append(ByteConvertUtil.addZeroForNum(area2, 4)).append(ByteConvertUtil.addZeroForNum(line1, 2)).append(ByteConvertUtil.addZeroForNum(line2, 2)).append(ByteConvertUtil.addZeroForNum(task.getRidePrimession(), 8)).append("0").append("\r\n").toString();
            writer.write(firstLine);
            TblTktPrePerson prePerson = new TblTktPrePerson();
            prePerson.setTaskNo(esAssign.getTaskNo());
            List<TblTktPrePerson> persons = personService.selectSelective(prePerson);
            for (TblTktPrePerson person : persons) {
                String no = person.getPhyCode();
                if (no.length() < 8) {
                    no = ByteConvertUtil.addZeroForNum(no, 8);
                } else {
                    no = no.substring(no.length() - 8, no.length());
                }
                StringBuffer taskStr = new StringBuffer();
                taskStr.append("PB").append(ByteConvertUtil.addZeroForNum(person.getPhyCode(), 16)).append(no).append(person.getPersonType()).append(ByteConvertUtil.addZeroRight(person.getUserName(), 20)).append(ByteConvertUtil.addZeroForNum(person.getIdType(), 2)).append(ByteConvertUtil.addSpaceRight(person.getId(), 32)).append(ByteConvertUtil.addZeroForNum(person.getEmpNo(), 8)).append(ByteConvertUtil.addZeroRight(person.getWorkUnit(), 80)).append("\r\n");
                writer.write(taskStr.toString());
            }
            writer.flush();
        } catch (Exception e) {
            log.error("生成个性化任务文件失败", e);
        }
        Ftp ftp = new Ftp(ftpComponent.getIp(), ftpComponent.getPort(), ftpComponent.getUsername(), ftpComponent.getPassword());
        ftp.setMode(FtpMode.Passive);
        ftp.upload(ftpComponent.getCustomTargetDir(), new File(ftpComponent.getCustomLocalDir() + fileName));
        try {
            ftp.close();
        } catch (IOException e) {
            log.error("关闭FTP连接失败", e);
        }

    }


}
