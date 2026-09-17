package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PayCallbackLogMapper {
    int insert(PayCallbackLog record);

    /** 统计同一商户订单号已收到的同类回调条数（含本次，调用点在 insert 之后）。 */
    int countByMerchantOrderNo(@Param("merchantOrderNo") String merchantOrderNo,
                               @Param("callbackType") String callbackType);

    /** 把该商户订单号最近一条同类回调标记为需人工处理。 */
    int markManualByMerchantOrderNo(@Param("merchantOrderNo") String merchantOrderNo,
                                    @Param("callbackType") String callbackType,
                                    @Param("handleMsg") String handleMsg);
}
