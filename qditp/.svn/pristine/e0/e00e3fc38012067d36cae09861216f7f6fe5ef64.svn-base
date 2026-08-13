package com.chinasofti.huateng.para.controller;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.network.LineInfo;
import com.chinasofti.huateng.para.entity.network.StationInfo;
import com.chinasofti.huateng.para.model.LineStationVersion;
import com.chinasofti.huateng.para.service.ParaPageService;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 参数管理页面查询接口。 */
@RestController
@RequestMapping("/page")
public class ParaPageController {
    private final ParaPageService paraPageService;

    public ParaPageController(ParaPageService paraPageService) {
        this.paraPageService = paraPageService;
    }

    @GetMapping("/line-info")
    public ResultVO<PageInfo<LineInfo>> pageLineInfo(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return paraPageService.pageLineInfo(pageNum, pageSize);
    }

    @GetMapping("/station-info")
    public ResultVO<PageInfo<StationInfo>> pageStationInfo(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return paraPageService.pageStationInfo(pageNum, pageSize);
    }

    @GetMapping("/line-station-version")
    public ResultVO<PageInfo<LineStationVersion>> pageLineStationVersion(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        return paraPageService.pageLineStationVersion(pageNum, pageSize);
    }
}
