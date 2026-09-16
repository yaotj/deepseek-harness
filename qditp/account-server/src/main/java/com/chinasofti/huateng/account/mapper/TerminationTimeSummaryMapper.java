package com.chinasofti.huateng.account.mapper;

import com.chinasofti.huateng.account.page.TerminationTimeSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 「用户查询」运营页专用：只读聚合 pay-sign 域的 {@code APP_TERMINATION_REQUEST}。
 *
 * <p>account-server 与 pay-sign-server 共用同一 AFCITPDB 实例，因此用本地 mapper
 * 直读跨域表来替代跨域 RPC（ADR-D30 禁止 {@code ItpUserQueryService} 注入
 * {@code PaySignClient}，曾因列表页每行一次 HTTP 的 N+1 被移除）。
 * <b>NEVER 在本 mapper 写 {@code APP_TERMINATION_REQUEST}</b>（insert/update/delete
 * 一律不允许）——写路径的权威在 pay-sign-server。</p>
 */
@Mapper
@Component
public interface TerminationTimeSummaryMapper {

    /**
     * 按逻辑卡号集合聚合每张卡的解约时间。
     *
     * <p>返回按 (CARD_ID, THIRD_USER_ID) 分组的结果：{@code requestTime} 是该卡最近一次
     * 申请解绑时间，{@code completeTime} 是最近一次解绑<b>成功</b>时间（仅统计
     * {@code TERMINATION_STATUS = 'SUCCESS'}）。从未解绑过的卡不会出现在结果里。</p>
     *
     * @param cardIds 命中的开户记录卡号集合；调用方 MUST 保证非空（空集合应直接跳过调用，
     *                否则 {@code in ()} 会在运行时才炸）
     */
    List<TerminationTimeSummary> selectByCardIds(@Param("cardIds") List<String> cardIds);
}
