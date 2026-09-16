package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.entity.AccountExceptionTicket;

import java.util.List;

/**
 * 账户域异常工单的运营查询与人工关单。
 *
 * <p>2026-09-11 从 {@code AccountExceptionTicketPageController} 下沉：那里原本直接注入 Mapper，
 * 关单这种<b>状态变更</b>走 controller 直连 mapper 违反 AGENTS.md §3.3（controller 只做参数校验与路由）。
 * 查询的条数收敛与关单的 CAS 结果判定都是业务规则，属于本层。</p>
 *
 * <p><b>NEVER 让任何自动流程调用 {@link #close}</b>：`ACCOUNT_EXCEPTION_TICKET` 里的行都是补偿任务
 * 重试到上限后留下的「自愈不了」，处置动作在库外，关单只表示「有人看过并处理完了」。
 * 补偿重推的入口是 {@code /phoneSignSyncCompensate}，<b>NEVER 在关单里顺手重推</b>。</p>
 */
public interface AccountExceptionTicketService {

    /**
     * 按条件查询工单。三个筛选项都可为空（空即不过滤）。
     *
     * <p>条数上限由本层收敛：{@code limit <= 0} 取 50，超过上限一律收敛到上限。
     * 这张表只增不删、没有归档任务，<b>NEVER 放开成全量查询</b>。</p>
     */
    List<AccountExceptionTicket> list(String ticketStatus, String ticketType, String thirdUserId, int limit);

    /**
     * 人工关单，CAS 只接受 {@code OPEN}。
     *
     * @return {@code true} 表示本次真的把一行从 OPEN 改成了 CLOSED；{@code false} 表示工单不存在或已不是 OPEN
     *
     * <p><b>调用方 MUST 显式检查返回值</b>（AGENTS.md §5.2 关于返回 boolean 的方法）：
     * 把 {@code false} 当成成功会让「工单不存在」「已被别人关掉」「本次关单成功」三种情况无法区分。</p>
     */
    boolean close(Long id, String closedBy);
}
