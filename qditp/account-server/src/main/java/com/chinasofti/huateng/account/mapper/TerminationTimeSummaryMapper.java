package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.page.TerminationTimeSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「用户查询」运营页专用：只读聚合 pay-sign 域的 {@code APP_TERMINATION_REQUEST}。
 */
@Mapper
@Component
public interface TerminationTimeSummaryMapper {
    /**
     * 按逻辑卡号集合聚合每张卡的解约时间。
     *
     * @param cardIds 命中的开户记录卡号集合；调用方 MUST 保证非空（空集合应直接跳过调用，
     * 否则 {@code in ()} 会在运行时才炸）
     */
    List<TerminationTimeSummary> selectByCardIds(@Param("cardIds") List<String> cardIds);
}
