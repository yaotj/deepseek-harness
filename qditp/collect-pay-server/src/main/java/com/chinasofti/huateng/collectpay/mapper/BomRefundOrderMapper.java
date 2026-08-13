package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.BomRefundOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * BOM退款订单Mapper接口。
 * 提供BOM退款订单的数据库操作方法。
 */
@Mapper
public interface BomRefundOrderMapper {

    /**
     * 根据退款单号查询退款订单信息。
     *
     * @param refundNo 退款单号
     * @return 退款订单实体对象，不存在返回null
     */
    BomRefundOrder selectByRefundNo(String refundNo);

    /**
     * 根据原支付订单号查询退款订单信息。
     *
     * @param payOrderNo 原支付订单号
     * @return 退款订单实体对象，不存在返回null
     */
    BomRefundOrder selectByPayOrderNo(String payOrderNo);

    /**
     * 插入新退款订单记录。
     *
     * @param refundOrder 退款订单实体对象
     * @return 影响的行数
     */
    int insert(BomRefundOrder refundOrder);

    /**
     * 根据退款单号更新退款订单信息。
     * 使用Map传参，支持动态更新字段。
     *
     * @param params 更新参数，必须包含refundNo字段
     * @return 影响的行数
     */
    int updateByRefundNo(Map<String, String> params);
}