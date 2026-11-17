package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsAssign;
import com.chinasofti.huateng.acc.es.server.service.IEsAssignService;
import com.chinasofti.huateng.acc.es.server.service.IEsTaskService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@AllArgsConstructor
@RequestMapping("/assign")
public class EsAssignController {

    private final IEsAssignService esAssignService;

    private final IEsTaskService esTaskService;


    @PostMapping("/save")
    public ResultVO<?> save(@RequestBody TblTktEsAssign esAssign) {
        if(esAssign.getCustom()) {
            esAssignService.customFile(esAssign);
        }
        ResultVO<?> save = esAssignService.save(esAssign);
        return save;
    }




    @GetMapping("/{taskNo}/{esCode}")
    public ResultVO<?> getById(@PathVariable("taskNo") Integer taskNo,@PathVariable("esCode") String esCode) {
        return esAssignService.assign(taskNo,esCode);
    }


    @PutMapping("/{taskNo}/{esCode}")
    public ResultVO<?> updateByTaskNoAndEsCode(@PathVariable("taskNo") Integer taskNo,@PathVariable("esCode") String esNo,@RequestBody TblTktEsAssign assign) {
        return esAssignService.updateByTaskNoAndEsCode(taskNo,esNo,assign);
    }

}
