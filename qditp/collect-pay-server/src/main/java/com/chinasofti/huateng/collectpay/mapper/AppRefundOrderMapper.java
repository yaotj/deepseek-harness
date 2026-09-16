package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.AppRefundOrder;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

/**
 * BOM退款订单Mapper接口。
 * 提供BOM退款订单的数据库操作方法。
 */
@Mapper
public interface AppRefundOrderMapper {

    /**
     * 根据退款单号查询退款订单信息。
     *
     * @param refundNo 退款单号
     * @return 退款订单实体对象，不存在返回null
     */
    AppRefundOrder selectByRefundNo(String refundNo);

    /**
     * 根据原支付订单号查询退款订单信息。
     *
     * @param payOrderNo 原支付订单号
     * @return 退款订单实体对象，不存在返回null
     */
    AppRefundOrder selectByPayOrderNo(String payOrderNo);

    /**
     * 插入新退款订单记录。
     *
     * @param refundOrder 退款订单实体对象
     * @return 影响的行数
     */
    int insert(AppRefundOrder refundOrder);

    /**
     * 根据退款单号更新退款订单信息。
     * 使用Map传参，支持动态更新字段。
     *
     * @param params 更新参数，必须包含refundNo字段
     * @return 影响的行数
     */
    int updateByRefundNo(Map<String, String> params);

    /**
     * 汇总某笔支付订单**已退款成功**的金额合计（单位：分）。
     *
     * <p>只统计 {@code REFUND_STATUS='1'} 的行 —— 失败（2）与退款中（0）都不占额度。
     * 唯一用途是算「可退余额 = 支付金额 - 本方法返回值」，给指定金额退款做超退闸门。</p>
     *
     * <p>⚠️ {@code REFUND_AMOUNT} 是 {@code VARCHAR2}，库里存在非数字与量级写错的历史脏值
     * （2026-09-14 实测 BOM 侧有 {@code '0'}、也有付 1 分却记 300 的行），因此 SQL 内按
     * {@code REGEXP_LIKE} 只取纯数字行。**NEVER 去掉那个过滤** —— {@code TO_NUMBER} 一旦
     * 撞到脏值就抛 {@code ORA-01722}，整个退款请求会连「留证据」的落库一起失败。</p>
     *
     * @param payOrderNo 原支付订单号
     * @return 已成功退款金额合计（分），无成功退款时返回 0
     */
    long sumSuccessRefundAmount(String payOrderNo);
}
