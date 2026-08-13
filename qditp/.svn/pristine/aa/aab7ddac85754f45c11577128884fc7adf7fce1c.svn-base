package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RiskRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** 风险规则参数表的数据访问接口。 */
public interface RiskRuleMapper {
    /** 关联风险组名称后分页查询规则。 */
    List<RiskRule> selectPage(@Param("ruleId") String ruleId, @Param("ruleName") String ruleName,
                              @Param("groupId") Long groupId);

    /** 新增规则，规则编号为业务主键。 */
    int insert(RiskRule record);

    /** 更新除规则编号外的规则属性。 */
    int update(RiskRule record);

    /** 删除风险规则配置。 */
    int deleteById(@Param("ruleId") String ruleId);

    /** 统计风险组的引用规则数，用于删除保护。 */
    int countByGroupId(@Param("groupId") Long groupId);
}
