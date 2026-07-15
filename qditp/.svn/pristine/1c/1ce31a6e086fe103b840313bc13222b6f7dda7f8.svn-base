package com.chinasofti.huateng.online.mapper;

import com.chinasofti.huateng.online.entity.OnlineOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface OnlineOrderMapper {
    OnlineOrder selectByOrderNo(@Param("orderNo") String orderNo);

    OnlineOrder selectByTakeTicketCode(@Param("deviceId") String deviceId,
                                       @Param("qrcodeGenDate") LocalDateTime qrcodeGenDate,
                                       @Param("randomFact") String randomFact);

    int insert(OnlineOrder record);

    int updateByOrderNo(OnlineOrder record);
}
