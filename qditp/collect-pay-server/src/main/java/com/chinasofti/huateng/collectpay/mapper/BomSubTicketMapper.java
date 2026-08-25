package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.BomSubTicket;
import com.chinasofti.huateng.collectpay.entity.TvmSubTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BomSubTicketMapper {
    /**
     * 根据主表ID查询明细列表。
     */
    List<BomSubTicket> selectByMainTicketId(@Param("mainTicketId") Long mainTicketId);

    /**
     * 插入出票明细记录。
     */
    int insert(BomSubTicket record);

    /**
     * 批量插入出票明细记录。
     */
    int batchInsert(@Param("list") List<BomSubTicket> list);

    int updateByTicketLogicNum(Map<String,String> map);
}
