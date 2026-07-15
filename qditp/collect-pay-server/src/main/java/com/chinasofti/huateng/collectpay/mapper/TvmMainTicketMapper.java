package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.TvmMainTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TvmMainTicketMapper {
    /**
     * 根据主键查询。
     */
    TvmMainTicket selectById(@Param("id") Long id);

    /**
     * 根据订单号查询。
     */
    TvmMainTicket selectByOrderNo(@Param("orderNo") String orderNo);


    String getTvmMainTicketSeq();
    /**
     * 插入出票主记录。
     */
    int insert(TvmMainTicket record);


}
