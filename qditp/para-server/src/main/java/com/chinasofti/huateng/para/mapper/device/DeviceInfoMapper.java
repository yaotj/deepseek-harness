package com.chinasofti.huateng.para.mapper.device;

import com.chinasofti.huateng.para.entity.device.DeviceInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DeviceInfoMapper {
    int merge(DeviceInfo record);

    List<DeviceInfo> selectPage(@Param("lineId") String lineId,
                                @Param("stationId") String stationId,
                                @Param("deviceType") String deviceType,
                                @Param("manageMode") String manageMode,
                                @Param("keyword") String keyword,
                                @Param("includeDeleted") boolean includeDeleted);

    int updateManageMode(@Param("deviceIds") List<String> deviceIds,
                         @Param("manageMode") String manageMode);

    List<DeviceInfo> selectByDeviceIds(@Param("deviceIds") List<String> deviceIds);
}
