package com.chinasofti.huateng.gatetxnpay.constant;

/** 本模块对上游（fep-dev-server / pay-sign-server / 运营后台）应答的 {@code retCode}。 */
public final class GateTxnPayRetCode {
    /** 成功。 */
    public static final String SUCCESS = "0000";
    /** 入参非法 / 状态不允许本次操作（业务拒绝，重推无用）。 */
    public static final String INVALID_PARAM = "8001";
    /** 订单入库失败，含离线码待重算落痕（对上游语义是「闸机照常放行、ITP 侧不认账」）。 */
    public static final String ORDER_PERSIST_FAILED = "8002";
    /** 状态机拒绝：当前状态不允许收敛为目标状态。 */
    public static final String STATUS_REJECT = "8003";
    /** 查询类接口的通用失败码（只用于 {@code GateTxnPayQueryService}）。 */
    public static final String QUERY_FAILED = "9002";

    private GateTxnPayRetCode() {
    }
}
