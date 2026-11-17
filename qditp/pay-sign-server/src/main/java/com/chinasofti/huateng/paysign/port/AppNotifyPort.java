package com.chinasofti.huateng.paysign.port;

import com.chinasofti.huateng.model.app.AppSignResultNotifyReqDTO;
import com.chinasofti.huateng.model.app.AppTerminationResultNotifyReqDTO;


public interface AppNotifyPort {

    NotifyDelivery pushSignResult(AppSignResultNotifyReqDTO bizData);

    NotifyDelivery pushTerminationResult(AppTerminationResultNotifyReqDTO bizData);
}
