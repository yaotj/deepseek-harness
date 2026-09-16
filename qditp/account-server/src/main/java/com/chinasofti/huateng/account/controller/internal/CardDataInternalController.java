package com.chinasofti.huateng.account.controller.internal;

import com.chinasofti.huateng.account.service.AccountProfileService;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.app.UpdateHceDataResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 票卡数据的<b>对内契约面</b>：只被 ITP 内部服务调用，报文<b>不经过 fep-app / fep-acc 接入层</b>。
 *
 * <p>两个入口的调用方实测（2026-09-11 全仓 grep {@code accountClient.xxx}）：</p>
 * <ul>
 *   <li>{@code queryCardTypeByCardId} —— ticket-server（{@code GateTicketHandler:612}、
 *       {@code CardDataHandler:526/556}）、fep-dev-server（{@code GateTransactionHandler:169}）。
 *       <b>零外部调用方</b>。</li>
 *   <li>{@code updateHceData} —— ticket-server（{@code GateTicketHandler:590}）、
 *       face-pay-server（{@code F2fHceService:163}）、collect-pay-server
 *       （{@code BomOrderServiceImpl:1648}）。<b>零外部调用方</b>。</li>
 * </ul>
 *
 * <p><b>为什么单独立类</b>：这两个入口原先混在 {@code controller/ci/app/RequestApplicationController}
 * 里，而那个类的其余入口全部来自 APP（经 fep-app-server）。混在一起的后果不是「不好看」，
 * 而是<b>补验签时没有落点</b>——外部面 MUST 走 {@code AccountRequestVerifier}，
 * 内部面走的是服务间直连、没有 APP 报文骨架也没有 {@code sign}，
 * 在同一个类上按方法开例外必然遗漏。同 ADR-D34 的判据：<b>按调用方切，不按业务切</b>。</p>
 *
 * <p><b>⚠️ 两个 URL 一个字都不能改</b>：{@code AccountClient.queryCardTypeByCardId}
 * （{@code rpc/.../AccountClient.java:136}，注意它把 {@code ?cardId=} 拼在路径里）与
 * {@code AccountClient.updateHceData}（{@code :165}）都是硬编码字符串常量。
 * 本类<b>刻意不加类级 {@code @RequestMapping}</b>，搬迁后路径与搬迁前逐字节相同；
 * 加任何前缀都会让 ticket-server / fep-dev / face-pay / collect-pay 四个模块同时 404，
 * 而且<b>编译与单测都发现不了</b>。</p>
 *
 * <p><b>NEVER 把 {@code queryUserInfo} 挪进来</b>：它的调用方同时含内部（ticket-server、
 * gate-txn-pay-server、pay-sign-server）与外部（fep-app-server 的 {@code IndustryDataServiceImpl}），
 * 是真·双来源，按来源切不干净，见 {@code docs/domain/decisions.md} ADR-D35。</p>
 */
@RestController
public class CardDataInternalController {
    private static final Logger log = LoggerFactory.getLogger(CardDataInternalController.class);

    /** 只用到 {@code queryCardTypeByCardId} 与 {@code updateHceData} 两个方法；{@code queryUserInfo} 留在 APP 面。 */
    private final AccountProfileService accountProfileService;

    /** 构造器注入（ADR-D37）。依赖全部 final，漏注入在编译期即报错。 */
    public CardDataInternalController(AccountProfileService accountProfileService) {
        this.accountProfileService = accountProfileService;
    }

    /**
     * 按逻辑卡号反查真实 ITP 卡类型。闸机链路（IF1A-01）与票卡数据分析链路都靠它把
     * {@code cardId} 翻成 {@code CARD_TYPE}，属只读。
     */
    @GetMapping("/queryCardTypeByCardId")
    public QueryUserInfoResult queryCardTypeByCardId(@RequestParam String cardId) {
        log.info("接收到按逻辑卡号查询真实ITP卡类型接口报文: cardId={}", cardId);
        return accountProfileService.queryCardTypeByCardId(cardId);
    }

    /**
     * 回写 IF1A-01 闸机交易后产生的 HCE 卡数据（{@code reserve1}）。
     *
     * @param request 包含逻辑卡号和 reserve1 的请求
     * @return 更新结果
     */
    @PostMapping("/updateHceData")
    public UpdateHceDataResult updateHceData(@RequestBody UpdateHceDataReqDTO request) {
        log.info("接收到更新HCE卡数据接口报文, cardId={}", request == null ? null : request.getCardId());
        return accountProfileService.updateHceData(request);
    }
}
