package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaySignInfoMapper {
    PaySignInfo selectBySeq(
                            @Param("requestSignSeq") String requestSignSeq,
                            @Param("paymentVendor") String paymentVendor);

    // 新增：根据用户ID和支付渠道查询
    PaySignInfo selectByUserAndVendor(
                            @Param("thirdUserId") String thirdUserId,
                            @Param("paymentVendor") String paymentVendor);

    // 新增：纯INSERT
    int insert(PaySignInfo record);

    // 新增：根据用户ID和支付渠道删除
    int deleteByUserAndVendor(
                            @Param("thirdUserId") String thirdUserId,
                            @Param("paymentVendor") String paymentVendor);

    // 废弃：原有的 upsert/MERGE
    // int upsert(PaySignInfo record);
}
