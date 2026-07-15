package com.chinasofti.huateng.acc.es.server.service.impl;

import cn.hutool.extra.ftp.Ftp;
import cn.hutool.extra.ftp.FtpMode;
import com.chinasofti.huateng.acc.es.server.config.FtpComponent;
import com.chinasofti.huateng.acc.es.server.enumns.TaskExecuteStat;
import com.chinasofti.huateng.acc.es.server.enumns.TaskType;
import com.chinasofti.huateng.acc.es.server.enumns.TicketType;
import com.chinasofti.huateng.acc.es.server.mapper.*;
import com.chinasofti.huateng.acc.es.server.model.*;
import com.chinasofti.huateng.acc.es.server.service.IEsReportService;
import com.chinasofti.huateng.acc.es.server.service.ITblTktPrePersonService;
import com.chinasofti.huateng.acc.es.server.util.DateUtil;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class EsReportServiceImpl implements IEsReportService {

    @Autowired
    private TblTktEsReportMapper esReportMapper;

    @Value("${pageConfig.defaultSize:10}")
    private int defaultPageSize;

    @Autowired
    private TblTktEsTaskMapper esTaskMapper;

    @Autowired
    private FtpComponent ftpComponent;

    @Autowired
    private TblTktEsFileProcLogMapper logMapper;

    @Value("${spring.application.name}")
    private String serverName;

    @Autowired
    private TblStlTicketSetMapper ticketSetMapper;

    @Autowired
    private ITblTktPrePersonService personService;


    @Override
    public ResultVO<?> selectPage(Integer pageNum,Integer pageSize,TblTktEsReport esReport) {
        TblTktEsReport query = esReport == null ? new TblTktEsReport() : esReport;
        PageInfo<TblTktEsReport> objectPageInfo = PageHelper.startPage(pageNum, pageSize).doSelectPageInfo(() -> {
            esReportMapper.quaryAll(query);
        });
        return ResultMapper.ok(objectPageInfo);
    }

    @Override
    public ResultVO<?> saveReports(TblTktEsReport report) {
        esReportMapper.insert(report);
        return ResultMapper.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Async
    public void analysisFile(String fileName, String taskNo, String taskNum, TblTktEsTask task) {
        log.info("start analysisFile, fileName={}", fileName);

        String fileSign = null;
        String esNodeId = null;
        String operator = null;
        String actionCode = null;
        String ticketType = null;
        String storeTicketType = null;
        String phyType= null;
        String version = null;
        String batchNo = null;
        String beginNo = null;
        String endNo = null;
        BufferedReader in = null;

        Ftp ftp = new Ftp(ftpComponent.getIp(), ftpComponent.getPort(), ftpComponent.getUsername(), ftpComponent.getPassword());
        ftp.setMode(FtpMode.Passive);
        String localFileName = ftpComponent.getReportLocalDir() + fileName;
        File file = new File(localFileName);
        ftp.download(ftpComponent.getReportTargetDir(),fileName,file);

        if (file.length() == 0) {
            throw new IllegalArgumentException("文件为空");
        }
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(localFileName)));
            String report  = null;
            report = in.readLine();
            if(!StringUtils.isEmpty(report)) {
                fileSign = report.substring(0,2);
                esNodeId = report.substring(2,10);
                operator = report.substring(10,20);
                actionCode = report.substring(20,21);
                ticketType = report.substring(21,24);
                storeTicketType = report.substring(24,26);
                phyType = report.substring(26,29);
                version = report.substring(29,35);
                batchNo = report.substring(35,45);
                beginNo = report.substring(45,55);
                endNo = report.substring(55,65);

            } else {
                log.error("文件内容为空,fileName={}", fileName);
                throw new IllegalArgumentException("文件内容为空");
            }
            List<Object> addList = new ArrayList<Object>();
            List<Object> deleteList = new ArrayList<Object>();
            List<Object> updateList = new ArrayList<Object>();
            List<TblTktEsFileProcLog> logs = new ArrayList<>();
            int count = 0;
            Integer param = Integer.valueOf(ticketType);
            TblStlTicketSet ticketSet = ticketSetMapper.findByTicketType(param);
            while (!StringUtils.isEmpty(report = in.readLine())) {
                // 记录标识 2
                String recordSign = report.substring(0,2);
                // 票卡逻辑号 16
                String ticketNo = report.substring(2,18);
                // 原票卡逻辑号 16
                String originalTicketNo = report.substring(18,34);
                // CSN 16
                String CSD = report.substring(34,50);
                // 票面号 16
                String printedNo = report.substring(50,66);
                // 初始金额 9
                String initAmt = report.substring(66,75);
                // 初始奖励金额 9
                String reward = report.substring(75,84);
                // 有效天数 6
                String validDays = report.substring(84,90);
                // 有效期开始日期 8
                String beginDay = report.substring(90,98);
                // 押金 9
                String deposit = report.substring(98,107);
                // 充值次数 9
                String rechargeCount = report.substring(107,116);
                // 消费次数 9
                String consume = report.substring(116,125);
                // 发行序号 10
                String sNum = report.substring(125,135);

                String endTime = null;
                if (!Objects.isNull(task.getBeginDate())) {
                    DateTimeFormatter yyyyMMdd = DateTimeFormatter.ofPattern("yyyyMMdd");
                    if ("00000000".equals(task.getBeginDate())) {
                        int year = LocalDate.now().plusYears(1).getYear();
                        endTime = year + "1231";
                    } else {
                        LocalDate begin = LocalDate.of(Integer.parseInt(task.getBeginDate().substring(0, 4)), Integer.parseInt(task.getBeginDate().substring(4, 6)), Integer.parseInt(task.getBeginDate().substring(6, 8).replaceAll(" ","")));
                        LocalDate localDate = begin.plusDays(task.getValidDays());
                        endTime = localDate.format(yyyyMMdd);
                    }

                }
                String type1 = ticketSet.getType1();
                // 卡类型
                String cardType = ticketSet.getCardType();
                if (TicketType.ONE_WAY.getCode().equals(type1)) {
                    TblStlTicketInfo tblStlTicketInfo = new TblStlTicketInfo(ticketNo, Short.valueOf(ticketType), Short.valueOf(ticketSet.getType5()), CSD, printedNo, cardType, Short.parseShort(version), Integer.parseInt(phyType), DateUtil.dateString8(), Integer.parseInt(batchNo), "", "", Integer.parseInt(initAmt), Integer.parseInt(reward), endTime, "00", DateUtil.dateString8(), null, 0, 0, 0, new Date(), operator, new Date(),originalTicketNo);
                    if (task.getTaskType().equals(TaskType.PUBLISH)) {
                        addList.add(tblStlTicketInfo);
                    } else if (task.getTaskType().equals(TaskType.RECODE)){
                        updateList.add(tblStlTicketInfo);
                    } else if (task.getTaskType().equals(TaskType.CANCEL)){
                        deleteList.add(tblStlTicketInfo);
                    }
                } else if (TicketType.STORE.getCode().equals(type1)){

                    TblStlAcctInfo tblStlAcctInfo = new TblStlAcctInfo(ticketNo,Short.parseShort(ticketType), Short.valueOf(ticketSet.getType5()),CSD,printedNo,cardType,Short.parseShort(version),Integer.valueOf(phyType),DateUtil.dateString8(),Integer.parseInt(batchNo),"","",Integer.parseInt(initAmt),Integer.parseInt(reward),task.getDepAmt(),0,endTime,"00","00",DateUtil.dateString8(),null,0,0,0,0,0,0,0,Integer.parseInt(initAmt),new Date(),originalTicketNo,ticketNo,operator,new Date());
                    if (task.getTaskType().equals(TaskType.PUBLISH)) {
                        addList.add(tblStlAcctInfo);
                    } else if (task.getTaskType().equals(TaskType.RECODE)){
                        updateList.add(tblStlAcctInfo);
                    } else if (task.getTaskType().equals(TaskType.CANCEL)){
                        deleteList.add(tblStlAcctInfo);
                    }
                }
                count ++;
            }
            TblTktEsFileProcLog procLog = new TblTktEsFileProcLog(Integer.valueOf(taskNo), esNodeId, count, actionCode, fileName, "0", count, count, 0, report, operator, null);
            record(addList,updateList,deleteList,procLog);

//            return true;
        } catch (FileNotFoundException e) {
            log.error("文件不存在,fileName={}", fileName, e);
        } catch (IOException e) {
            log.error("读取文件失败,fileName={}", fileName, e);
        } finally {
            if (!Objects.isNull(in)) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error("关闭文件流失败", e);
                }
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @Async
    public void analysisCustomFile(String fileName, String taskNo, String taskNum, TblTktEsTask task) {
        String fileSign = null;
        String esNodeId = null;
        String operator = null;
        String actionCode = null;
        String ticketType = null;
        String storeTicketType = null;
        String phyType= null;
        String version = null;
        String batchNo = null;
        String beginNo = null;
        String endNo = null;
        BufferedReader in = null;
        Ftp ftp = new Ftp(ftpComponent.getIp(), ftpComponent.getPort(), ftpComponent.getUsername(), ftpComponent.getPassword());
        ftp.setMode(FtpMode.Passive);
        String localFileName = ftpComponent.getReportLocalDir() + fileName;
        File file = new File(localFileName);
        ftp.download(ftpComponent.getReportTargetDir(),fileName,file);
        if (file.length() == 0) {
            throw new IllegalArgumentException("文件为空");
        }
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(localFileName)));
            String report  = null;
            report = in.readLine();
            if(!StringUtils.isEmpty(report)) {
                fileSign = report.substring(0,2);
                esNodeId = report.substring(2,10);
                operator = report.substring(10,20);
                actionCode = report.substring(20,21);
                ticketType = report.substring(21,24);
                storeTicketType = report.substring(24,26);
                phyType = report.substring(26,29);
                version = report.substring(29,35);
                batchNo = report.substring(35,45);
                beginNo = report.substring(45,55);
                endNo = report.substring(55,65);

            } else {
                log.error("文件内容为空,fileName={}", fileName);
                throw new IllegalArgumentException("文件内容为空");
            }
            String finalType = ticketType;
            TblStlTicketSet ticketSet = ticketSetMapper.findByTicketType(Integer.valueOf(finalType));

            List<TblStlTicketInfo> ticketList = new ArrayList<TblStlTicketInfo>();
            List<TblStlAcctInfo> accList = new ArrayList<TblStlAcctInfo>();

            TblTktPrePerson prePerson = new TblTktPrePerson();
            prePerson.setTaskNo(task.getTaskNo());
            List<TblTktPrePerson> tblTktPrePeople = personService.selectSelective(prePerson);

            List<TblStlPersonInfo> personInfos = new ArrayList<>();
            int count = 0;
            while (!StringUtils.isEmpty(report = in.readLine())) {
                // 记录标识
                String recordSign = report.substring(0,2);
                // 序号
                String seqNo = report.substring(2,10);
                // 票卡逻辑号
                String ticketLogicNo = report.substring(10,26);
                // 物理卡号
                String phyNo = report.substring(26,42);
                // 票面号
                String printNo = report.substring(42,58);
                // 初始金额
                String initPrice = report.substring(58,67);
                // 初始奖励金额
                String initReward = report.substring(67,76);
                // 有效天数
                String validDay = report.substring(76,82);
                // 有效期开始日期
                String beginDay = report.substring(82,90);
                // 押金
                String pledge = report.substring(90,99);
                // 充值次数
                String chargeNum = report.substring(99,108);
                // 消费次数
                String consumerNum  = report.substring(108,117);
                // 发行序号
                String publishSeq = report.substring(117,127);
                String endTime = null;
                for (int i = 0 ; i < tblTktPrePeople.size(); i ++) {
                    TblTktPrePerson thePerson = tblTktPrePeople.get(i);
                    if (seqNo.equals(thePerson.getPhyCode())){
                        TblStlPersonInfo cn = new TblStlPersonInfo(ticketLogicNo, thePerson.getUserName(), "0", thePerson.getIdType(), thePerson.getId(), "0", "CN", thePerson.getWorkUnit(), thePerson.getEmpNo(), "000", "", "", "", serverName, new Date(), null);
                        personInfos.add(cn);
                        tblTktPrePeople.remove(i);
                        break;
                    }
                }
                LocalDate from = LocalDate.parse(beginDay, DateTimeFormatter.ofPattern("yyyyMMdd"));
                LocalDate localDate = from.plusDays(Integer.parseInt(validDay));
                endTime = DateTimeFormatter.ofPattern("yyyyMMdd").format(localDate);
                String type1 = ticketSet.getType1();
                String cardType = ticketSet.getCardType();// 卡类型
                if (TicketType.ONE_WAY.getCode().equals(type1)) {
                    TblStlTicketInfo tblStlTicketInfo = new TblStlTicketInfo(ticketLogicNo, Short.parseShort(ticketType), Short.parseShort(ticketSet.getType5()), phyType, printNo, ticketType, Short.parseShort(version), 1, beginDay, Integer.valueOf(batchNo), task.getSpecifyAreas(), "", Integer.valueOf(initPrice), Integer.valueOf(initReward), endTime,
                            "00", DateUtil.dateString8(), null, 0, 0, 0, new Date(), operator, new Date(), null);
                    ticketList.add(tblStlTicketInfo);
                } else if (TicketType.STORE.getCode().equals(type1)){
                    TblStlAcctInfo tblStlAcctInfo = new TblStlAcctInfo(ticketLogicNo, Short.valueOf(ticketType), Short.valueOf(ticketSet.getType5()), phyNo, printNo, cardType, Short.valueOf(version), 2, DateUtil.dateString8(), Integer.valueOf(batchNo), task.getSpecifyAreas(), "", Integer.valueOf(initPrice),
                            Integer.valueOf(initReward), Integer.valueOf(pledge), 0, endTime, "00", "00", DateUtil.dateString8(), null, Integer.valueOf(chargeNum), Integer.valueOf(consumerNum), Integer.valueOf(consumerNum), Integer.valueOf(consumerNum), Integer.valueOf(consumerNum), 0, 0, 0, new Date(), null, ticketLogicNo, operator, new Date());
                    accList.add(tblStlAcctInfo);
                }
                count ++;
            }
            TblTktEsFileProcLog tblTktEsFileProcLog = new TblTktEsFileProcLog(Integer.valueOf(taskNo.trim()), esNodeId, 0, actionCode, fileName, "0", Integer.valueOf(taskNum.trim()), count, Integer.valueOf(taskNum.trim()) - count, "", operator.trim(), null);
            logMapper.insert(tblTktEsFileProcLog);

            SqlSession sqlSession = null;
            try {
                sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false);
