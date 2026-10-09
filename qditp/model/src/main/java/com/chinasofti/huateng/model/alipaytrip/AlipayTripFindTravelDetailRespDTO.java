package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 支付宝出行-查询乘车记录详情响应参数。
 *
 * <p><b>应答形态：顶层只有 {@code retCode} / {@code retMsg} + 一个 {@code data} 对象</b>
 * （2026-09-20 按支付宝侧实测要求改回，ADR-D150）。20 个业务字段全在
 * {@link AlipayTripTravelDetailDTO} 里，<b>NEVER 再把它们平铺回顶层</b> —— 扁平结构是 ADR-D148
 * 按 R6 §3.72 表148 推出的口径，已被对接方的实测要求取代（外部契约以对方实际解析行为为准，
 * 与「支付中心网关字段名 MUST 实测」同一条判据）。
 *
 * <p>失败分支（参数非法 / 订单不存在 / 系统异常）**只填 {@code retCode} / {@code retMsg}、
 * {@code data} 留 null**，NEVER 为了「字段齐全」塞一个空对象。
 */
public class AlipayTripFindTravelDetailRespDTO extends CommonResult {

    /**
     * 乘车记录详情业务体；查询失败时为 {@code null}。
     */
    private AlipayTripTravelDetailDTO data;

    public AlipayTripTravelDetailDTO getData() {
        return data;
    }

    public void setData(AlipayTripTravelDetailDTO data) {
        this.data = data;
    }
}
