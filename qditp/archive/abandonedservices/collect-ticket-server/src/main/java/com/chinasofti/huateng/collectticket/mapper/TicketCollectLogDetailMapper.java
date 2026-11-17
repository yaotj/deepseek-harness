package com.chinasofti.huateng.collectticket.mapper;

import com.chinasofti.huateng.collectticket.entity.TicketCollectLogDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TicketCollectLogDetailMapper {

    java.util.List<TicketCollectLogDetail> selectByOrderNo(@Param("orderNo") String orderNo);


    int insert(TicketCollectLogDetail record);


    int insertBatch(@Param("list") java.util.List<TicketCollectLogDetail> list);
}
