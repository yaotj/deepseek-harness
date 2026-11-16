package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsAssign;
import com.chinasofti.huateng.acc.es.server.service.IEsAssignService;
import com.chinasofti.huateng.acc.es.server.service.IEsTaskService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 任务分配
 */
@RestController
@AllArgsConstructor
@RequestMapping("/assign")
public class EsAssignController {

    private final IEsAssignService esAssignService;

    private final IEsTaskService esTaskService;

    /**
     * 任务分配
     * @param esAssign
     * @return
     */
    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody TblTktEsAssign esAssign) {
        if(esAssign.getCustom()) {
            esAssignService.customFile(esAssign);
        }
        ResultVO<?> save = esAssignService.save(esAssign);
        return save;
    }



    /**
     * 根据taskNo和esCode进行查询
     * @param taskNo
     * @param esCode
     * @return
     */
    @GetMapping("/{taskNo}/{esCode}")
    public ResultVO<?> getById(@PathVariable("taskNo") Integer taskNo,@PathVariable("esCode") String esCode) {
        return esAssignService.assign(taskNo,esCode);
    }

    /**
     * 根据taskNo和esCode对任务分配进行修改
     * @param taskNo
     * @param esNo
     * @param assign
     * @return
     */
    @PutMapping("/{taskNo}/{esCode}")
    public ResultVO<?> updateByTaskNoAndEsCode(@PathVariable("taskNo") Integer taskNo,@PathVariable("esCode") String esNo,@RequestBody TblTktEsAssign assign) {
        return esAssignService.updateByTaskNoAndEsCode(taskNo,esNo,assign);
    }

}
