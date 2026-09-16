package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.entity.UserItpRegInfo;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;

/**
 * 开户收口：把**已组装好**的注册信息落地。<b>渠道无关</b>。
 *
 * <p>2026-09-11（ADR-D31）按渠道拆分 {@code AccountRegistrationServiceImpl} 时抽出。
 * 拆分本身会把 IF8A-01 与支付宝出行分到两个类，而这三步在两条链路上**逐字节相同**——
 * 若不抽出来就会把 ADR-D29 刚合并掉的 {@code buildRegLog} / {@code buildTicketRequest} 重新分成两份。
 * <b>NEVER 把这三步复制回渠道 service</b>。</p>
 *
 * <p><b>本接口不承载任何渠道规则</b>：票种归一、默认渠道推导、亲情卡判定、发号业务类型都留在各渠道
 * service 里。<b>NEVER 因为「两个渠道都要用」就把渠道 if-else 挪进来</b> —— 那会让本类退化成杂物间
 * （ADR-D25 删掉的那个类就是这么来的）。</p>
 *
 * <p><b>收敛判据（ADR-D33 修订）：只放「开户提交动作本身」以及它自己需要的归一化</b>。
 * 原判据写的是「入参只有 {@code UserItpRegInfo} 的方法才属于这里」，但它被本接口自己违反了
 * 3/5 —— 判据一旦不自洽就等于没有判据。现行口径下：
 * {@code registerRideStatus} / {@code persistRegistration} 是提交动作本身；
 * {@code normalizeIssueOrgCode} 是落库前对入参的归一化、带账户域专属的 ERROR 告警，留在这里。
 * <b>已按此判据移出的两个方法，NEVER 加回来</b>：
 * ①{@code isDayPassCard} —— 只是
 * {@code CardTypeCodeEnum.isDailyTicket(CardTypeMapping.toIssueCardType(..))} 的一行转发，
 * 已内联回两个渠道 service；
 * ②{@code attachEmployeeCardsQuietly} —— 它依赖 {@code UserAccEmployeeCardMapper}、与开户落库无关，
 * 已迁到已持同一个 mapper 的
 * {@link com.chinasofti.huateng.account.service.EmployeeCardPersistenceService#attachEmployeeCardsQuietly}。</p>
 */
public interface RegistrationCommitService {

    /**
     * 向 ticket-server 注册乘车状态（<b>RPC，MUST 在事务外调用</b>）。
     *
     * <p>按 AGENTS.md §5.2「先调远端、后改本地」，调用方 MUST 在本方法成功后才落库；
     * 失败时 MUST 释放卡池预占。</p>
     *
     * @param regInfo 已组装完成的注册信息
     * @return ticket-server 的响应；<b>{@code null} 表示不可用</b>，调用方 MUST 视为失败
     */
    RegisterRideStatusRespDTO registerRideStatus(UserItpRegInfo regInfo);

    /**
     * 在**独立短事务**里插入 {@code USER_ITP_REG_INFO} 与 {@code USER_ITP_REG_LOG} 两行。
     *
     * <p>两条写必须一起成立（流水行是注册行的凭证），因此这里**确实需要事务**——与 ADR-D30 里
     * 「展示列回写不包事务」是相反的情形，判据同样是「两条写是否必须一起成立」。
     * 实现用 {@code TransactionTemplate} 而非 {@code @Transactional}，
     * 以保证调用方的 RPC <b>不被</b>卷进这个事务。</p>
     *
     * @param regInfo 已组装完成的注册信息
     */
    void persistRegistration(UserItpRegInfo regInfo);

    /**
     * 留存 APP / 渠道上送的发卡机构码原值；未知机构码打 ERROR 但**不拒绝开户**。
     *
     * @param cardIssueCode 上送的发卡机构码
     * @return trim 后的原值
     */
    String normalizeIssueOrgCode(String cardIssueCode);
}
