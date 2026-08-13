package com.chinasofti.huateng.collectpay.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 序列Mapper，用于生成订单号等业务单号。
 */
@Mapper
public interface OrderSeqMapper {

    /**
     * 获取 ORDER_NO_SEQ 下一个值。
     */
    @Select("SELECT ORDER_NO_SEQ.NEXTVAL FROM DUAL")
    Long nextval();
}
