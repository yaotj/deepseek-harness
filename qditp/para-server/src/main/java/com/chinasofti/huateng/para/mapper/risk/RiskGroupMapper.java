package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
/** 风险组参数表的数据访问接口。 */
public interface RiskGroupMapper {
    /** 按名称模糊筛选风险组。 */
    List<RiskGroup> selectPage(@Param("groupName") String groupName);

    /** 查询规则编辑页的下拉选项。 */
    List<RiskGroup> selectOptions();

    /** 写入新风险组。 */
    int insert(RiskGroup record);

    /** 更新风险组基本信息。 */
    int update(RiskGroup record);

    /** 按主键删除风险组。 */
    int deleteById(@Param("groupId") Long groupId);
}
