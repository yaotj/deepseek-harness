package com.chinasofti.huateng.key.mapper;

import com.chinasofti.huateng.key.entity.ComDeviceSynKey;
import com.chinasofti.huateng.key.page.DeviceKeySyncView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Component;

import java.util.List;

@Mapper
@Component
public interface ComDeviceSynKeyMapper {
    int merge(ComDeviceSynKey deviceSynKey);


    List<DeviceKeySyncView> selectList(@Param("deviceId") String deviceId,
                                       @Param("stationCode") String stationCode,
                                       @Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);


    long count(@Param("deviceId") String deviceId,
               @Param("stationCode") String stationCode,
               @Param("status") String status);
}
