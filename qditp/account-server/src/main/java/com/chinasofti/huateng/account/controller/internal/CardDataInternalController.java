package com.chinasofti.huateng.account.controller.internal;

import com.chinasofti.huateng.account.service.AccountProfileService;
import com.chinasofti.huateng.model.app.QueryActiveCardsReqDTO;
import com.chinasofti.huateng.model.app.QueryActiveCardsResult;
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


@RestController
public class CardDataInternalController {
    private static final Logger log = LoggerFactory.getLogger(CardDataInternalController.class);


    private final AccountProfileService accountProfileService;


    public CardDataInternalController(AccountProfileService accountProfileService) {
        this.accountProfileService = accountProfileService;
    }


    @GetMapping("/queryCardTypeByCardId")
    public QueryUserInfoResult queryCardTypeByCardId(@RequestParam String cardId) {
        log.info("接收到按逻辑卡号查询真实ITP卡类型接口报文: cardId={}", cardId);
        return accountProfileService.queryCardTypeByCardId(cardId);
    }


    @PostMapping("/queryActiveCards")
    public QueryActiveCardsResult queryActiveCards(@RequestBody QueryActiveCardsReqDTO request) {
        log.info("接收到查询有效票卡列表请求, thirdUserId={}",
                request == null ? null : request.getThirdUserId());
        return accountProfileService.queryActiveCards(request);
    }


    @PostMapping("/updateHceData")
    public UpdateHceDataResult updateHceData(@RequestBody UpdateHceDataReqDTO request) {
        log.info("接收到更新HCE卡数据接口报文, cardId={}", request == null ? null : request.getCardId());
        return accountProfileService.updateHceData(request);
    }
}
