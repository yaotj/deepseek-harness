package com.chinasofti.huateng.key.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.mapper.ComDeviceSynKeyMapper;
import com.chinasofti.huateng.key.page.DeviceKeySyncPageResult;
import com.chinasofti.huateng.key.page.DeviceKeySyncView;
import com.chinasofti.huateng.key.service.DeviceKeySyncQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 综管台「设备密钥同步」查询实现。
 */
@Service
public class DeviceKeySyncQueryServiceImpl implements DeviceKeySyncQueryService {

    private static final Logger log = LoggerFactory.getLogger(DeviceKeySyncQueryServiceImpl.class);

    private final ComDeviceSynKeyMapper comDeviceSynKeyMapper;

    public DeviceKeySyncQueryServiceImpl(ComDeviceSynKeyMapper comDeviceSynKeyMapper) {
        this.comDeviceSynKeyMapper = comDeviceSynKeyMapper;
    }

    @Override
    public ResultVO<DeviceKeySyncPageResult> query(String deviceId,
                                                   String stationCode,
                                                   String status,
                                                   int pageNum,
                                                   int pageSize) {
        int normalizedPageNum = Math.max(pageNum, 1);
        int normalizedPageSize = Math.max(Math.min(pageSize, 500), 1);
        int offset = (normalizedPageNum - 1) * normalizedPageSize;

        try {
            List<DeviceKeySyncView> list = comDeviceSynKeyMapper.selectList(
                    trimToNull(deviceId), trimToNull(stationCode), trimToNull(status), offset, normalizedPageSize);
            long total = comDeviceSynKeyMapper.count(
                    trimToNull(deviceId), trimToNull(stationCode), trimToNull(status));

            DeviceKeySyncPageResult result = new DeviceKeySyncPageResult();
            result.setList(list);
            result.setTotal(total);
            return ResultMapper.ok(result);
        } catch (Exception e) {
            log.error("设备密钥同步查询失败, deviceId={}, stationCode={}, status={}", deviceId, stationCode, status, e);
            return ResultMapper.error("设备密钥同步查询失败");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
