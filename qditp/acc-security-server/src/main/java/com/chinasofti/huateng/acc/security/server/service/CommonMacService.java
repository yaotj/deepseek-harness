package com.chinasofti.huateng.acc.security.server.service;

import com.chinasofti.huateng.acc.security.feign.domain.commonmac.InvestMac1Param;
import com.chinasofti.huateng.acc.security.feign.domain.commonmac.InvestMac2Param;
import com.chinasofti.huateng.common.response.ResultVO;

/**
 * @author houkepan
 * @date 2020/5/6 11:55
 */
public interface CommonMacService {

    /**
     * @return
     */
    ResultVO<Boolean> verifyMac1(InvestMac1Param param) throws InterruptedException;


    ResultVO<String> getMac2(InvestMac2Param param) throws InterruptedException;
}
