package com.chinasofti.huateng.collectpay.utils;

import com.chinasofti.huateng.collectpay.constant.ProductType;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 订单号生成工具类。 */
public class OrderNoUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 生成订单号。
     *
     * @param productType 产品类型
     * @param seq         序列值，0-9999
     * @return 20位订单号
     */
    public static String generateOrderNo(ProductType productType, long seq) {
        String seqStr = StringUtils.leftPad(String.valueOf(seq), 4, "0");
        return productType.getCode() + LocalDateTime.now().format(DATE_FORMATTER) + seqStr;
    }

    /**
     * 生成退款单号。
     *
     * @param seq 序列值，0-9999
     * @return 20位退款单号
     */
    public static String generateRefundNo(long seq) {
        String seqStr = StringUtils.leftPad(String.valueOf(seq), 4, "0");
        return "RF" + LocalDateTime.now().format(DATE_FORMATTER) + seqStr;
    }
}
