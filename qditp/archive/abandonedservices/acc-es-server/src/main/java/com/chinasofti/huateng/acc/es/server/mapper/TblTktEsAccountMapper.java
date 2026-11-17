package com.chinasofti.huateng.acc.es.server.mapper;


import com.chinasofti.huateng.acc.es.server.model.TblTktEsAccount;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface TblTktEsAccountMapper {

    int deleteByPrimaryKey(String username);


    int insert(TblTktEsAccount record);


    int insertSelective(TblTktEsAccount record);


    TblTktEsAccount selectByPrimaryKey(String username);


    int updateByPrimaryKeySelective(TblTktEsAccount record);


    int updateByPrimaryKey(TblTktEsAccount record);


    Integer getEsUserTypeByUsernameAndPassword(TblTktEsAccount account);

    List<TblTktEsAccount> queryAccountByPage(TblTktEsAccount account);
}