//                if (!CollectionUtils.isEmpty(accList)) {
//                    TblStlAcctInfoMapper mapper = sqlSession.getMapper(TblStlAcctInfoMapper.class);
//                    accList.stream().forEach(mapper::updateByPrimaryKey);
//                }
//                if (!CollectionUtils.isEmpty(ticketList)) {
//                    TblStlTicketInfoMapper mapper = sqlSession.getMapper(TblStlTicketInfoMapper.class);
//                    ticketList.stream().forEach(mapper::updateByPrimaryKey);
//                }
                TblStlPersonInfoMapper mapper = sqlSession.getMapper(TblStlPersonInfoMapper.class);
                personInfos.stream().forEach(mapper::insert);
                sqlSession.commit();
            } catch (Exception e) {
                log.error("保存实名票信息失败", e);
                TblTktEsTask esTask = new TblTktEsTask();
                task.setTaskNo(task.getTaskNo());
                log.error("任务处理失败,taskNo={}", task.getTaskNo());
                task.setTaskExecStat(TaskExecuteStat.FAILURE.code());
                esTaskMapper.updateByPrimaryKeySelective(task);
                log.error("任务执行状态已更新为{}", task.getTaskExecStat());
            }finally {
                if (!Objects.isNull(sqlSession)) {
                    sqlSession.close();
                }
            }

