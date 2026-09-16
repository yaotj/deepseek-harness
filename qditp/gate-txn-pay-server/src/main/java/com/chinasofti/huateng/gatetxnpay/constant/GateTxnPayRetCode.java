package com.chinasofti.huateng.gatetxnpay.constant;

/**
 * 本模块对上游（fep-dev-server / pay-sign-server / 运营后台）应答的 {@code retCode}。
 *
 * <p>这五个值此前在 8 个类里各写一份裸字面量（`FareDataGateway` / `FareCalculator` /
 * `PaySignInitiator` / `SupplementConvergeService` / `GateTxnPayServiceImpl` /
 * `SupplementOrderServiceImpl` / `GateTxnPayQueryServiceImpl` / `OfflineMetroTransferClient`），
 * 其中四个类还各自定义了一个同名私有常量 {@code RET_SUCCESS="0000"}。</p>
 *
 * <p><b>它们是对外契约，NEVER 改值</b>：上游按这几个码分支处理，改一个字面量等于
 * 悄悄改协议。本类的作用只是让「同一个码只有一处定义」，<b>不是</b>给它们改名或加新码的入口。</p>
 *
 * <p>注意 {@link #SUCCESS} 这个 {@code "0000"} <b>同时用于两个方向</b>：本模块**应答**上游时用它表示成功，
 * 判断**下游**（pay-sign / 支付宝 / 票价 / 公交换乘）的答复是否成功时用的也是它 —— 本项目全链路
 * 共用同一套码表，所以一处定义是正确的。<b>但如果哪天某个下游改用别的成功码，
 * MUST 为那个下游单独加一个常量、NEVER 改本常量的值</b>：改这里会同时改掉我方对上游的应答。</p>
 */
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
