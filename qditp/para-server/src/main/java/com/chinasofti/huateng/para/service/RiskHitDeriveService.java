package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.model.para.RiskHitDeriveRespDTO;

/**
 * 风控命中批量派生（IT-086 方案 B）：直读交易表判命中、写 TBL_RISK_CONTROL_LOG。
 */
public interface RiskHitDeriveService {

    RiskHitDeriveRespDTO derive();
}
