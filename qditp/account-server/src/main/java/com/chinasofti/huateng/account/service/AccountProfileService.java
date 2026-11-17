package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.model.app.QueryActiveCardsReqDTO;
import com.chinasofti.huateng.model.app.QueryActiveCardsResult;
import com.chinasofti.huateng.model.app.QueryUserInfoReqDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.app.UpdateHceDataResult;


public interface AccountProfileService {

    QueryUserInfoResult queryUserInfo(QueryUserInfoReqDTO request);


    QueryUserInfoResult queryCardTypeByCardId(String cardId);


    QueryActiveCardsResult queryActiveCards(QueryActiveCardsReqDTO request);


    UpdateHceDataResult updateHceData(UpdateHceDataReqDTO request);
}
