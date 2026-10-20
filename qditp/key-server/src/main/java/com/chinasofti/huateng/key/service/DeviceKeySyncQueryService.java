package com.chinasofti.huateng.key.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.page.DeviceKeySyncPageResult;

/**
 * 综管台「设备密钥同步」查询服务。
 */
public interface DeviceKeySyncQueryService {

    /**
     * 分页查询设备最新密钥同步状态。
     *
     * @param deviceId    设备编号（可选，模糊）
     * @param stationCode 车站编码（可选，模糊）
     * @param status      同步状态（可选）
     * @param pageNum     页码，从 1 开始
     * @param pageSize    每页条数
     * @return 分页结果
     */
    ResultVO<DeviceKeySyncPageResult> query(String deviceId,
                                            String stationCode,
                                            String status,
                                            int pageNum,
                                            int pageSize);
}
