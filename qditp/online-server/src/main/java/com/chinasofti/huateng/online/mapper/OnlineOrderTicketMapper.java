package com.chinasofti.huateng.online.mapper;

import com.chinasofti.huateng.online.entity.OnlineOrderTicket;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OnlineOrderTicketMapper {
    int insert(OnlineOrderTicket record);

    List<OnlineOrderTicket> selectByOrderNo(@Param("orderNo") String orderNo);

    int deleteByOrderNo(@Param("orderNo") String orderNo);
}
