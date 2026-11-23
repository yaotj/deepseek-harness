package com.chinasofti.huateng.para.service.risk.hitderive;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 派生处理器公共支撑：交易表时间列为 VARCHAR2(14)（yyyyMMddHHmmss）与 VARCHAR2(8)（yyyyMMdd），
 * 落库前转 LocalDateTime。包内私有，NEVER 升级成全局工具类。
 */
abstract class AbstractRiskHitDeriveHandler implements RiskHitDeriveHandler {

    private static final DateTimeFormatter DATE_TIME14 = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DATE8 = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 解析 yyyyMMddHHmmss；空串 / null / 非法值返回 null（该行仍派生，命中时间退化到当前）。 */
    protected LocalDateTime parseDateTime14(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME14);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 解析 yyyyMMdd（RepeatExitDay.lastExitTime 等仅到日的值补零点后）。 */
    protected LocalDateTime parseDate8(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim() + "000000", DATE_TIME14);
        } catch (RuntimeException e) {
            return null;
        }
    }

    protected DateTimeFormatter date8Formatter() {
        return DATE8;
    }
}
