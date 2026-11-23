package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RepeatExitDay;
import com.chinasofti.huateng.para.entity.risk.StuckEntryEvent;
import com.chinasofti.huateng.para.entity.risk.TimeoutExitEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 风控命中批量派生查询（IT-086 方案 B）。三条查询均自带去重反连接：
 * 同一事件多轮扫描不会重复落 TBL_RISK_CONTROL_LOG。
 */
@Mapper
public interface RiskHitDeriveMapper {

    /**
     * 窗口内超时出站明细（出站 TRX_TYPE 02/03 且 OVERTIME_AMOUNT 大于 0）。
     * 去重键：(RULE_ID, CARD_ID, USER_ORDER_NO=本次出站 TICKET_TRANS_SEQ)。
     */
    List<TimeoutExitEvent> selectTimeoutExits(@Param("ruleId") String ruleId,
                                              @Param("windowDays") int windowDays);

    /**
     * 当前滞留进站（CODE_STATUS=04 且 GATE_IN_TIME 早于 sysdate - stuckHours）。
     * 去重键：滞留窗口内同卡已有 USER_ORDER_NO 为空的滞留命中即跳过（一个滞留 episode 只落一行）。
     */
    List<StuckEntryEvent> selectStuckEntries(@Param("ruleId") String ruleId,
                                             @Param("stuckHours") int stuckHours);

    /**
     * 窗口内同卡同自然日出站明细数达阈值的天（000002 出站规则）。
     * 去重键：(RULE_ID, CARD_ID, TRUNC(RISK_HIT_TIME)=该自然日)，一天只落一行。
     */
    List<RepeatExitDay> selectRepeatExitDays(@Param("ruleId") String ruleId,
                                             @Param("threshold") int threshold,
                                             @Param("windowDays") int windowDays);
}
