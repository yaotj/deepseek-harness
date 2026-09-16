package com.chinasofti.huateng.alipay.paysign.mapper;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AlipayPayCallbackLogMapper {

    int insert(AlipayPayCallbackLog callbackLog);

    /**
     * 统计同一订单同一类型的回调累计推送次数（含本次）。
     *
     * <p>计数点 MUST 在 insert 之后：{@code handlePayNotify} 无事务，insert 已自动提交，
     * 这个 COUNT 才等于支付中心实际推送次数。</p>
     */
    int countByOrderNo(@Param("orderNo") String orderNo, @Param("callbackType") String callbackType);

    int updateHandleResult(@Param("callbackSeq") String callbackSeq,
                           @Param("handleStatus") String handleStatus,
                           @Param("handleMsg") String handleMsg);

    /**
     * 把该订单最后一条回调标成需人工处理。
     *
     * <p>达到重推上限仍未处理成功时调用：那一刻我方主动回 0000 让支付中心停推，
     * 代价是真实失败不再被上游重试，因此 <b>MUST 留痕</b>，否则等于静默丢单。</p>
     */
    int markManualByOrderNo(@Param("orderNo") String orderNo,
                            @Param("callbackType") String callbackType,
                            @Param("handleMsg") String handleMsg);
}
