package com.chinasofti.huateng.collectticket.mapper;

import com.chinasofti.huateng.collectticket.entity.TicketCollectInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketCollectInfoMapper {

    TicketCollectInfo selectByOrderNo(@Param("orderNo") String orderNo);


    TicketCollectInfo selectByDeviceIdAndQrcodeDate(@Param("deviceId") String deviceId, 
                                                      @Param("qrcodeGenDate") String qrcodeGenDate);


    java.util.List<TicketCollectInfo> selectByUserId(@Param("userId") String userId);


    int insert(TicketCollectInfo record);


    int updateByOrderNo(TicketCollectInfo record);
}
