package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsReport;
import com.chinasofti.huateng.acc.es.server.service.IEsReportService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@AllArgsConstructor
@RequestMapping("/report")
public class EsReportController {

    private final IEsReportService esReportService;


    @PostMapping("/page")
    public ResultVO<?> page(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsReport esReport) {
        return esReportService.selectPage(pageNum,pageSize,esReport);
    }


}
