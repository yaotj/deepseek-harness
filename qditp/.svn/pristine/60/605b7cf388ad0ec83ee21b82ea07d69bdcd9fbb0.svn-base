package com.chinasofti.huateng.acc.security.server.service;

import com.chinasofti.huateng.acc.security.feign.domain.commontac.CpuTacParam;
import com.chinasofti.huateng.acc.security.feign.domain.commontac.SingleTicketTacParam;
import com.chinasofti.huateng.common.response.ResultVO;

/**
 * @author houkepan
 * @date 2020/5/6 11:55
 */
public interface CommonTacService {

    /**
     * @return
     */
    ResultVO<Boolean> ulTacVerify(SingleTicketTacParam param) throws InterruptedException;


    ResultVO<Boolean> cpuTacVerify(CpuTacParam param) throws InterruptedException;
}