//            return true;
        } catch (FileNotFoundException e) {
            log.error("文件不存在,fileName={}", fileName, e);
        } catch (IOException e) {
            log.error("读取文件失败,fileName={}", fileName, e);
        } finally {
            if (!Objects.isNull(in)) {
                try {
                    in.close();
                } catch (IOException e) {
                    log.error("关闭文件流失败", e);
                }
            }
        }

//        return true;
    }

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    /**
     * 批量记录文件处理结果
     * @param addList
     * @param updateList
     * @param deleteList
     * @param procLog
     */
    public void record(List<Object> addList,List<Object> updateList,List<Object> deleteList,TblTktEsFileProcLog procLog) {

        SqlSession sqlSession = null;
        log.info("addList size={}", addList.size());
        log.info("deleteList size={}", deleteList.size());
        log.info("updateList size={}", updateList.size());
        try {
            sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH, false);
            // 保存文件处理日志
            logMapper.insert(procLog);
            if (!CollectionUtils.isEmpty(addList)) {
                Object o = addList.get(0);
                if (o instanceof TblStlTicketInfo) {
                    TblStlTicketInfoMapper tblStlTicketInfoMapper = sqlSession.getMapper(TblStlTicketInfoMapper.class);
                    addList.stream().map(obj -> (TblStlTicketInfo)obj).forEach(tblStlTicketInfoMapper::insert);
                } else if (o instanceof TblStlAcctInfo) {
                    TblStlAcctInfoMapper mapper = sqlSession.getMapper(TblStlAcctInfoMapper.class);
                    addList.stream().map(obj -> (TblStlAcctInfo)obj).forEach(mapper::insert);
                } else {
                    System.out.println("unsupported add type");
                }
            }
            if(!CollectionUtils.isEmpty(deleteList)) {
                Object o = deleteList.get(0);
                if (o instanceof TblStlTicketInfo) {
                    TblStlTicketInfoMapper tblStlTicketInfoMapper = sqlSession.getMapper(TblStlTicketInfoMapper.class);
                    deleteList.stream().map(obj -> (TblStlTicketInfo)obj).forEach(tblStlTicketInfoMapper::deleteStat);
                } else if (o instanceof TblStlAcctInfo) {
                    TblStlAcctInfoMapper mapper = sqlSession.getMapper(TblStlAcctInfoMapper.class);
                    deleteList.stream().map(obj -> (TblStlAcctInfo)obj).forEach(mapper::deleteStat);
                } else {
                    System.out.println("unsupported delete type");
                }
            }
            if (!CollectionUtils.isEmpty(updateList)) {
                Object o = updateList.get(0);
                if (o instanceof TblStlTicketInfo) {
                    TblStlTicketInfoMapper tblStlTicketInfoMapper = sqlSession.getMapper(TblStlTicketInfoMapper.class);
                    updateList.stream().map(obj -> (TblStlTicketInfo)obj).forEach(tblStlTicketInfoMapper::insert);
                    updateList.stream().map(obj -> (TblStlTicketInfo)obj).forEach(tblStlTicketInfoMapper::delTheOldStat);
                } else if (o instanceof TblStlAcctInfo) {
                    TblStlAcctInfoMapper mapper = sqlSession.getMapper(TblStlAcctInfoMapper.class);
                    updateList.stream().map(obj -> (TblStlAcctInfo)obj).forEach(mapper::insert);
                    updateList.stream().map(obj -> (TblStlAcctInfo)obj).forEach(mapper::delTheOldStat);
                } else {
                    System.out.println("unsupported update type");
                }
            }
            sqlSession.commit();
        } catch (Exception e) {
            log.error("批量记录文件处理结果失败", e);
            TblTktEsTask task = new TblTktEsTask();
            task.setTaskNo(procLog.getTaskNo());
            log.error("任务处理失败,taskNo={}", procLog.getTaskNo());
            task.setTaskExecStat(TaskExecuteStat.FAILURE.code());
            log.error("任务执行状态已更新为{}", task.getTaskExecStat());
            esTaskMapper.updateByPrimaryKeySelective(task);
        } finally {
            if (!Objects.isNull(sqlSession)) {
                sqlSession.close();
            }
        }


    }


}
