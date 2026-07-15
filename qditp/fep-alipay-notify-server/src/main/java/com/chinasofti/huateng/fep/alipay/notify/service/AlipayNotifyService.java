package com.chinasofti.huateng.fep.alipay.notify.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPushTransDataReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveBlackListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripReceiveCardDataReqDTO;

public interface AlipayNotifyService {

    boolean notifyCloseResult(String url, AlipayTripCloseResultReqDTO dto);

    boolean notifyPushTransData(String url, AlipayTripPushTransDataReqDTO dto);

    boolean notifyCardData(String url, AlipayTripReceiveCardDataReqDTO dto);

    boolean notifyBlackList(String url, AlipayTripReceiveBlackListReqDTO dto);
}