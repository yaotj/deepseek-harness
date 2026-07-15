package com.chinasofti.huateng.collectticket.mapper;

import com.chinasofti.huateng.collectticket.entity.TicketCollectInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketCollectInfoMapper {
    /**
     * 根据订单号查询取票订单信息。
     */
    TicketCollectInfo selectByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 根据设备ID和二维码生成时间查询取票订单信息。
     */
    TicketCollectInfo selectByDeviceIdAndQrcodeDate(@Param("deviceId") String deviceId, 
                                                      @Param("qrcodeGenDate") String qrcodeGenDate);

    /**
     * 根据用户ID查询取票订单信息列表。
     */
    java.util.List<TicketCollectInfo> selectByUserId(@Param("userId") String userId);

    /**
     * 插入取票订单信息。
     */
    int insert(TicketCollectInfo record);

    /**
     * 更新取票订单信息。
     */
    int updateByOrderNo(TicketCollectInfo record);
}
