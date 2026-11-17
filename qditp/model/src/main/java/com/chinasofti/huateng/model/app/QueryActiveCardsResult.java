package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.ArrayList;
import java.util.List;


public class QueryActiveCardsResult extends CommonResult {

    private List<String> cardIds = new ArrayList<>();

    public List<String> getCardIds() {
        return cardIds;
    }

    public void setCardIds(List<String> cardIds) {
        this.cardIds = cardIds == null ? new ArrayList<>() : cardIds;
    }
}
