package com.chinasofti.huateng.alipay.paysign.port;

import com.chinasofti.huateng.rpc.outcome.RpcOutcome;


public interface DebitSyncPort {


    RpcOutcome syncDebitStatus(String orderNo, String payStatus, String remark);
}
