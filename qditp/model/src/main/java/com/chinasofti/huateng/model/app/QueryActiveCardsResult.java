package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.ArrayList;
import java.util.List;

/**
 * 内部 RPC：第三方用户号名下有效票卡列表应答。
 *
 * @see QueryActiveCardsReqDTO
 */
public class QueryActiveCardsResult extends CommonResult {

    /** 有效票卡的逻辑卡号列表；无卡时为空列表（NEVER null，调用方不判空）。 */
    private List<String> cardIds = new ArrayList<>();

    public List<String> getCardIds() {
        return cardIds;
    }

    public void setCardIds(List<String> cardIds) {
        this.cardIds = cardIds == null ? new ArrayList<>() : cardIds;
    }
}
