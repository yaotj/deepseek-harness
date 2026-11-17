package com.chinasofti.huateng.acc.es.server.mapper;


import com.chinasofti.huateng.acc.es.server.model.TblStlPersonInfo;
import org.apache.ibatis.annotations.Mapper;


@Mapper
public interface TblStlPersonInfoMapper {

    int deleteByPrimaryKey(String ticketId);


    int insert(TblStlPersonInfo record);


    int insertSelective(TblStlPersonInfo record);


    TblStlPersonInfo selectByPrimaryKey(String ticketId);


    int updateByPrimaryKeySelective(TblStlPersonInfo record);


    int updateByPrimaryKeyWithBLOBs(TblStlPersonInfo record);


    int updateByPrimaryKey(TblStlPersonInfo record);
}