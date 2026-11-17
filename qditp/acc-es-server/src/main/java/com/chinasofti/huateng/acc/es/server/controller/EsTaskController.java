package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsTask;
import com.chinasofti.huateng.acc.es.server.service.IEsTaskService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@AllArgsConstructor
@RequestMapping("/task")
public class EsTaskController {

    private final IEsTaskService esTaskService;


    @PostMapping("/1/save")
    public ResultVO<?> save(@RequestBody TblTktEsTask esTask) {
        return esTaskService.save(esTask);
    }



    @PostMapping("/1/update")
    public ResultVO<?> update(@RequestBody TblTktEsTask esTask) {
        return esTaskService.updateTask(esTask);
    }


    @DeleteMapping("/1/delete")
    public ResultVO<?> deleteById(@RequestParam("taskNo") Integer taskNo, @RequestParam(name = "esCode", required = false) String esCode) {
        return esTaskService.deleteById(taskNo, esCode);
    }


    @PostMapping("/1/page")
    public ResultVO<?> esTasksByPage(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsTask task) {
        return esTaskService.selectTasksByPage(pageNum, pageSize, task);
    }


    @PostMapping("/2/save")
    public ResultVO<?> save2(@RequestBody TblTktEsTask esTask) {
        return esTaskService.save2(esTask);
    }


    @DeleteMapping("/2/delete")
    public ResultVO<?> delete2(@RequestParam("taskNo") Integer taskNo, @RequestParam(name = "esCode", required = false) String esCode) {
        return esTaskService.delete2(taskNo, esCode);
    }


    @PostMapping("/2/update")
    public ResultVO<?> update2(@RequestBody TblTktEsTask task) {
        return esTaskService.update2(task);
    }


    @PostMapping("/2/page")
    public ResultVO<?> page2(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsTask task) {
        return esTaskService.page2(pageNum, pageSize, task);
    }


    @PostMapping("/custom/page")
    public ResultVO<?> customTasks(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsTask task) {
        return esTaskService.getCustomTasks(pageNum, pageSize, task);
    }


    @PostMapping("/custom/save")
    public ResultVO<?> addCustomTask(@RequestBody TblTktEsTask task) {
        return esTaskService.saveCustom(task);
    }

}
