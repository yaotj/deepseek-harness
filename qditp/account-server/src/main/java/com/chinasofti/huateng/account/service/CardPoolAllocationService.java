package com.chinasofti.huateng.account.service;

/**
 * 开户发号的出网协作者：卡池预占 / 确认 / 释放，以及 HCE 票种向安全服务取卡。
 *
 * <p>2026-09-11 从 {@code AccountApplicationServiceImpl} 拆出（原类 1900+ 行）。
 * 本接口<b>只做 RPC 与日志，不含任何业务策略</b> —— 「哪种票种走卡池、业务流水号怎么拼、
 * 同行票是否幂等」仍留在 {@code AccountApplicationServiceImpl.allocateCard}，
 * <b>NEVER 把这些判断挪进来</b>，否则支付宝出行与 IF8A-01 两条链路的差异会被埋进公共协作者。</p>
 *
 * <p>所有方法都是出网调用，<b>MUST 在事务外调用</b>（AGENTS.md §5.2）。</p>
 */
public interface CardPoolAllocationService {

    /**
     * 向 card-pool-server 预占一个逻辑卡号。
     *
     * @param cardType     票种码（044X）
     * @param businessType 业务类型，与 {@code LOGIC_CARD_POOL_CARD.BUSINESS_TYPE} 对应
     * @param businessId   业务流水号，决定幂等边界
     * @param ownerId      卡号归属方，取 thirdUserId
     * @return 预占成功返回卡号与预占标识；否则返回 {@code null}
     */
    CardAllocation reserveFromPool(String cardType, String businessType, String businessId, String ownerId);

    /**
     * 确认预占。失败只打 ERROR，<b>NEVER 抛出</b> —— 卡号已发给用户，回滚开户才是错的。
     *
     * @param allocation 预占信息，为 {@code null} 或非卡池发号（HCE）时直接跳过
     * @param scene      日志场景名
     * @return {@code true} 表示确认成功（或本次无预占可确认，属正常跳过）；{@code false} 表示卡池拒绝或抛异常
     *
     * <p><b>调用方 MUST 显式检查返回值</b>（AGENTS.md §5.2 关于返回 boolean 的方法）。
     * 返 {@code false} 意味着「账户表已把卡号发出去，池子那边却不是 {@code ASSIGNED}」——
     * 卡号随时可能被再发给另一个用户，**NEVER 在这种情况下对 APP 返成功**，
     * MUST 开 {@code AccountExceptionTicket.TYPE_CARD_POOL_CONFIRM_REJECTED} 工单转人工。
     * 2026-09-14 之前本方法返 {@code void}、只打一行 ERROR，实测并发下真的漏出了一个
     * 「账户表有效、池子 AVAILABLE」的卡号且 APP 收到 `0000`，见 ADR-D52。</p>
     */
    boolean confirmReservation(CardAllocation allocation, String scene);

    /**
     * 释放预占。失败只打 WARN，<b>NEVER 抛出</b> —— 卡池的预占超时回收会兜底。
     *
     * <p><b>⚠️ NEVER 在「本次请求失败」的分支里调本方法</b>（2026-09-14 / ADR-D52）。
     * {@code reserveFromPool} 按 {@code businessId} 幂等，**同一用户同一票种的并发请求拿到的是
     * 同一个 {@code reservationId}**，即预占是这批请求<b>共享</b>的、不是本请求私有的。
     * 于是失败方一 release 就把兄弟请求正要 confirm 的那张卡抽走 —— 实测后果是成功方
     * confirm 被拒、卡号回到 {@code AVAILABLE} 却已写进账户表。
     * 而**任何以 {@code reservationId} 为条件的 CAS 都挡不住这件事**（兄弟持有的是同一个 id），
     * 请求内也拿不到「有没有兄弟正要 confirm」的信息，因此唯一正确的做法是<b>不释放</b>，
     * 交给 `sys_job` 107「卡池维护」（{@code cardPoolQuartzTask.runMaintenance()}，cron
     * {@code 0 0/5 * * * ?}）的预占超时回收。
     *
     * <p>本方法**保留**是因为「明确不该占着这张卡」的场景仍需要它（如运维显式回收），
     * <b>NEVER 因为开户链路不再调用就删掉</b>。</p>
     *
     * @param allocation 预占信息，为 {@code null} 或非卡池发号（HCE）时直接跳过
     * @param scene      日志场景名
     */
    void releaseReservation(CardAllocation allocation, String scene);

    /**
     * 判断是否为需要通过安全服务发售的 HCE 卡类型。
     *
     * @param cardType APP 卡类型
     * @return {@code true} 表示 HCE卡（03）或新版HCE卡（04）
     */
    boolean isHceCard(String cardType);

    /**
     * 请求安全服务发售 HCE 卡数据并取得逻辑卡号。
     *
     * @param thirdUserId 十进制第三方用户标识
     * @param ticketCard  NFC 票卡类型
     * @return 安全服务生成的逻辑卡号与 HCE 数据；调用失败时返回 {@code null}
     */
    HceCardAllocation requestHceCardData(String thirdUserId, String ticketCard);

    /**
     * 开户时确定的逻辑卡号及 HCE 卡数据。非 HCE 卡没有 HCE 卡数据。
     */
    record HceCardAllocation(String cardId, String hceData) {
    }

    /**
     * 一次开户的发号结果。
     *
     * @param cardId        逻辑卡号
     * @param hceData       HCE 卡数据，仅 HCE 票种非空
     * @param reservationId 卡池预占标识，仅走卡池发号时非空；为空表示无预占可确认 / 释放
     * @param businessId    预占时使用的业务流水号，确认与释放都要带上供服务端校验归属
     */
    record CardAllocation(String cardId, String hceData, String reservationId, String businessId) {
    }
}
