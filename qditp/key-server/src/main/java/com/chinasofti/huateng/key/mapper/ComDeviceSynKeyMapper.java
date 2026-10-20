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

    /**
     * 分页查询设备最新密钥同步状态。
     *
     * @param deviceId    设备编号（可选，模糊）
     * @param stationCode 车站编码（可选，模糊）
     * @param status      同步状态（可选，SUCCESS/FAIL）
     * @param offset      偏移量
     * @param limit       每页条数
     * @return 视图列表
     */
    List<DeviceKeySyncView> selectList(@Param("deviceId") String deviceId,
                                       @Param("stationCode") String stationCode,
                                       @Param("status") String status,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    /**
     * 统计符合条件的设备数量。
     */
    long count(@Param("deviceId") String deviceId,
               @Param("stationCode") String stationCode,
               @Param("status") String status);
}
