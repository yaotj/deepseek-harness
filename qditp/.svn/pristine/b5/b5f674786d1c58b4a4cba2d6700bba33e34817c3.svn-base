 package com.chinasofti.huateng.collectpay.mapper;

 import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
 import org.apache.ibatis.annotations.Mapper;
 import org.apache.ibatis.annotations.Param;

 import java.util.Map;

@Mapper
public interface TvmOrderMapper {
    /**
     * 根据订单号查询订单。
     */
    TvmPayOrder selectByOrderNo(@Param("orderNo") String orderNo);

    TvmPayOrder selectByPayCenterOrderNo(@Param("payCenterOrderNo") String payCenterOrderNo);

    /**
     * 插入订单。
     */
    int insert(TvmPayOrder record);

    int insertTvmOrderPre(Map<String, Object> params);

    /**
     * 根据orderNo动态更新订单。
     */
    int updateByOrderNo(Map<String, String> params);


}