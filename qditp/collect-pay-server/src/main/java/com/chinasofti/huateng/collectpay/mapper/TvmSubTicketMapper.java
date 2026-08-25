package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.SubTicket;
import com.chinasofti.huateng.collectpay.entity.TvmSubTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TvmSubTicketMapper {
    /**
     * 根据主表ID查询明细列表。
     */
    List<TvmSubTicket> selectByMainTicketId(@Param("mainTicketId") Long mainTicketId);

    /**
     * 插入出票明细记录。
     */
    int insert(TvmSubTicket record);

    /**
     * 批量插入出票明细记录。
     */
    int batchInsert(@Param("list") List<TvmSubTicket> list);


    TvmSubTicket selectByCondition(Map<String,String> condition);

    // 这用TvmSubTicket类接收，由于TvmSubTicket和BomSubTicket的字段一样
    SubTicket selectSubTickettByCondition(Map<String,String> condition);

    int updateByTicketLogicNum(Map<String,String> map);
}
