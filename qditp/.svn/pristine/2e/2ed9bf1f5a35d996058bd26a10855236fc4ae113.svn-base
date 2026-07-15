package com.chinasofti.huateng.acc.security.server.controller;

import com.chinasofti.huateng.acc.security.feign.domain.commontac.CpuTacParam;
import com.chinasofti.huateng.acc.security.feign.domain.commontac.SingleTicketTacParam;
import com.chinasofti.huateng.acc.security.server.service.CommonTacService;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * 公共TAC计算Controller
 */
@Validated
@RestController
public class CommonTacController {

    @Autowired
    private CommonTacService commonTacService;

    /**
     * @Description: (单程票tac验证方法)
     */
    @PostMapping("/singleticket/check")
    public ResultVO<Boolean> verifyUlTac(@RequestBody @Valid SingleTicketTacParam param) throws InterruptedException {
        return commonTacService.ulTacVerify(param);
    }

    /**
     * @Description: (CPU tac验证方法)
     */
    @PostMapping("/cpu/check")
    public ResultVO<Boolean> verifyCpuTac(@RequestBody @Valid CpuTacParam param) throws InterruptedException {
        return commonTacService.cpuTacVerify(param);
    }

}

