package com.chinasofti.huateng.facepay.mapper;

import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusPageVO;
import com.chinasofti.huateng.facepay.api.page.F2fDeviceStatusSummaryVO;
import com.chinasofti.huateng.facepay.entity.F2fDeviceStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface F2fDeviceStatusMapper {

    int mergeHeartbeat(@Param("channel") String channel,
                       @Param("deviceId") String deviceId,
                       @Param("stationCode") String stationCode,
                       @Param("heartbeatTms") LocalDateTime heartbeatTms);

    F2fDeviceStatus selectByChannelAndDevice(@Param("channel") String channel,
                                            @Param("deviceId") String deviceId);

    List<F2fDeviceStatus> selectHeartbeatTimeout(@Param("deadline") LocalDateTime deadline,
                                                @Param("limit") int limit);

    int markOfflineByDevice(@Param("channel") String channel,
                           @Param("deviceId") String deviceId);

    int markOfflineByDeadline(@Param("deadline") LocalDateTime deadline);

    List<F2fDeviceStatus> selectByChannel(@Param("channel") String channel,
                                         @Param("onlineFlag") String onlineFlag);

    List<F2fDeviceStatusPageVO> selectDevicePage(@Param("channel") String channel,
                                                @Param("onlineFlag") String onlineFlag,
                                                @Param("deviceId") String deviceId,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    long countDevicePage(@Param("channel") String channel,
                         @Param("onlineFlag") String onlineFlag,
                         @Param("deviceId") String deviceId);

    F2fDeviceStatusSummaryVO selectDeviceSummary();
}
