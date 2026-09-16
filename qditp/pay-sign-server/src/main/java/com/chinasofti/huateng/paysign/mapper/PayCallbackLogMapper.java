package com.chinasofti.huateng.paysign.mapper;

import com.chinasofti.huateng.paysign.entity.PayCallbackLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PayCallbackLogMapper {
    int insert(PayCallbackLog record);

    /**
     * 统计同一商户订单号已收到的同类回调条数（含本次，调用点在 insert 之后）。
     *
     * <p>用于「重推只做一次」的硬限次判定：本表只追加不删除，且 insert 已脱离事务、
     * 立即提交，因此这个计数就是支付中心实际推送次数的可靠计数器。</p>
     */
    int countByMerchantOrderNo(@Param("merchantOrderNo") String merchantOrderNo,
                               @Param("callbackType") String callbackType);

    /**
     * 把该商户订单号最近一条同类回调标记为需人工处理。
     *
     * <p>调用点是「达到重推上限仍未处理成功」，此时已决定回 0000 让上游停推，
     * 必须留下可检索的痕迹，否则等于静默丢单。</p>
     */
    int markManualByMerchantOrderNo(@Param("merchantOrderNo") String merchantOrderNo,
                                    @Param("callbackType") String callbackType,
                                    @Param("handleMsg") String handleMsg);
}
