 package com.chinasofti.huateng.collectpay.mapper;

 import com.chinasofti.huateng.collectpay.entity.TvmPayOrder;
 import com.chinasofti.huateng.collectpay.entity.TvmPayPreOrder;
 import org.apache.ibatis.annotations.Mapper;
 import org.apache.ibatis.annotations.Param;

 import java.util.Map;

 @Mapper
 public interface TvmOrderPreMapper {
     /**
      * 根据订单号查询订单。
      */
     TvmPayPreOrder selectByOrderNo(@Param("orderNo") String orderNo);

     int insert(Map<String,Object> map);

 }