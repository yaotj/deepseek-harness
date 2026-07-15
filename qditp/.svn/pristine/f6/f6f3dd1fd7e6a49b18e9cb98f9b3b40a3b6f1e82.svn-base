package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsInfo;
import com.chinasofti.huateng.acc.es.server.service.IEsInfoService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * ES编码机设备
 */
@RestController
@AllArgsConstructor
@RequestMapping("/es")
public class EsInfoController {

    private final IEsInfoService esInfoService;

    /**
     * 获取所有的ES设备信息
     *
     * @return
     */
    @PostMapping("/all")
    public ResultVO<?> all() {
        return esInfoService.getAllESInfo();
    }

    /**
     * 录入编码机
     *
     * @param info
     * @return
     */
    @PostMapping("/save")
    public ResultVO<?> saveOne(@RequestBody TblTktEsInfo info) {
        return esInfoService.saveOne(info);
    }


    /**
     * 修改ES设备
     *
     * @param info
     * @return
     */
    @PostMapping("/update")
    public ResultVO<?> updateEs(@RequestBody TblTktEsInfo info) {
        return esInfoService.updateByEsCode(info);
    }

    /**
     * 查询所有es
     *
     * @return
     */
    @PostMapping("/page")
    public ResultVO<?> page(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsInfo esInfo) {
        return esInfoService.page(pageNum, pageSize, esInfo);
    }

    /**
     * 通过esCode删除es
     *
     * @param
     * @return
     */
    @PostMapping("/delete")
    public ResultVO<?> deleteOne(@RequestBody TblTktEsInfo esInfo) {
        String esCode = esInfo.getEsCode();
        return esInfoService.deleteOne(esCode);
    }

}
