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
 */
@RestController
public class CardDataInternalController {
    private static final Logger log = LoggerFactory.getLogger(CardDataInternalController.class);

    /**
     * 只用到 {@code queryCardTypeByCardId} 与 {@code updateHceData} 两个方法；{@code queryUserInfo} 留在 APP 面。
     */
    private final AccountProfileService accountProfileService;

    /**
     * 构造器注入（ADR-D37）。
     */
    public CardDataInternalController(AccountProfileService accountProfileService) {
        this.accountProfileService = accountProfileService;
    }

    /**
     * 按逻辑卡号反查真实 ITP 卡类型。
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
