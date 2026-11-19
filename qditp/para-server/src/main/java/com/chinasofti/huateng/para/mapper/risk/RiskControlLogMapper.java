package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
import com.chinasofti.huateng.para.entity.risk.RiskHitAggregation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RiskControlLogMapper {
    List<RiskControlLog> selectPage(@Param("cardId") String cardId, @Param("userOrderNo") String userOrderNo,
                                    @Param("ruleId") String ruleId, @Param("riskStatus") String riskStatus,
                                    @Param("riskHitTimeBegin") String riskHitTimeBegin,
                                    @Param("riskHitTimeEnd") String riskHitTimeEnd);

    List<RiskControlLog> selectForExport(@Param("cardId") String cardId, @Param("userOrderNo") String userOrderNo,
                                         @Param("ruleId") String ruleId, @Param("riskStatus") String riskStatus,
                                         @Param("riskHitTimeBegin") String riskHitTimeBegin,
                                         @Param("riskHitTimeEnd") String riskHitTimeEnd,
                                         @Param("limit") Integer limit);

    /**
     * 窗口期按「卡+规则」聚合命中次数，只回传达到该规则自身阈值（RISK_LIMIT_VALUE）的候选。
     * 供黑名单直拉规则组自动加黑任务使用；CARD_ID 为空的记录无拉黑对象，直接排除。
     */
    List<RiskHitAggregation> selectHitAggregationForBlacklist(@Param("groupId") Long groupId,
                                                              @Param("windowDays") Integer windowDays);

    int insert(RiskControlLog record);
}
