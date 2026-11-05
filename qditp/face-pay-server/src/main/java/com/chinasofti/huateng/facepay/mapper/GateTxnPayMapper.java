package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.GateTxnPay;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GateTxnPayMapper {

    List<GateTxnPay> selectByOrderNos(@Param("orderNos") List<String> orderNos);
}
