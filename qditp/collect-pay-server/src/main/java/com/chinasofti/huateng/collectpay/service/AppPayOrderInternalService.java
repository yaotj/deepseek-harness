package com.chinasofti.huateng.collectpay.service;

import com.chinasofti.huateng.model.collectpay.AppPayOrderCloseReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRegisterReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderResultRespDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRespDTO;

/**
 * {@code TBL_TVM_APP_ORDER} / {@code TBL_TVM_ORDER_PAY_PRE} 的对内写入入口。
 *
 * <p><b>存在的原因是把跨域直写收回来</b>：这两张表的 owner 是本模块
 * （`docs/business/tvm-bom-pay.md`），但 2026-09-11 起 gate-txn-pay-server 的 IF8A-26 补款单
 * 为了复用本模块的收银台链路，直接在自己进程里 INSERT / UPDATE 这两张表 —— 那违反
 * `docs/domain/README.md` 的「热路径写入定 owner」判据：字段口径散落在两个模块，
 * 本模块改一列语义就可能静默打挂对方。2026-09-14 按用户要求收口成本接口。</p>
 *
 * <p><b>切换尚未发生（截至本接口入库时）</b>：gate-txn-pay-server 的
 * {@code mapper/AppPayOrderMapper.java} 与 {@code resources/mapper/AppPayOrderMapper.xml}
 * <b>仍在原地</b>，{@code SupplementOrderServiceImpl} 的私有 {@code registerAppPayOrder}
 * 也仍在用它直写两张表。也就是说**现在有两条写入路径并存**，本接口这一条暂时无人调用。
 * 完成切换的那一笔 MUST 同时删掉对方那个 mapper 与 XML、并改掉它的三处调用点与单测桩，
 * <b>NEVER</b> 在两条路径并存的状态下上线 —— 那样同一笔单据可能被两种口径各写一次。</p>
 *
 * <p><b>三条资损口径从对方的 mapper 注释原样搬来，改任何一条前 MUST 逐条复核</b>：</p>
 * <ul>
 *   <li>{@code requestPayInfo} 按 {@code TICKET_PRICE × TICKET_NUM} 算送去支付中心的金额
 *       （`AppOrderServiceImpl:152`），因此登记时 {@code TICKET_NUM} 固定 {@code 1}、
 *       {@code TICKET_PRICE} 写全额。<b>NEVER</b> 只写 {@code TOTALPRICE}。</li>
 *   <li>{@code RSV2} <b>MUST 非空</b>：{@code refundAppNotTakeTickets}（`TvmAppOrderMapper.xml`
 *       的 {@code selectByCondition}）会把「{@code PAY_STATUS='1'} + {@code RSV2} 为空 +
 *       昨天创建 + 主票表查不到票」当成购票未取票**全额退款**。补款单永远不会有票，
 *       {@code RSV2} 一空就是次日把收到的欠费退回去。</li>
 *   <li>前置单 {@code TRANS_TYPE} 决定 {@code payNotice} 的分派（`TvmOrderPreServiceImpl:120`）。
 *       补款单只能是 {@code 03}：只有它进 {@code appOrderService.payNotice}（只改订单行、不出票）。
 *       缺这一行前置单，支付中心异步回调对该单**完全失效**、状态永远停在 {@code '0'}。</li>
 * </ul>
 *
 * <p><b>本接口当前无鉴权</b>（用户 2026-09-14 裁决：与本模块 {@code /internal/recon} 的
 * 临时降级保持一致）。它是状态变更型接口，与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权」
 * 冲突，属**有意为之的临时降级**：任何网络可达方都能按订单号给他人建单或关掉他人的待支付单。
 * <b>上线前 MUST 补鉴权</b>，方式对齐现有验签实现，NEVER 自造签名逻辑。</p>
 */
public interface AppPayOrderInternalService {

    /**
     * 把一张外部单据登记成 APP 订单行 + 支付前置单行。
     *
     * <p><b>按 {@code orderNo} 幂等</b>：已登记过就直接返成功、不重复插入，
     * 因为调用方是「本地先落 PENDING、提交后再同步、失败留给扫表补偿」的 outbox 模式，
     * 同一笔会被重放。<b>NEVER</b> 改成「重复即报错」—— 那会让对方的补偿永远收不了口。</p>
     *
     * <p>两张表在**同一个本地事务**里写：只落其中一张的后果分别是
     * 「乘客看不到单」与「支付中心回调无处分派」，都需要人工介入，因此 MUST 同生同死。</p>
     */
    AppPayOrderRespDTO register(AppPayOrderRegisterReqDTO request);

    /**
     * 关闭仍待支付的订单行（带 {@code PAY_STATUS='0'} 白名单，影响 0 行也算成功）。
     */
    AppPayOrderRespDTO closeUnpaid(AppPayOrderCloseReqDTO request);

    /**
     * 按订单号回查支付结果，供调用方收敛自己的单据状态。
     *
     * <p>查不到时返回 {@code found=false} 而不是抛异常 —— 那是真实的业务事实，
     * 调用方据此落 ERROR 或补登记；抛异常只留给「本模块自己出错」。</p>
     */
    AppPayOrderResultRespDTO queryPayResult(String orderNo);
}
