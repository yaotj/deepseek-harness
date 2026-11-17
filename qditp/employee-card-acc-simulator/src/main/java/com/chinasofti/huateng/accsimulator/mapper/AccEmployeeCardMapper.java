package com.chinasofti.huateng.accsimulator.mapper;

import com.chinasofti.huateng.accsimulator.entity.AccEmployeeCard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface AccEmployeeCardMapper {


    void merge(AccEmployeeCard card);


    List<AccEmployeeCard> selectPage(@Param("cardNo") String cardNo,
                                     @Param("employeeName") String employeeName,
                                     @Param("cardStatus") Integer cardStatus);


    AccEmployeeCard selectByCardNo(@Param("cardNo") String cardNo);


    int updateStatus(@Param("cardNo") String cardNo, @Param("cardStatus") Integer cardStatus);


    int deleteAll();
}
