package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.BomMainTicket;
import com.chinasofti.huateng.collectpay.entity.TvmMainTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BomMainTicketMapper {
    /** 根据主键查询。 */
    BomMainTicket selectById(@Param("id") Long id);

    /** 根据订单号查询。 */
    BomMainTicket selectByOrderNo(@Param("orderNo") String orderNo);


    String getBomMainTicketSeq();
    /** 插入出票主记录。 */
    int insert(BomMainTicket record);


}
