package com.chinasofti.huateng.para.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.para.entity.device.DeviceInfo;
import com.chinasofti.huateng.para.mapper.device.DeviceInfoMapper;
import com.chinasofti.huateng.para.model.device.DeviceSyncResult;
import com.chinasofti.huateng.para.service.AccDeviceSyncService;
import com.chinasofti.huateng.para.service.DeviceInfoService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 设备信息参数实现：页面输入统一在此裁剪校验，管理模式码值用白名单校验。 */
@Service
public class DeviceInfoServiceImpl implements DeviceInfoService {

    /** 业主自定义管理模式白名单：与 CK_TBL_DEVICE_INFO_MODE 约束一致。 */
    private static final Set<String> MANAGE_MODE_WHITELIST = Set.of("01", "02");

    /** 单次批量设置管理模式的最大设备数，防止误操作整表刷写。 */
    private static final int MAX_BATCH_SIZE = 1000;

    private final DeviceInfoMapper deviceInfoMapper;
    private final AccDeviceSyncService accDeviceSyncService;

    public DeviceInfoServiceImpl(DeviceInfoMapper deviceInfoMapper, AccDeviceSyncService accDeviceSyncService) {
        this.deviceInfoMapper = deviceInfoMapper;
        this.accDeviceSyncService = accDeviceSyncService;
    }

    @Override
    public ResultVO<PageInfo<DeviceInfo>> pageDevices(String lineId, String stationId, String deviceType,
                                                      String manageMode, String keyword, boolean includeDeleted,
                                                      Integer pageNum, Integer pageSize) {
        PageInfo<DeviceInfo> page = PageHelper.startPage(safePageNum(pageNum), safePageSize(pageSize))
                .doSelectPageInfo(() -> deviceInfoMapper.selectPage(trimToNull(lineId), trimToNull(stationId),
                        trimToNull(deviceType), trimToNull(manageMode), trimToNull(keyword), includeDeleted));
        return ResultMapper.ok(page);
    }

    @Override
    public ResultVO<Integer> updateManageMode(List<String> deviceIds, String manageMode) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return ResultMapper.illegalParams("请先勾选要设置的设备");
        }
        if (deviceIds.size() > MAX_BATCH_SIZE) {
            return ResultMapper.illegalParams("单次最多设置" + MAX_BATCH_SIZE + "台设备");
        }
        String mode = trimToNull(manageMode);
        if (mode == null || !MANAGE_MODE_WHITELIST.contains(mode)) {
            return ResultMapper.illegalParams("管理模式码值非法，仅允许 01 正常使用 / 02 节能关机");
        }
        List<String> ids = deviceIds.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return ResultMapper.illegalParams("设备编号不能为空");
        }
        List<DeviceInfo> existing = deviceInfoMapper.selectByDeviceIds(ids);
        if (existing.size() != ids.size()) {
            List<String> missing = new ArrayList<>(ids);
            existing.forEach(item -> missing.remove(item.getDeviceId()));
            return ResultMapper.error("以下设备编号不存在: " + String.join(", ", missing));
        }
        int updated = deviceInfoMapper.updateManageMode(ids, mode);
        return ResultMapper.ok(updated);
    }

    @Override
    public ResultVO<DeviceSyncResult> syncFromAcc() {
        return ResultMapper.ok(accDeviceSyncService.syncAll());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private int safePageNum(Integer pageNum) {
        return (pageNum == null || pageNum < 1) ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) return 10;
        return Math.min(pageSize, 200);
    }
}
