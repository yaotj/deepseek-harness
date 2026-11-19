package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
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

    int insert(RiskControlLog record);
}
