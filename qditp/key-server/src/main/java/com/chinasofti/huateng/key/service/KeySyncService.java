package com.chinasofti.huateng.key.service;

import com.chinasofti.huateng.model.app.RequestKeyListReqDTO;
import com.chinasofti.huateng.model.app.RequestKeyListResult;
import com.chinasofti.huateng.model.agm.RequestAgmSynKeyListReqDTO;
import com.chinasofti.huateng.model.agm.RequestAgmSynKeyListResult;


public interface KeySyncService {

    RequestKeyListResult requestKeyList(RequestKeyListReqDTO request);


    RequestKeyListResult requestHceKeyList(RequestKeyListReqDTO request);


    RequestAgmSynKeyListResult requestAgmSynKeyList(RequestAgmSynKeyListReqDTO request);
}
