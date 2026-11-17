package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsInfo;
import com.chinasofti.huateng.acc.es.server.service.IEsInfoService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@AllArgsConstructor
@RequestMapping("/es")
public class EsInfoController {

    private final IEsInfoService esInfoService;


    @PostMapping("/all")
    public ResultVO<?> all() {
        return esInfoService.getAllESInfo();
    }


    @PostMapping("/save")
    public ResultVO<?> saveOne(@RequestBody TblTktEsInfo info) {
        return esInfoService.saveOne(info);
    }



    @PostMapping("/update")
    public ResultVO<?> updateEs(@RequestBody TblTktEsInfo info) {
        return esInfoService.updateByEsCode(info);
    }


    @PostMapping("/page")
    public ResultVO<?> page(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsInfo esInfo) {
        return esInfoService.page(pageNum, pageSize, esInfo);
    }


    @PostMapping("/delete")
    public ResultVO<?> deleteOne(@RequestBody TblTktEsInfo esInfo) {
        String esCode = esInfo.getEsCode();
        return esInfoService.deleteOne(esCode);
    }

}
