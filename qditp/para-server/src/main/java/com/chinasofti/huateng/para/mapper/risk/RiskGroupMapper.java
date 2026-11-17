package com.chinasofti.huateng.para.mapper.risk;

import com.chinasofti.huateng.para.entity.risk.RiskGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RiskGroupMapper {
    List<RiskGroup> selectPage(@Param("groupName") String groupName);

    List<RiskGroup> selectOptions();

    int insert(RiskGroup record);

    int update(RiskGroup record);

    int deleteById(@Param("groupId") Long groupId);
}
