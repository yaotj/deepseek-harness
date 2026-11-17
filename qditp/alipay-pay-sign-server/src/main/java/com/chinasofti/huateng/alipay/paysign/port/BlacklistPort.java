package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;


public interface BlacklistPort {


    RpcOutcome addBlackList(String cardId, String thirdUserId, String cardType, String reason);
}
