package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsTask;
import com.chinasofti.huateng.acc.es.server.service.IEsTaskService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 拆分任务
 *
 * @author rxwnc
 */
@RestController
@AllArgsConstructor
@RequestMapping("/task")
public class EsTaskController {

    private final IEsTaskService esTaskService;

    /**
     * 拆分任务   (发行，预赋值)
     *
     * @param esTask
     * @return
     */
    @PostMapping("/1/save")
    public ResultVO<?> save(@RequestBody TblTktEsTask esTask) {
        return esTaskService.save(esTask);
    }


    /**
     * 修改任务 (发行，预赋值)
     *
     * @param esTask
     * @return
     */
    @PostMapping("/1/update")
    public ResultVO<?> update(@RequestBody TblTktEsTask esTask) {
        return esTaskService.updateTask(esTask);
    }

    /**
     * 删除任务 (发行，预赋值)
     *
     * @param taskNo
     * @return
     */
    @DeleteMapping("/1/delete")
    public ResultVO<?> deleteById(@RequestParam("taskNo") Integer taskNo, @RequestParam(name = "esCode", required = false) String esCode) {
        return esTaskService.deleteById(taskNo, esCode);
    }

    /**
     * 分页查询任务 (发行，预赋值)
     *
     * @param task
     * @return
     */
    @PostMapping("/1/page")
    public ResultVO<?> esTasksByPage(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsTask task) {
        return esTaskService.selectTasksByPage(pageNum, pageSize, task);
    }

    /**
     * 任务的保存 （用于缴销/重编码/注销/个性化）
     *
     * @param esTask
     * @return
     */
    @PostMapping("/2/save")
    public ResultVO<?> save2(@RequestBody TblTktEsTask esTask) {
        return esTaskService.save2(esTask);
    }

    /**
     * 删除任务 （用于缴销/重编码/注销）
     *
     * @param taskNo
     * @return
     */
    @DeleteMapping("/2/delete")
    public ResultVO<?> delete2(@RequestParam("taskNo") Integer taskNo, @RequestParam(name = "esCode", required = false) String esCode) {
        return esTaskService.delete2(taskNo, esCode);
    }

    /**
     * 修改任务 （用于缴销/重编码/注销）
     *
     * @param task
     * @return
     */
    @PostMapping("/2/update")
    public ResultVO<?> update2(@RequestBody TblTktEsTask task) {
        return esTaskService.update2(task);
    }

    /**
     * 分页查询缴销/重编码/注销的任务
     *
     * @param task
     * @return
     */
    @PostMapping("/2/page")
    public ResultVO<?> page2(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsTask task) {
        return esTaskService.page2(pageNum, pageSize, task);
    }

    /**
     * 获取所有的自动化任务
     *
     * @param task
     * @return
     */
    @PostMapping("/custom/page")
    public ResultVO<?> customTasks(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsTask task) {
        return esTaskService.getCustomTasks(pageNum, pageSize, task);
    }

    /**
     * 保存个性化任务
     *
     * @param task
     * @return
     */
    @PostMapping("/custom/save")
    public ResultVO<?> addCustomTask(@RequestBody TblTktEsTask task) {
        return esTaskService.saveCustom(task);
    }

}
