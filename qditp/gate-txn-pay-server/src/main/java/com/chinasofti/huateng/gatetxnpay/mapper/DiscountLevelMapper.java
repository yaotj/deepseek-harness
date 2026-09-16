package com.chinasofti.huateng.gatetxnpay.mapper;

import com.chinasofti.huateng.gatetxnpay.entity.DiscountLevel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DiscountLevelMapper {
    DiscountLevel selectApplicable(@Param("discountType") String discountType,
                                   @Param("totalAmt") Integer totalAmt);
}
