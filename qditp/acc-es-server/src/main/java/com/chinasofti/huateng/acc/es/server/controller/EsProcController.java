package com.chinasofti.huateng.acc.es.server.controller;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsProc;
import com.chinasofti.huateng.acc.es.server.service.IEsProcService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * ES报告结果处理
 */
@RestController
@AllArgsConstructor
@RequestMapping("/proc")
public class EsProcController {

    private final IEsProcService esProcService;

    /**
     * 分页查寻
     * @param esProc
     * @return
     */
    @PostMapping("/page")
    public ResultVO<?> page(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsProc esProc) {
        return esProcService.selectPage(pageNum,pageSize,esProc);
    }
}
