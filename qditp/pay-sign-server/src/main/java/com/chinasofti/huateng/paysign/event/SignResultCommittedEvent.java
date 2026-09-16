package com.chinasofti.huateng.paysign.event;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;

/**
 * 签约成功结果已落库并提交。由 {@code PaySignWorkflow.receiveSignResult} 在事务内发布，
 * 唯一监听方是同包的 {@link SignResultCommittedListener}（{@code AFTER_COMMIT}）。
 *
 * <h2>为什么要有这个事件</h2>
 * <p>原实现在 {@code @Transactional} 方法内直接调 {@code appNotifyService.asyncNotifySignResult}，
 * 违反 AGENTS.md §5.2「{@code @Transactional} 方法内 NEVER 提交异步通知任务」——事务若在
 * 提交阶段失败回滚，APP 已经收到「签约成功」而本地 {@code APP_PAY_SIGN_INFO} 并没有这行。
 * 改为发布事件后，投递物理上位于 {@code afterCommit}，回滚则事件根本不投递。</p>
 *
 * <h2>字段取舍</h2>
 * <p>只带 {@code requestSignSeq} + {@code paymentVendor} 两个键与**入向回调报文本身**，
 * <b>NEVER 把 PaySignRequest / PaySignInfo 实体塞进来</b>：实体在事务内可能还是脏快照，
 * 监听器 MUST 自己按键回查已提交的数据。</p>
 *
 * <p>{@code callback} 是支付平台回调的原始入参，已定型且不可变，监听器无法回查重建
 * （{@code signResult} 等字段只存在于这次请求里），因此必须随事件传递。</p>
 *
 * <p><b>本事件是进程内、非持久的</b>：JVM 崩溃时它会消失。可靠性不依赖它——
 * 流水行插入时 {@code NOTIFY_STATUS='PENDING'}，真正兜底的是
 * {@code POST /internal/paySign/compensateNotify} 的扫表补偿。本事件只是「快速路径」。</p>
 *
 * @param requestSignSeq 签约流水号
 * @param paymentVendor  支付渠道，回查 {@code APP_PAY_SIGN_INFO} 需要它做联合键
 * @param callback       支付平台签约结果回调原始入参
 */
public record SignResultCommittedEvent(String requestSignSeq,
                                       String paymentVendor,
                                       ReceiveSignResultReqDTO callback) {
}
