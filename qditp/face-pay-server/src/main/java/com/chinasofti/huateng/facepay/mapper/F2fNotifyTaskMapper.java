package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fNotifyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/** 出向通知任务 Mapper（表 F2F_NOTIFY_TASK）。 */
@Mapper
public interface F2fNotifyTaskMapper {

    /**
     * 插入通知任务。
     *
     * @return 影响行数，正常为 1
     */
    int insert(F2fNotifyTask task);

    /**
     * 按主键查询。
     *
     * @return 命中的任务；不存在返回 null
     */
    F2fNotifyTask selectById(@Param("id") Long id);

    /**
     * 按幂等三要素查询，命中 UK_F2F_NOTIFY_IDEM。
     *
     * @return 命中的任务；不存在返回 null
     */
    F2fNotifyTask selectByBizKey(@Param("notifyType") String notifyType,
                                 @Param("orderNo") String orderNo,
                                 @Param("refundNo") String refundNo);

    /**
     * 扫出到期待发送的任务：NOTIFY_STATUS = 'PENDING' 且 NEXT_RETRY_TMS 已到。
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
     * @param notifyType 只投递该类型，取值见 {@code F2fNotifyService.TYPE_*}
     * @return 到期任务列表，可能为空
     */
    List<F2fNotifyTask> selectDueTasksByType(@Param("now") LocalDateTime now,
                                             @Param("notifyType") String notifyType,
                                             @Param("limit") int limit);

    /**
     * 标记投递成功：置 SUCCESS 并回填 SUCCESS_TMS。
     *
     * @return 影响行数；0 表示任务已不在可投递状态（已成功或已 GIVEUP），
     */
    int markSuccess(@Param("id") Long id,
                    @Param("successTms") LocalDateTime successTms);

    /**
     * 记一次投递失败：RETRY_TIMES + 1，写入 LAST_ERROR 与下次重试时间。
     *
     * @param nextRetryTms 下次重试时间，由应用按退避策略计算
     * @param lastError    本次失败原因，超长 MUST 由调用方截断到 512 字符内
     * @return 影响行数；0 表示任务已成功或已 GIVEUP。
     */
    int markFailure(@Param("id") Long id,
                    @Param("nextRetryTms") LocalDateTime nextRetryTms,
                    @Param("lastError") String lastError);
}
