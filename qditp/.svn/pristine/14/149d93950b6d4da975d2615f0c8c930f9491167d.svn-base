package com.chinasofti.huateng.acc.es.server.service.impl;

import com.chinasofti.huateng.acc.es.server.config.FtpComponent;
import com.chinasofti.huateng.acc.es.server.enumns.PersonTypeEnum;
import com.chinasofti.huateng.acc.es.server.enumns.PidCdEnum;
import com.chinasofti.huateng.acc.es.server.mapper.TblTktPrePersonMapper;
import com.chinasofti.huateng.acc.es.server.model.CustomTask;
import com.chinasofti.huateng.acc.es.server.model.TblTktPrePerson;
import com.chinasofti.huateng.acc.es.server.service.ITblTktPrePersonService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.crab2died.ExcelUtils;
import com.github.crab2died.exceptions.Excel4JException;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author rxwnc
 */
@Service
@Slf4j
public class TblTktPrePersonServiceImpl implements ITblTktPrePersonService {

    private final TblTktPrePersonMapper personMapper;

    private final FtpComponent ftpComponent;

    private final SqlSessionFactory sqlSessionFactory;

    @Autowired
    public TblTktPrePersonServiceImpl(TblTktPrePersonMapper personMapper, FtpComponent ftpComponent, SqlSessionFactory sqlSessionFactory) {
        this.personMapper = personMapper;
        this.ftpComponent = ftpComponent;
        this.sqlSessionFactory = sqlSessionFactory;
    }

    @Override
    public ResultVO<?> insertPersonInfoBatch(Integer taskNo, String fileName) {
        String excelName = ftpComponent.getExcelDir() + fileName;
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        TblTktPrePersonMapper mapper = sqlSession.getMapper(TblTktPrePersonMapper.class);
        try {
            log.info("开始解析excel文件");
            List<CustomTask> customTasks = ExcelUtils.getInstance().readExcel2Objects(excelName, CustomTask.class, 0, 0);
            log.info("excel文件数据解析完成，开始验证");
            Map<String,Long> map = customTasks.stream().filter(customTask -> !Objects.isNull(customTask.getPhyCode())).collect(Collectors.groupingBy(CustomTask::getPhyCode,Collectors.counting()));
            List<String> errorResult = new ArrayList<>();
            map.forEach((s,l) -> {
                if(l > 1) {
                    errorResult.add(s);
                }
            });
            if(errorResult.size() > 0) {
                return ResultMapper.error("请检查excel，物理卡号存在重复" + errorResult);
            }
            log.info("Excel文件中的数据没有问题，开始保存...");
            customTasks
                    .stream()
                    .filter(customTask -> !Objects.isNull(customTask.getPhyCode()))
                    .peek(customTask -> {
                        customTask.setIdType(PidCdEnum.getPidCode(customTask.getIdType()));
                        customTask.setPersonType(PersonTypeEnum.getValue(customTask.getPersonType()));
                    })
                    .map(customTask -> {
                        TblTktPrePerson prePerson = new TblTktPrePerson();
                        prePerson.setTaskNo(taskNo);
                        prePerson.setEmpNo(customTask.getUserNo());
                        prePerson.setId(customTask.getId());
                        prePerson.setIdType(customTask.getIdType());
                        prePerson.setPersonType(customTask.getPersonType());
                        prePerson.setPhyCode(customTask.getPhyCode());
                        prePerson.setUserName(customTask.getUserName());
                        prePerson.setWorkUnit(customTask.getWorkUnit());
                        return prePerson;
                    })
                    .forEach(mapper::insert);
            log.info("数据保存完毕.");
            sqlSession.commit();
        }
        catch (Excel4JException | IOException e) {
            log.error("解析excel出现异常", e);
            return ResultMapper.error();
        }
        catch (Exception e) {
            log.error("保存预制人员信息异常", e);
            return ResultMapper.error();
        }
        finally {
            sqlSession.close();
        }
        return ResultMapper.ok();

    }

    @Override
    public List<TblTktPrePerson> selectSelective(TblTktPrePerson prePerson) {
        return personMapper.selectSelective(prePerson);
    }

    @Override
    public TblTktPrePerson selectByPrimary(TblTktPrePerson p) {
        return personMapper.selectByPrimary(p);
    }
}
