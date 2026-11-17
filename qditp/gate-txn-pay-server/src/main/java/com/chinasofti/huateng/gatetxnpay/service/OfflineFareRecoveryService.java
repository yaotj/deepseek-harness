package com.chinasofti.huateng.gatetxnpay.service;

public interface OfflineFareRecoveryService {


    int recoverOfflineFarePendingOrders(int limit, int lookbackDays);
}
