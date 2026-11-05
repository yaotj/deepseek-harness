package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.entity.F2fNotifyTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface F2fNotifyTaskMapper {

    int insert(F2fNotifyTask task);

    F2fNotifyTask selectById(@Param("id") Long id);

    F2fNotifyTask selectByBizKey(@Param("notifyType") String notifyType,
                                 @Param("orderNo") String orderNo,
                                 @Param("refundNo") String refundNo);

    List<F2fNotifyTask> selectDueTasks(@Param("now") LocalDateTime now,
                                       @Param("limit") int limit);

    List<F2fNotifyTask> selectDueTasksByType(@Param("now") LocalDateTime now,
                                             @Param("notifyType") String notifyType,
                                             @Param("limit") int limit);

    int markSuccess(@Param("id") Long id,
                    @Param("successTms") LocalDateTime successTms);

    int markFailure(@Param("id") Long id,
                    @Param("nextRetryTms") LocalDateTime nextRetryTms,
                    @Param("lastError") String lastError);
}
