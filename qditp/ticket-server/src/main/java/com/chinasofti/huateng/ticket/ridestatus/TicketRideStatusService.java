package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;

/**
 * APP 侧乘车码状态服务。
 *
 * <p>本包只负责 {@code QRCODE_STATUS} 这一张表的注册与读取，方法数刻意收窄到两个。
 * 以下职责**已迁出、NEVER 加回**：
 * <ul>
 *   <li>IF8A-04 自助补站 → {@code supplement/ExcessFareHandler}，由
 *       {@code TicketSupplementController} 直连（此前在本接口里是零业务的穿透方法）</li>
 *   <li>{@code queryEntryDevice} / {@code queryFirstEntryTxn} →
 *       {@code entrytxn/EntryTxnQueryService}（读的是 {@code QRCODE_TXN_DETAIL}，与状态无关）</li>
 * </ul>
 *
 * <p>AGM 侧接口请使用 {@code gate/AgmRideStatusService}。**这里刻意不用 {@code @link}** ——
 * 只为不让本接口的 javadoc 硬绑 gate 包的全限定名（那边改名时不会静默留下死链）。
 * **NEVER 把这条读成「{@code @link} 会被架构门禁算成依赖」**：2026-09-14 实测
 * （javac 21 编译一个仅在 javadoc 里 {@code @link} 引用某类的类，{@code javap -v -p}
 * 里那个类名出现 0 次）javadoc 不进 class 文件，而 {@code arch/TicketArchitectureTest}
 * 读的是字节码 —— 换成纯文本对门禁结果**没有任何影响**。
 */
public interface TicketRideStatusService {

    /**
     * 开户成功后初始化用户乘车状态（开卡复位）。
     *
     * <p>走 {@code QRCodeStatusStore.upsert} 的**无条件覆盖**分支，这是刻意设计：
     * 复位就该把 {@code CODE_STATUS} / {@code TXN_SEQ} / {@code GATE_STATUS} 拉回初始值。
     * **NEVER 改成 CAS 版 {@code upsertWithCas}** —— 那条是过闸链路用的，见
     * {@code docs/domain/state-machines.md} §四。
     */
    RegisterRideStatusRespDTO registerRideStatus(RegisterRideStatusReqDTO request);

    /**
     * IF8A-29 查询用户上次行程。
     *
     * @param request 查询用户上次行程请求参数
     * @return 用户当前行程信息
     */
    QueryUserItineraryResult queryUserItinerary(QueryUserItineraryReqDTO request);
}
