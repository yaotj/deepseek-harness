package com.chinasofti.huateng.para.service.risk.hitderive;

import com.chinasofti.huateng.para.entity.risk.DerivedHit;
import com.chinasofti.huateng.para.entity.risk.RepeatExitDay;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.chinasofti.huateng.para.mapper.risk.RiskHitDeriveMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 000002 出站规则 —— 批量派生（2026-11-19 用户裁定：同一自然日出站明细数 ≥ 阈值记一次命中）。
 * 出站明细 TRX_TYPE 02/03；阈值取规则自身 RISK_LIMIT_VALUE；窗口独立（默认 1 天），
 * NEVER 沿用 IT-083 加黑聚合的全局 7 天窗口（正常乘客 7 天出站十余次，会全量误伤）。
 */
@Component
public class RepeatExitHitDeriveHandler extends AbstractRiskHitDeriveHandler {

    public static final String RULE_ID = "000002";

    private static final String KIND_REPEAT_EXIT_DAY = "自然日重复出站";

    @Value("${risk.hit-derive.repeat-exit-window-days:1}")
    private int windowDays;

    private final RiskHitDeriveMapper riskHitDeriveMapper;

    public RepeatExitHitDeriveHandler(RiskHitDeriveMapper riskHitDeriveMapper) {
        this.riskHitDeriveMapper = riskHitDeriveMapper;
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public List<DerivedHit> derive(RiskRule rule) {
        int threshold = rule.getRiskLimitValue() == null ? 1 : rule.getRiskLimitValue().intValue();
        List<DerivedHit> hits = new ArrayList<>();
        for (RepeatExitDay day : riskHitDeriveMapper.selectRepeatExitDays(RULE_ID, threshold, windowDays)) {
            LocalDateTime hitTime = parseDateTime14(day.getLastExitTime());
            if (hitTime == null) {
                hitTime = parseDate8(day.getTxnDate());
            }
            hits.add(new DerivedHit(KIND_REPEAT_EXIT_DAY, day.getCardId(), day.getLastExitSeq(),
                    null, hitTime != null ? hitTime : LocalDateTime.now()));
        }
        return hits;
    }
}
