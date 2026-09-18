package com.chinasofti.huateng.accsimulator;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AccEmployeeCardMapper {
    void merge(AccEmployeeCard card);

    AccEmployeeCard selectByCardNo(@Param("cardNo") String cardNo);

    List<AccEmployeeCard> selectAll();

    int deleteAll();
}
