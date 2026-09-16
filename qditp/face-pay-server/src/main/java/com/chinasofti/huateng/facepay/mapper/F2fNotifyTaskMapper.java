package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fNotifyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出向通知任务 Mapper（表 F2F_NOTIFY_TASK）。
 *
 * <p>本项目不使用消息队列，出向通知（IF8B-04/05/06/07）统一是
 * 「落库状态 + {@code @Scheduled} 扫表重试」，本表是可靠投递的唯一载体。
 * 改这个接口前 MUST 先读以下四条：
 * <ul>
 *   <li><b>落库只 INSERT，不先查后插。</b>并发下「先 SELECT 判存在再 INSERT」无效。
 *       重复由函数唯一索引 UK_F2F_NOTIFY_IDEM
 *       {@code (NOTIFY_TYPE, ORDER_NO, NVL(REFUND_NO,'#NONE#'))} 抛
 *       {@code DuplicateKeyException}，由 application 层捕获后按已存在任务幂等返回。</li>
 *   <li><b>扫表只按 {@code NOTIFY_STATUS} 与 {@code NEXT_RETRY_TMS} 两列取</b>
 *       （命中 IDX_F2F_NOTIFY_SCAN）。退避策略由应用算好 {@code nextRetryTms} 后写入，
 *       SQL 内 NEVER 做时间计算。</li>
 *   <li><b>状态推进走白名单。</b>{@link #markSuccess} / {@link #markFailure} 的 WHERE 都写明
 *       允许的前置状态，NEVER 改成「非终态即可更新」。</li>
 *   <li><b>GIVEUP 是人工介入终态。</b>超过 {@code MAX_RETRY_TIMES} 后置 GIVEUP，
 *       不再被 {@link #selectDueTasks} 捞出。</li>
 * </ul>
 */
@Mapper
public interface F2fNotifyTaskMapper {

    /**
     * 插入通知任务。重复的（NOTIFY_TYPE, ORDER_NO, REFUND_NO）由 UK_F2F_NOTIFY_IDEM 抛
     * DuplicateKeyException，本方法不做任何判重。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fNotifyTask task);

    /**
     * 按主键查询。供运营端排查 GIVEUP 任务、或按日志中的 ID 定位单笔任务。
     *
     * @return 命中的任务；不存在返回 null
     */
    F2fNotifyTask selectById(@Param("id") Long id);

    /**
     * 按幂等三要素查询，命中 UK_F2F_NOTIFY_IDEM。
     *
     * <p>SQL 内 REFUND_NO 用 {@code NVL(..., '#NONE#')} 与索引表达式保持一致，
     * 因此 refundNo 传 null 即表示「整单类通知」，NEVER 改写成 {@code REFUND_NO = #{refundNo}}
     * ——那样 null 永远比不中，且用不上函数索引。
     *
     * <p>主要用途是 INSERT 撞唯一索引后回查已有任务做幂等返回，
     * NEVER 用它做「先查后插」的前置判断。
     *
     * @return 命中的任务；不存在返回 null
     */
    F2fNotifyTask selectByBizKey(@Param("notifyType") String notifyType,
                                 @Param("orderNo") String orderNo,
                                 @Param("refundNo") String refundNo);

    /**
     * 扫出到期待发送的任务：NOTIFY_STATUS = 'PENDING' 且 NEXT_RETRY_TMS 已到。
     * 命中 IDX_F2F_NOTIFY_SCAN，按 NEXT_RETRY_TMS 升序，先到期的先发。
     *
     * <p>NEXT_RETRY_TMS 为空视为「立即可发」，首次落库不写该列时也能被捞出。
     * SUCCESS / FAILED / GIVEUP 都不会被捞出：FAILED 由 {@link #markFailure}
     * 重置回 PENDING 才重新进入扫描，GIVEUP 需人工介入。
     *
     * @param now   当前时间，由调用方传入以便单测可控
     * @param limit 单批最大条数，SQL 用 FETCH FIRST ... ROWS ONLY 限制
     * @return 到期任务列表，可能为空
     */
    List<F2fNotifyTask> selectDueTasks(@Param("now") LocalDateTime now,
                                       @Param("limit") int limit);

    /**
     * 与 {@link #selectDueTasks} 完全同义，只多一条 {@code NOTIFY_TYPE} 谓词。
     *
     * <p>存在的唯一理由是旧模块 {@code /pay/noticeAppTask/**} 那三个外部触发端点
     * <b>按业务类型分开</b>（取票成功 / 取票失败 / 退款结果各一个 URL，原本对应三张
     * {@code TBL_NOTICE_APP_*} 表）。本模块把三张表合成了 {@code F2F_NOTIFY_TASK} 一张，
     * 但对上游的触发粒度 MUST 保持不变——否则运维点「重投退款通知」会连带把取票通知也发一遍。
     *
     * <p><b>NEVER 用它替代 {@link #selectDueTasks} 做常规扫表</b>：`@Scheduled` 那条走全类型，
     * 少一次索引前缀不匹配。本语句在 IDX_F2F_NOTIFY_SCAN 上仍是
     * {@code NOTIFY_STATUS} 前缀扫描，{@code NOTIFY_TYPE} 只做回表过滤，数据量下无需额外索引。
     *
     * @param notifyType 只投递该类型，取值见 {@code F2fNotifyService.TYPE_*}
     * @return 到期任务列表，可能为空
     */
    List<F2fNotifyTask> selectDueTasksByType(@Param("now") LocalDateTime now,
                                             @Param("notifyType") String notifyType,
                                             @Param("limit") int limit);

    /**
     * 标记投递成功：置 SUCCESS 并回填 SUCCESS_TMS。
     * 前置状态白名单只允许 PENDING / FAILED，SUCCESS 与 GIVEUP 不再翻转。
     *
     * @return 影响行数；0 表示任务已不在可投递状态（已成功或已 GIVEUP），
     *         调用方 MUST 据此判断，NEVER 忽略返回值
     */
    int markSuccess(@Param("id") Long id,
                    @Param("successTms") LocalDateTime successTms);

    /**
     * 记一次投递失败：RETRY_TIMES + 1，写入 LAST_ERROR 与下次重试时间。
     *
     * <p>是否放弃在同一条 UPDATE 里用 CASE WHEN 判定：
     * {@code RETRY_TIMES + 1 >= MAX_RETRY_TIMES} 时置 GIVEUP，否则回到 PENDING。
     * 选一条 UPDATE 而不是拆「加次数」与「置 GIVEUP」两个方法，理由有三：
     * <ul>
     *   <li>次数与状态是同一个决策的两个面，拆两条 SQL 之间存在窗口，
     *       期间任务仍是 PENDING 且 NEXT_RETRY_TMS 已到，会被下一轮扫表重复捞出，
     *       实际重试次数可能超过 MAX_RETRY_TIMES；</li>
     *   <li>MAX_RETRY_TIMES 是行上的列而非常量（DDL 默认 5，允许按任务调整），
     *       比较交给数据库读到的是当前行真值，应用侧先读再算会引入读到即过期的问题；</li>
     *   <li>本项目不使用分布式锁，跨行原子性只能靠单语句，这与「幂等靠唯一索引、
     *       状态推进靠条件 UPDATE」的既有做法一致。</li>
     * </ul>
     *
     * <p>nextRetryTms 由调用方按退避策略算好后传入，SQL 内 NEVER 做时间计算。
     * 命中 GIVEUP 分支时该值仍会写入，但 GIVEUP 不被扫表捞出，不影响行为，
     * 且保留了「原本打算何时重试」的现场信息。
     *
     * @param nextRetryTms 下次重试时间，由应用按退避策略计算
     * @param lastError    本次失败原因，超长 MUST 由调用方截断到 512 字符内
     * @return 影响行数；0 表示任务已成功或已 GIVEUP。
     *         本方法不返回是否已放弃，需要时由调用方随后 {@link #selectById} 读取 NOTIFY_STATUS
     */
    int markFailure(@Param("id") Long id,
                    @Param("nextRetryTms") LocalDateTime nextRetryTms,
                    @Param("lastError") String lastError);
}
