package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.TvmTopupOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface TvmTopupOrderMapper {
    /**
     * 根据订单号查询订单。
     */
    TvmTopupOrder selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 插入订单。
     */
    int insert(TvmTopupOrder record);

    /**
     * 根据orderNo动态更新订单。
     */
    int updateByOrderNo(Map<String, String> params);

    int insertNotiy(Map<String, String> params);

    int insertFailtNotiy(Map<String, String> params);
}
