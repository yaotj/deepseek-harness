package com.chinasofti.huateng.collectpay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

import java.time.LocalDateTime;

/** BOM充值结果通知Mapper。 */
@Mapper
public interface BomTopupResultMapper{

    /**
     * 插入充值结果通知。
     *
     * @param params 参数Map（orderNo/ticketLogicNum/ticketPhysicsNum/transDate/transAmount/afterAmount/topupStatus/transType）
     */
    void insertNotiy(Map<String, String> params);
}
