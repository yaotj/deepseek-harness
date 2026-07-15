package com.chinasofti.huateng.acc.es.server.mapper;

import com.chinasofti.huateng.acc.es.server.model.TblStlAcctInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TblStlAcctInfoMapper {
    int deleteByPrimaryKey(String ticketLogicNo);

    int insert(TblStlAcctInfo record);

    int insertSelective(TblStlAcctInfo record);

    TblStlAcctInfo selectByPrimaryKey(String ticketLogicNo);

    int updateByPrimaryKeySelective(TblStlAcctInfo record);

    int updateByPrimaryKey(TblStlAcctInfo record);

    int deleteStat(TblStlAcctInfo tblStlAcctInfo);

    int delTheOldStat(TblStlAcctInfo tblStlAcctInfo);

    TblStlAcctInfo selectByCsn(@Param("csn") String csn);

}