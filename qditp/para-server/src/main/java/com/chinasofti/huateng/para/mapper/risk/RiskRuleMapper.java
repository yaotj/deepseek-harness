package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RiskRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RiskRuleMapper {
    List<RiskRule> selectPage(@Param("ruleId") String ruleId, @Param("ruleName") String ruleName,
                              @Param("groupId") Long groupId);

    int insert(RiskRule record);

    int update(RiskRule record);

    int deleteById(@Param("ruleId") String ruleId);

    int countByGroupId(@Param("groupId") Long groupId);
}
