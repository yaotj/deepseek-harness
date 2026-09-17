package com.chinasofti.huateng.facepay.mapper;

import org.apache.ibatis.annotations.Mapper;

/** 序列取号。 */
@Mapper
public interface F2fSequenceMapper {

    /** 取订单号序列段。 */
    Long nextOrderNoSeq();
}
