package com.chinasofti.huateng.para.service.risk.hitderive;

import com.chinasofti.huateng.para.entity.risk.DerivedHit;
import com.chinasofti.huateng.para.entity.risk.RiskRule;

import java.util.List;

/**
 * 单条风控规则的批量派生处理器（IT-086 方案 B）。
 * 一个规则一个实现，Spring Bean 按 ruleId() 注册进编排 Map；
 * 新增直拉规则 = 新增实现，编排层零改动。
 */
public interface RiskHitDeriveHandler {

    /** 处理的规则编号（TBL_RISK_RULE.RULE_ID）。 */
    String ruleId();

    /**
     * 扫描交易表派生候选命中。实现 MUST 在查询内完成去重反连接，
     * 保证同一事件多轮扫描不重复落行；返回可直接落库的候选。
     */
    List<DerivedHit> derive(RiskRule rule);
}
