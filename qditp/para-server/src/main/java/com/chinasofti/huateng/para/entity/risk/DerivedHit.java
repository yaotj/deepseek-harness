package com.chinasofti.huateng.para.entity.risk;

import java.time.LocalDateTime;

/**
 * 批量派生的一条候选命中（IT-086 方案 B）。
 * 由 RiskHitDeriveHandler 产出，编排层负责补规则快照并落 TBL_RISK_CONTROL_LOG。
 * 去重在派生查询内以反连接完成（按各口径自身键），同一事件重复扫描不会重复落行。
 */
public class DerivedHit {

    /** 口径子类，写日志与明细用（如 超时出站 / 滞留进站 / 自然日重复出站）。 */
    private final String kind;

    private final String cardId;

    /** 展示用源单号（滞留进站这类无单号事件为 null）。 */
    private final String userOrderNo;

    private final String deviceId;

    private final LocalDateTime riskHitTime;

    public DerivedHit(String kind, String cardId, String userOrderNo, String deviceId, LocalDateTime riskHitTime) {
        this.kind = kind;
        this.cardId = cardId;
        this.userOrderNo = userOrderNo;
        this.deviceId = deviceId;
        this.riskHitTime = riskHitTime;
    }

    public String getKind() {
        return kind;
    }

    public String getCardId() {
        return cardId;
    }

    public String getUserOrderNo() {
        return userOrderNo;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public LocalDateTime getRiskHitTime() {
        return riskHitTime;
    }
}
