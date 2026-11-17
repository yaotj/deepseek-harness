package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PayCallbackLogMapper {
    int insert(PayCallbackLog record);

    int countByMerchantOrderNo(@Param("merchantOrderNo") String merchantOrderNo,
                               @Param("callbackType") String callbackType);

    int markManualByMerchantOrderNo(@Param("merchantOrderNo") String merchantOrderNo,
                                    @Param("callbackType") String callbackType,
                                    @Param("handleMsg") String handleMsg);


    List<PayCallbackLog> selectLatestPayDiscountInfoByOrderNos(@Param("orderNos") List<String> orderNos,
                                                               @Param("callbackType") String callbackType);
}
