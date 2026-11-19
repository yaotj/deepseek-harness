package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RiskGroupMapper {
    List<RiskGroup> selectPage(@Param("groupName") String groupName);

    List<RiskGroup> selectOptions();

    /** 按组名精确查询（自动加黑任务按组名识别风险组，名称区分大小写走 BINARY 等价语义由调用方保证）。 */
    RiskGroup selectByName(@Param("groupName") String groupName);

    int insert(RiskGroup record);

    int update(RiskGroup record);

    int deleteById(@Param("groupId") Long groupId);
}
