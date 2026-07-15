package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktTaskPlan;
import com.chinasofti.huateng.acc.es.server.service.ITaskPlanService;
import com.chinasofti.huateng.acc.es.server.util.AlgorithmUtils;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务计划
 */
@RestController
@AllArgsConstructor
@RequestMapping("/plan")
public class TaskPlanController {

    private final ITaskPlanService taskPlanService;

    /**
     * 新增任务计划
     *
     * @param taskPlan
     * @return
     */
    @PostMapping("/save")
    public ResultVO<?> saveOne(@RequestBody TblTktTaskPlan taskPlan) {
        return taskPlanService.save(taskPlan);
    }

    /**
     * 分页查询任务计划
     */
    @PostMapping("/page")
    public ResultVO<?> taskPlansByPage(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktTaskPlan plan) {
        return taskPlanService.taskPlansByPage(pageNum, pageSize, plan);
    }

    /**
     * 通过计划planNo修改计划
     *
     * @param taskPlan
     * @return
     */
    @PostMapping("/update")
    public ResultVO<?> updateByPlanNo(@RequestBody TblTktTaskPlan taskPlan) {
        return taskPlanService.updateByPlanNo(taskPlan);
    }

    /**
     * 通过planNo对任务计划进行删除
     *
     * @param
     * @return
     */
    @PostMapping("/delete")
    public ResultVO<?> deleteByPlanNo(@RequestBody TblTktTaskPlan taskPlan) {
        Integer planNo = taskPlan.getPlanNo();
        return taskPlanService.deleteByPlanNo(planNo);
    }

    /**
     * (发行、预赋值)   审批
     *
     * @param taskPlan
     * @return
     */
    @PostMapping("/approve")
    public ResultVO<?> approve(@RequestBody TblTktTaskPlan taskPlan) {
        return taskPlanService.approve(taskPlan);
    }

    /**
     * 查询可以分配任务的计划（已经审核或者审批中）
     *
     * @return
     */
    @PostMapping(value = "/available")
    public ResultVO<?> queryAvailablePlan() {
        List<TblTktTaskPlan> availablePlans = taskPlanService.queryAvailable();
        return ResultMapper.ok(availablePlans);
    }

    /**
     * （其他缴销，重编码，注销）同意，拒绝
     *
     * @param taskPlan
     * @return
     */
    @PostMapping("/stat")
    public ResultVO<?> changeStat(@RequestBody TblTktTaskPlan taskPlan) {
        return taskPlanService.changeStat(taskPlan);
    }

}
