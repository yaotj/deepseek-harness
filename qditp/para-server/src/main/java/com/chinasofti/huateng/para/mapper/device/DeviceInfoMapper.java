package com.chinasofti.huateng.para.mapper.device;

import com.chinasofti.huateng.para.entity.device.DeviceInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 设备信息参数表 TBL_DEVICE_INFO 的数据访问接口。 */
@Mapper
public interface DeviceInfoMapper {
    /** 按 ACC 同步字段幂等写入（MERGE INTO），本地管理字段 MANAGE_MODE / REMARK 不在更新列内。 */
    int merge(DeviceInfo record);

    /** 设备管理页分页查询，条件全部可选，空条件时由 <where> 产出无谓词查询。 */
    List<DeviceInfo> selectPage(@Param("lineId") String lineId,
                                @Param("stationId") String stationId,
                                @Param("deviceType") String deviceType,
                                @Param("manageMode") String manageMode,
                                @Param("keyword") String keyword,
                                @Param("includeDeleted") boolean includeDeleted);

    /** 批量更新业主自定义管理模式，仅命中给定设备编号。 */
    int updateManageMode(@Param("deviceIds") List<String> deviceIds,
                         @Param("manageMode") String manageMode);

    /** 查询指定设备编号的当前管理模式，供批量更新前校验存在性。 */
    List<DeviceInfo> selectByDeviceIds(@Param("deviceIds") List<String> deviceIds);
}
