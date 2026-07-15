package com.chinasofti.huateng.acc.es.server.mapper;

import com.chinasofti.huateng.acc.es.server.model.TblStlTicketInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TblStlTicketInfoMapper {
    int deleteByPrimaryKey(String ticketLogicNo);

    int insert(TblStlTicketInfo record);

    int insertSelective(TblStlTicketInfo record);

    TblStlTicketInfo selectByPrimaryKey(String ticketLogicNo);

    int updateByPrimaryKeySelective(TblStlTicketInfo record);

    int updateByPrimaryKey(TblStlTicketInfo record);

    int deleteStat(TblStlTicketInfo tblStlTicketInfo);

    int delTheOldStat(TblStlTicketInfo tblStlTicketInfo);
}