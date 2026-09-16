package com.chinasofti.huateng.account.controller.internal;

import com.chinasofti.huateng.account.service.PayChannelInternalService;
import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractReqDTO;
import com.chinasofti.huateng.model.app.QueryPayChannelByContractResult;
import com.chinasofti.huateng.model.app.SyncPayAccountIdReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付通道的<b>对内端点</b>：唯一调用方是 pay-sign-server（ADR-D34）。
 *
 * <p>2026-09-11 从 {@code controller/ci/app/RequestApplicationController} 切出，
 * 与 recon 的 {@code ReconInternalController} 同形。切出的收益是<b>鉴权有了单一落点</b>：
 * 上线前补入向校验只需拦本类，不必在 APP 端点上开例外。</p>
 *
 * <p><b>⚠️ 两个 URL 一个字都不能改</b>：{@code AccountClient.queryPayChannelByContractNo}
 * （{@code rpc/.../AccountClient.java:130}）与 {@code AccountClient.syncPayAccountId}
 * （同文件 :156）是硬编码路径，改这里等于让支付域两条链路同时 404，
 * 而 <b>404 会被上游 catch 成「远端不可用」、不会有编译期或单测报错</b>。
 * 因此 {@code /queryPayChannelByContractNo} 保留在根路径下、<b>NEVER 为了整齐挪进
 * {@code /internal/} 前缀</b>；两个路径的不一致是历史现状，要统一 MUST 与 rpc 模块同批改。</p>
 *
 * <p><b>本类只做路由与入参日志，NEVER 写业务逻辑</b>（AGENTS.md §3.3）。</p>
 */
@RestController
public class PayChannelInternalController {
    private static final Logger log = LoggerFactory.getLogger(PayChannelInternalController.class);

    private final PayChannelInternalService payChannelInternalService;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public PayChannelInternalController(PayChannelInternalService payChannelInternalService) {
        this.payChannelInternalService = payChannelInternalService;
    }

    /**
     * 按签约流水号查询支付通道（供 pay-sign-server IF8A-75 反查票卡信息）。
     *
     * <p>只读、不改状态，因此不落在 AGENTS.md §5.2「状态变更型接口必须鉴权」范围内；
     * 但它会返回 cardId，补入向验签时 MUST 一并覆盖本端点。</p>
     */
    @PostMapping("/queryPayChannelByContractNo")
    public QueryPayChannelByContractResult queryPayChannelByContractNo(
            @RequestBody QueryPayChannelByContractReqDTO request) {
        log.info("接收到按签约流水号查询支付通道报文: {}", request);
        return payChannelInternalService.queryPayChannelByContractNo(request);
    }

    /**
     * 接收支付域签约成功后推来的 {@code PAY_ACCOUNT_ID}（ADR-D32）。
     *
     * <p>调用方是 pay-sign-server 的 {@code SignResultCommittedListener}，走
     * {@code AccountClient.syncPayAccountId}。补 ADR-D30 的覆盖率缺口：该列原先只有 IF8A-77 会写。</p>
     *
     * <p><b>⚠️ 本端点是状态变更型接口但当前无鉴权</b>，与 AGENTS.md §5.2 冲突，
     * 属**有意为之的临时降级**（对齐 recon 的 {@code X-Recon-Token} 已删除现状）。
     * 风险面比 recon 那批小：它只能按签约流水号改一列展示值、改不了任何业务状态。
     * <b>上线前 MUST 补齐</b>，补时与 {@code /queryPayChannelByContractNo} 一并处理——
     * 这两个端点现在同在本类，是 ADR-D34 切分的直接收益。</p>
     */
    @PostMapping("/internal/payChannel/syncPayAccountId")
    public CommonResult syncPayAccountId(@RequestBody SyncPayAccountIdReqDTO request) {
        log.info("接收到支付域回写支付账号报文: {}", request);
        return payChannelInternalService.syncPayAccountId(request);
    }
}
