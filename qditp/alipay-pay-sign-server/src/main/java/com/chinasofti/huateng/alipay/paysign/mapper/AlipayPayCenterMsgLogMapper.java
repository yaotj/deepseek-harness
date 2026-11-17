package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCenterMsgLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface AlipayPayCenterMsgLogMapper {

    int insert(AlipayPayCenterMsgLog record);


    List<AlipayPayCenterMsgLog> selectByOrderNo(@Param("orderNo") String orderNo);
}
