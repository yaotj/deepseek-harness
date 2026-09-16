package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.domain.EmployeeCardEvent;
import com.chinasofti.huateng.account.entity.UserAccEmployeeCard;
import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;

/**
 * 员工码本地落库服务。
 *
 * <p>本接口<b>多数</b>实现方法带 {@code @Transactional}，用途是把「短事务的本地写」从
 * 「调 ACC / APP 的 HTTP 请求」里分离出来：调用方 MUST 在事务外完成远端调用，
 * 远端成功后再调本接口提交本地写，NEVER 把网络调用包进事务（见 AGENTS.md §5.2）。
 *
 * <p><b>两个例外方法各自在 Javadoc 里写明了「为什么不带事务」，NEVER 顺手给它们补上</b>：
 * {@link #recordEvent}（单条 INSERT，要按调用方语义决定跟不跟着回滚）与
 * {@link #attachEmployeeCardsQuietly}（逐卡独立 CAS + 吞异常，包事务会让一张卡失败拖垮整批）。</p>
 */
public interface EmployeeCardPersistenceService {
    /**
     * 落库 ACC 员工码状态通知（新增 / 更新 + 写一条事件日志）。
     *
     * <p><b>UPDATE 影响 0 行时抛 {@link IllegalStateException}</b>：本方法先按 cardNo 查到了行，
     * 0 行只可能是并发删除。调用方 {@code EmployeeCardServiceImpl.processAppBatch} 会捕获、
     * 把该卡放进 failList，ACC 收到 PARTIAL_SUCCESS 后重推即自愈。
     * <b>NEVER 改成只记日志</b>——那会连带写出一条「处理成功」的事件日志。</p>
     *
     * @param source ACC 上送的员工码信息
     */
    void saveFromStatusNotify(EmployeeCardInfoDTO source);

    /**
     * ACC 激活 / 禁用返回成功后，提交本地状态与事件日志两条写。
     *
     * @param cardNo       员工码卡号，已 trim
     * @param targetStatus 目标卡状态，1 正常 / 2 禁用
     * @param markOpenTms  为 true 且 {@code OPEN_TMS} 为空时回填开通时间
     * @param remark       事件日志备注
     * @return true 表示卡记录被更新；false 表示按卡号未命中任何行（并发注销等），调用方 MUST 视为不一致
     */
    boolean applyActivationResult(String cardNo, int targetStatus, boolean markOpenTms, String remark);

    /**
     * 用 ACC 返回的资料补全本地员工码行（查询时发现姓名为空才走这里），并回写 {@code UPDATE_TMS}。
     *
     * <p>2026-09-11 由 {@code EmployeeCardServiceImpl} 内联的「applyAccInfo + update」上移到本接口：
     * 那份 {@code applyAccInfo} 与本实现里的同名方法**逐字节相同**，
     * {@code EmployeeCardInfoDTO} 加字段时容易只改一处。</p>
     *
     * <p>本方法<b>不写事件日志</b>（与搬迁前行为一致，只是补全资料、不是状态变更），
     * <b>NEVER 在这里改 {@code CARD_STATUS} 之外的语义</b>：{@code source.getCardStatus()}
     * 仍按 ACC 返回值覆盖，这也是搬迁前的行为。</p>
     *
     * <p><b>UPDATE 影响 0 行只记 WARN、NEVER 抛</b>：本方法挂在只读的员工码查询链路上，
     * 抛出会让查询退化成全局异常处理器的 UUID retCode；资料回填是尽力而为的旁路，
     * 本次响应用的是内存里已更新的 {@code target}，下次查询还会再试。
     * 这与 {@link #saveFromStatusNotify}「0 行即抛」是<b>两类语义，NEVER 统一</b>。</p>
     *
     * @param target  本地已存在的员工码行，方法内会被就地修改
     * @param source  ACC 返回的员工码资料
     */
    void refreshProfileFromAcc(UserAccEmployeeCard target, EmployeeCardInfoDTO source);

    /**
     * 只写一条员工码事件日志（{@code USER_ACC_EMPLOYEE_CARD_LOG}），不动员工码主表。
     *
     * <p>2026-09-11 收口：这段「新建 log 实体 + 填 5 个字段 + insert」原先在
     * {@code EmployeeCardServiceImpl} 里有一份**逐字节相同**的私有副本，两处各自注入
     * {@code UserAccEmployeeCardLogMapper}。日志表加列时容易只改一处，故上移到本接口。</p>
     *
     * <p>本方法<b>故意不带 {@code @Transactional}</b>：它只有一条 INSERT，
     * 调用方在事务内调（如 {@code updateEmployeeInfo}）会按 REQUIRED 加入调用方事务、
     * 随其一起回滚；调用方不在事务内调（如 APP 注册失败留痕）则自动提交、
     * <b>NEVER 因为上层业务失败而丢掉这条痕迹</b>。两种语义都与搬迁前一致。</p>
     *
     * @param cardNo     员工码卡号
     * @param eventType  事件类型，取值定义在 {@link EmployeeCardEvent}
     *                   （2026-09-12 由 {@code String} 改成枚举，写错一个字母即编译失败；
     *                   <b>NEVER 改回 String</b> —— 此前 6 处裸字面量，拼错只会在日志表里
     *                   多出一个没人查得到的事件类型，编译与单测都发现不了）
     * @param cardStatus 记录当时的卡状态
     * @param remark     备注
     */
    void recordEvent(String cardNo, EmployeeCardEvent eventType, Integer cardStatus, String remark);

    /**
     * 开户成功后按手机号把该号名下的活跃员工码挂到这个 ITP 用户上（写 {@code THIRD_USER_ID}）。
     *
     * <p>2026-09-11（ADR-D33 收尾）由 {@code RegistrationCommitService} 迁来。<b>逐行照搬、行为不变</b>。
     * 迁移理由：它依赖的是 {@code UserAccEmployeeCardMapper}、与「开户提交动作」无关，
     * 而本接口已持同一个 mapper；留在开户收口类里会让那个类的收敛判据再次失效。</p>
     *
     * <p><b>为什么挂接只能在开户时做</b>：{@code USER_ACC_EMPLOYEE_CARD} 的行是 ACC 通知先建的
     * （见 {@link #saveFromStatusNotify}），那一刻 ITP 侧还不知道这张卡属于哪个 APP 用户，
     * 所以 {@code THIRD_USER_ID} 只能在「ITP 用户出现」的时刻反向补，而开户正是这个时刻。
     * 手机号是员工码与 ITP 用户两边唯一的共有键。</p>
     *
     * <p><b>MUST 在开户事务提交之后调用，本方法 NEVER 抛异常、NEVER 带 {@code @Transactional}</b>：
     * 挂接失败只意味着「员工码暂时没挂上」，而开户已经成功、卡号也已回给 APP；把它做成能拖垮开户的
     * 强依赖，等于用一个可用性故障换一个数据问题。多卡时每张卡是独立 CAS，
     * 包进一个事务会让一张卡失败连带回滚已挂好的其它卡。</p>
     *
     * <p><b>幂等</b>：{@code updateThirdUserId} 的 WHERE 含「{@code THIRD_USER_ID} 为空或已等于目标值」，
     * 因此重复开户重放不会把别人的卡抢过来；影响 0 行 = 该卡已被**别的**用户占用或不在正常态，
     * 只记 WARN，<b>NEVER 改成强行覆盖</b>。</p>
     *
     * @param thirdUserId 第三方用户标识
     * @param msisdn      手机号
     */
    void attachEmployeeCardsQuietly(String thirdUserId, String msisdn);
}
