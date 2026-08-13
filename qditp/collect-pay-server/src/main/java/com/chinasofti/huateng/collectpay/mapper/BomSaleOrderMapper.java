package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.BomNoCashOrder;
import com.chinasofti.huateng.collectpay.entity.BomSaleOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * BOM非现金收款订单Mapper接口。
 * 提供BOM非现金收款订单的数据库操作方法。
 */
@Mapper
public interface BomSaleOrderMapper {

    BomSaleOrder selectByOrderNo(String orderNo);


    int insertBomSaleTicketInfo(Map<String,String> map);


}