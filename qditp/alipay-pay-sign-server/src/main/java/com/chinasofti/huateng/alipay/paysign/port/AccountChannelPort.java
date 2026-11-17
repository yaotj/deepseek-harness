package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;


public interface AccountChannelPort {


    RpcOutcome updatePaymentChannel(String thirdUserId, String channelUserAccount, String agreementCode);
}
