package com.chinasofti.huateng.account.controller.page;

import com.chinasofti.huateng.account.entity.AccountExceptionTicket;
import com.chinasofti.huateng.account.service.AccountExceptionTicketService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 账户域异常工单的运营查询与人工关单。
 *
 * <p>只做后端两个端点，前端页面由前端同学另建（用户 2026-09-11 决定）。</p>
 *
 * <p><b>本类只做参数校验与路由</b>（AGENTS.md §3.3）：条数收敛、筛选项归一、关单的 CAS 结果判定
 * 都在 {@link AccountExceptionTicketService} 里。2026-09-11 之前这里直接注入 Mapper，
 * 关单这种状态变更绕过 service 层是当时的欠账，已还。<b>NEVER 再把 Mapper 注回 controller</b>。</p>
 *
 * <p><b>关单是本域唯一的人工出口</b>：{@code ACCOUNT_EXCEPTION_TICKET} 里的行都是补偿任务
 * 重试到上限后留下的「自愈不了」，处置动作（核对支付域展示账号、订正员工码状态）在库外，
 * 关单只表示「有人看过并处理完了」。因此 <b>NEVER 让任何自动流程调用关单</b>，
 * 也 <b>NEVER</b> 在这里顺手做补偿重推 —— 重推入口是 {@code /phoneSignSyncCompensate}。</p>
 *
 * <p><b>本控制器当前没有鉴权</b>：用户 2026-09-11 明确选择「对齐现状，暂不加鉴权，
 * 与其他 20 个端点一致」。这与 AGENTS.md §5.2「新增状态变更型接口 MUST 有鉴权与归属校验」
 * 冲突，是<b>有意为之的临时降级，上线前 MUST 补齐</b>（形态对齐 {@code AccountRequestVerifier}，
 * NEVER 自造签名）。在此之前任何网络可达方都能按 ID 关掉任意工单，
 * {@code CLOSED_BY} 只是线索、不是审计凭据。</p>
 */
@RestController
@RequestMapping("/page/exception-ticket")
public class AccountExceptionTicketPageController {

    private final AccountExceptionTicketService accountExceptionTicketService;

    public AccountExceptionTicketPageController(AccountExceptionTicketService accountExceptionTicketService) {
        this.accountExceptionTicketService = accountExceptionTicketService;
    }

    /**
     * 工单列表。三个筛选项都可为空；不传 {@code ticketStatus} 时返回全部状态，
     * 运营日常关注的是 {@code OPEN}。
     *
     * @param limit 条数上限，缺省 50、上限 200，收敛规则在 service 层
     */
    @GetMapping("/list")
    public ResultVO<List<AccountExceptionTicket>> list(
            @RequestParam(required = false) String ticketStatus,
            @RequestParam(required = false) String ticketType,
            @RequestParam(required = false) String thirdUserId,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        List<AccountExceptionTicket> tickets =
                accountExceptionTicketService.list(ticketStatus, ticketType, thirdUserId, limit);
        return ResultMapper.ok(tickets);
    }

    /**
     * 人工关单。CAS 只接受 {@code OPEN}，因此重复关单返回失败而不是静默成功 ——
     * <b>NEVER 改成「影响 0 行也返回 ok」</b>，那会让「工单不存在」「已被别人关掉」
     * 和「本次关单成功」三种情况在页面上无法区分。
     *
     * @param id       工单 ID
     * @param closedBy 操作人。当前无鉴权，这个值完全由调用方自述，MUST 只当线索
     */
    @PostMapping("/close")
    public ResultVO<Void> close(@RequestParam Long id, @RequestParam String closedBy) {
        if (id == null || !StringUtils.hasText(closedBy)) {
            return ResultMapper.illegalParams("工单ID和操作人不能为空");
        }
        // MUST 显式检查返回值：false 表示工单不存在或已不是 OPEN，NEVER 当成成功
        if (!accountExceptionTicketService.close(id, closedBy)) {
            return ResultMapper.error("工单不存在或已关闭");
        }
        return ResultMapper.ok();
    }
}
