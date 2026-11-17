package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipayPayCallbackLogMapper {

    int insert(AlipayPayCallbackLog callbackLog);

    int countByOrderNo(@Param("orderNo") String orderNo, @Param("callbackType") String callbackType);

    int updateHandleResult(@Param("callbackSeq") String callbackSeq,
                           @Param("handleStatus") String handleStatus,
                           @Param("handleMsg") String handleMsg);

    int markManualByOrderNo(@Param("orderNo") String orderNo,
                            @Param("callbackType") String callbackType,
                            @Param("handleMsg") String handleMsg);
}
