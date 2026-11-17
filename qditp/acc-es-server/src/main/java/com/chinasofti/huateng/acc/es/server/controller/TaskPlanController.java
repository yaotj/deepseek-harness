package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktTaskPlan;
import com.chinasofti.huateng.acc.es.server.service.ITaskPlanService;
import com.chinasofti.huateng.acc.es.server.util.AlgorithmUtils;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@AllArgsConstructor
@RequestMapping("/plan")
public class TaskPlanController {

    private final ITaskPlanService taskPlanService;


    @PostMapping("/save")
    public ResultVO<?> saveOne(@RequestBody TblTktTaskPlan taskPlan) {
        return taskPlanService.save(taskPlan);
    }


    @PostMapping("/page")
    public ResultVO<?> taskPlansByPage(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktTaskPlan plan) {
        return taskPlanService.taskPlansByPage(pageNum, pageSize, plan);
    }


    @PostMapping("/update")
    public ResultVO<?> updateByPlanNo(@RequestBody TblTktTaskPlan taskPlan) {
        return taskPlanService.updateByPlanNo(taskPlan);
    }


    @PostMapping("/delete")
    public ResultVO<?> deleteByPlanNo(@RequestBody TblTktTaskPlan taskPlan) {
        Integer planNo = taskPlan.getPlanNo();
        return taskPlanService.deleteByPlanNo(planNo);
    }


    @PostMapping("/approve")
    public ResultVO<?> approve(@RequestBody TblTktTaskPlan taskPlan) {
        return taskPlanService.approve(taskPlan);
    }


    @PostMapping(value = "/available")
    public ResultVO<?> queryAvailablePlan() {
        List<TblTktTaskPlan> availablePlans = taskPlanService.queryAvailable();
        return ResultMapper.ok(availablePlans);
    }


    @PostMapping("/stat")
    public ResultVO<?> changeStat(@RequestBody TblTktTaskPlan taskPlan) {
        return taskPlanService.changeStat(taskPlan);
    }

}
