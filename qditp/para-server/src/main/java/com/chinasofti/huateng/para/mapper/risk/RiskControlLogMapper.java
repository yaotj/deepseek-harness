package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RiskControlLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** 风险控制命中审计记录的数据访问接口，只读。 */
public interface RiskControlLogMapper {
    /** 通过逻辑卡号、订单、规则及命中时间范围筛选记录。 */
    List<RiskControlLog> selectPage(@Param("cardId") String cardId, @Param("userOrderNo") String userOrderNo,
                                    @Param("ruleId") String ruleId, @Param("riskHitTimeBegin") String riskHitTimeBegin,
                                    @Param("riskHitTimeEnd") String riskHitTimeEnd);
}
