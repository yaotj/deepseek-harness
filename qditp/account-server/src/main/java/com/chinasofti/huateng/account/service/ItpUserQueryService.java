package com.chinasofti.huateng.account.service;

import com.chinasofti.huateng.account.page.ItpPayChannelView;
import com.chinasofti.huateng.account.page.ItpUserCanceledSearchView;
import com.chinasofti.huateng.account.page.ItpUserSearchQuery;
import com.chinasofti.huateng.account.page.ItpUserSearchView;
import com.chinasofti.huateng.account.page.RegStatView;

import java.util.List;


public interface ItpUserQueryService {

    List<ItpUserSearchView> search(ItpUserSearchQuery query);


    List<ItpPayChannelView> payChannels(String thirdUserId, String cardId, String cardType);


    List<RegStatView> regStats(String startDate, String endDate);


    List<ItpUserSearchView> batchSearch(List<String> cardIds);


    List<ItpUserCanceledSearchView> searchCanceledLog(ItpUserSearchQuery query);
}
