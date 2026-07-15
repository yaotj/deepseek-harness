package com.chinasofti.huateng.collectpay.mapper;

import com.chinasofti.huateng.collectpay.entity.TvmTakeTicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

@Mapper
public interface TvmTakeTicketOrderMapper {
    /**
     * 根据订单号和设备查询取票订单。
     */
    TvmTakeTicketOrder selectByOrderNoAndDevice(@Param("orderNo") String orderNo, @Param("deviceId") String deviceId);

    /**
     * 根据设备ID和二维码信息查询取票订单。
     */
    TvmTakeTicketOrder selectByDeviceAndQrcode(@Param("deviceId") String deviceId, 
                                                @Param("qrcodeGenDate") String qrcodeGenDate, 
                                                @Param("randomFact") String randomFact);

    /**
     * 插入取票订单。
     */
    int insert(TvmTakeTicketOrder record);

    /**
     * 根据订单号和设备更新激活状态。
     */
    int updateActiveStatusByOrderNoAndDevice(Map<String, Object> params);
}
