package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipayPayCallbackLogMapper {

    int insert(AlipayPayCallbackLog callbackLog);

    /** 统计同一订单同一类型的回调累计推送次数（含本次）。 */
    int countByOrderNo(@Param("orderNo") String orderNo, @Param("callbackType") String callbackType);

    int updateHandleResult(@Param("callbackSeq") String callbackSeq,
                           @Param("handleStatus") String handleStatus,
                           @Param("handleMsg") String handleMsg);

    /** 把该订单最后一条回调标成需人工处理。 */
    int markManualByOrderNo(@Param("orderNo") String orderNo,
                            @Param("callbackType") String callbackType,
                            @Param("handleMsg") String handleMsg);
}
