package com.chinasofti.huateng.key.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.page.DeviceKeySyncPageResult;


public interface DeviceKeySyncQueryService {


    ResultVO<DeviceKeySyncPageResult> query(String deviceId,
                                            String stationCode,
                                            String status,
                                            int pageNum,
                                            int pageSize);
}
