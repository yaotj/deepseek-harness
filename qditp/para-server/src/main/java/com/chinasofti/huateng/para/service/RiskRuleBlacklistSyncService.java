package com.chinasofti.huateng.para.service;

import com.chinasofti.huateng.model.para.RiskRuleBlacklistSyncRespDTO;

/**
 * 黑名单直拉规则组自动加黑：聚合风控命中记录，达规则阈值且未在黑名单的卡
 * 经 blacklist-server 幂等接口拉入黑名单（BLACK_SOURCE=01 系统自动）。
 */
public interface RiskRuleBlacklistSyncService {

    /**
     * 执行单轮扫描：按配置组名找组 → 组内规则窗口期聚合 → 逐卡查重 → 加黑。
     * 任何单卡失败不影响其余卡；组不存在时返回成功且计数全 0（环境未建组的空转语义）。
     */
    RiskRuleBlacklistSyncRespDTO sync();
}
