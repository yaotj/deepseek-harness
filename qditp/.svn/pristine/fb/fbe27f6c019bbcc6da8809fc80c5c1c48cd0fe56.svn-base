package com.chinasofti.huateng.acc.es.server.service.impl;

import com.chinasofti.huateng.acc.es.server.enumns.PlanStat;
import com.chinasofti.huateng.acc.es.server.mapper.TblTktEsAssignMapper;
import com.chinasofti.huateng.acc.es.server.mapper.TblTktEsTaskMapper;
import com.chinasofti.huateng.acc.es.server.mapper.TblTktTaskPlanMapper;
import com.chinasofti.huateng.acc.es.server.model.TblTktTaskPlan;
import com.chinasofti.huateng.acc.es.server.service.ITaskPlanService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ES任务计划管理
 */
@Service
@Slf4j
public class TaskPlanServiceImpl implements ITaskPlanService {

    @Autowired
    private TblTktTaskPlanMapper taskPlanMapper;

    @Autowired
    private TblTktEsAssignMapper esAssignMapper;

    @Autowired
    private TblTktEsTaskMapper esTaskMapper;

    @Value("${pageConfig.defaultSize:10}")
    private int defaultPageSize;

    @Value("${spring.application.name}")
    private String serverName;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<?> save(TblTktTaskPlan taskPlan) {
        taskPlan.setPlanTms(null);
        taskPlan.setActNum(0);
        taskPlan.setAssignNum(0);
        taskPlan.setLastUpdId(serverName);
        taskPlan.setLastUpdTms(null);
        taskPlan.setTaskPlanStat(PlanStat.APPLYING.code());
        taskPlanMapper.insert(taskPlan);
        return ResultMapper.ok();
    }


    @Override
    public ResultVO<?> taskPlansByPage(Integer pageNum, Integer pageSize, TblTktTaskPlan plan) {
        TblTktTaskPlan query = plan == null ? new TblTktTaskPlan() : plan;
        PageInfo<Object> objectPageInfo = PageHelper.startPage(pageNum, pageSize).doSelectPageInfo(() -> {
            taskPlanMapper.queryAll(query);
        });
        return ResultMapper.ok(objectPageInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<?> updateByPlanNo(TblTktTaskPlan taskPlan) {
        taskPlan.setLastUpdTms(null);
        taskPlan.setLastUpdId(serverName);
        taskPlanMapper.updateByPrimaryKey(taskPlan);
        return ResultMapper.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<?> deleteByPlanNo(Integer planNo) {
        esAssignMapper.deleteByPlanNo(planNo);
        esTaskMapper.deleteByPlanNo(planNo);
        taskPlanMapper.deleteByPrimaryKey(planNo);
        return ResultMapper.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<?> approve(TblTktTaskPlan taskPlan) {
        taskPlanMapper.approve(taskPlan);
        return ResultMapper.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<?> changeStat(TblTktTaskPlan taskPlan) {
        taskPlanMapper.changeStat(taskPlan);
        return ResultMapper.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTask(TblTktTaskPlan taskPlan) {
        int i = taskPlanMapper.assignTask(taskPlan);
        log.info("修改条数{}", i);
    }

    @Override
    public TblTktTaskPlan selectByPlanNo(Integer planNo) {
        return taskPlanMapper.selectByPrimaryKey(planNo);
    }

    @Override
    public List<TblTktTaskPlan> queryAvailable() {
        return taskPlanMapper.queryAllAvailable();
    }

    @Override
    public int updateByPlanNoSelective(TblTktTaskPlan taskPlan) {
        return taskPlanMapper.updateByPrimaryKeySelective(taskPlan);
    }
}
