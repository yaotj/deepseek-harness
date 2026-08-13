package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.model.BaseFarePageView;
import com.chinasofti.huateng.para.model.BaseFareStationOption;
import com.chinasofti.huateng.para.service.BaseFarePageService;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 参数管理页面的当前基础票价查询接口。 */
@RestController
@RequestMapping("/page/base-fare")
public class BaseFarePageController {
    private final BaseFarePageService baseFarePageService;

    public BaseFarePageController(BaseFarePageService baseFarePageService) {
        this.baseFarePageService = baseFarePageService;
    }

    /** 按进出站查询当前费率版本的基础票价。 */
    @GetMapping
    public ResultVO<PageInfo<BaseFarePageView>> page(@RequestParam(required = false) String entryStationCode,
                                                      @RequestParam(required = false) String exitStationCode,
                                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return baseFarePageService.pageCurrentBaseFare(entryStationCode, exitStationCode, pageNum, pageSize);
    }

    /** 返回当前路网版本车站，供进出站下拉框使用。 */
    @GetMapping("/stations")
    public ResultVO<List<BaseFareStationOption>> listStations() {
        return baseFarePageService.listCurrentStations();
    }
}
