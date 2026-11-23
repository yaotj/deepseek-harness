package com.chinasofti.huateng.para.service.risk.hitderive;

import com.chinasofti.huateng.para.entity.risk.DerivedHit;
import com.chinasofti.huateng.para.entity.risk.RiskRule;
import com.chinasofti.huateng.para.entity.risk.StuckEntryEvent;
import com.chinasofti.huateng.para.entity.risk.TimeoutExitEvent;
import com.chinasofti.huateng.para.mapper.risk.RiskHitDeriveMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 000003 最大允许单边交易数 —— 批量派生（2026-11-19 用户裁定：两种都算）。
 * 超时出站（出站明细 OVERTIME_AMOUNT 大于 0，系统已确认单边）与
 * 滞留进站（CODE_STATUS=04 且进站超过 stuckHours 未出站）各派生一行命中。
 */
@Component
public class OnesideHitDeriveHandler extends AbstractRiskHitDeriveHandler {

    public static final String RULE_ID = "000003";

    private static final String KIND_TIMEOUT_EXIT = "超时出站";
    private static final String KIND_STUCK_ENTRY = "滞留进站";

    @Value("${risk.hit-derive.window-days:7}")
    private int windowDays;

    @Value("${risk.hit-derive.oneside-stuck-hours:24}")
    private int stuckHours;

    private final RiskHitDeriveMapper riskHitDeriveMapper;

    public OnesideHitDeriveHandler(RiskHitDeriveMapper riskHitDeriveMapper) {
        this.riskHitDeriveMapper = riskHitDeriveMapper;
    }

    @Override
    public String ruleId() {
        return RULE_ID;
    }

    @Override
    public List<DerivedHit> derive(RiskRule rule) {
        List<DerivedHit> hits = new ArrayList<>();
        for (TimeoutExitEvent event : riskHitDeriveMapper.selectTimeoutExits(RULE_ID, windowDays)) {
            LocalDateTime hitTime = parseDateTime14(event.getHandleDateTime());
            hits.add(new DerivedHit(KIND_TIMEOUT_EXIT, event.getCardId(), event.getTicketTransSeq(),
                    event.getDeviceId(), hitTime != null ? hitTime : LocalDateTime.now()));
        }
        for (StuckEntryEvent event : riskHitDeriveMapper.selectStuckEntries(RULE_ID, stuckHours)) {
            LocalDateTime hitTime = parseDateTime14(event.getGateInTime());
            hits.add(new DerivedHit(KIND_STUCK_ENTRY, event.getCardId(), null,
                    null, hitTime != null ? hitTime : LocalDateTime.now()));
        }
        return hits;
    }
}
