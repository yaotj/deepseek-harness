package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.BomNoCashOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * BOM非现金收款订单Mapper接口。
 * 提供BOM非现金收款订单的数据库操作方法。
 */
@Mapper
public interface BomNoCashOrderMapper {

    /**
     * 根据订单号查询订单信息。
     *
     * @param orderNo 订单号
     * @return 订单实体对象，不存在返回null
     */
    BomNoCashOrder selectByOrderNo(String orderNo);

    /**
     * 插入新订单。
     *
     * @param order 订单实体对象
     * @return 影响的行数
     */
    int insert(BomNoCashOrder order);

    /**
     * 根据订单号更新订单信息。
     * 使用Map传参，支持动态更新字段。
     *
     * @param params 更新参数，必须包含orderNo字段
     * @return 影响的行数
     */
    int updateByOrderNo(Map<String, String> params);
}