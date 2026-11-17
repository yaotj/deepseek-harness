package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.model.BaseFareLineOption;
import com.chinasofti.huateng.para.model.BaseFarePageView;
import com.chinasofti.huateng.para.model.BaseFareStationOption;
import com.chinasofti.huateng.para.service.BaseFarePageService;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/page/base-fare")
public class BaseFarePageController {
    private final BaseFarePageService baseFarePageService;

    public BaseFarePageController(BaseFarePageService baseFarePageService) {
        this.baseFarePageService = baseFarePageService;
    }

    @GetMapping
    public ResultVO<PageInfo<BaseFarePageView>> page(@RequestParam(required = false) String entryStationCode,
                                                      @RequestParam(required = false) String exitStationCode,
                                                      @RequestParam(required = false) String entryLineCode,
                                                      @RequestParam(required = false) String exitLineCode,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return baseFarePageService.pageCurrentBaseFare(entryStationCode, exitStationCode, entryLineCode, exitLineCode, pageNum, pageSize);
    }

    @GetMapping("/stations")
    public ResultVO<List<BaseFareStationOption>> listStations(@RequestParam(required = false) String lineCode) {
        return baseFarePageService.listCurrentStations(lineCode);
    }

    @GetMapping("/lines")
    public ResultVO<List<BaseFareLineOption>> listLines() {
        return baseFarePageService.listCurrentLines();
    }
}
