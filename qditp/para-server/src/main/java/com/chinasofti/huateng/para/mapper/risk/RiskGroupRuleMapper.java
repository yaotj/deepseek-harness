package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RiskGroupRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RiskGroupRuleMapper {
    List<String> selectRuleIdsByGroupId(@Param("groupId") Long groupId);

    List<Long> selectGroupIdsByRuleId(@Param("ruleId") String ruleId);

    int insert(@Param("groupId") Long groupId, @Param("ruleId") String ruleId);

    int deleteByGroupId(@Param("groupId") Long groupId);

    int deleteByRuleId(@Param("ruleId") String ruleId);

    int deleteByGroupAndRule(@Param("groupId") Long groupId, @Param("ruleId") String ruleId);

    int countByGroupId(@Param("groupId") Long groupId);
}
