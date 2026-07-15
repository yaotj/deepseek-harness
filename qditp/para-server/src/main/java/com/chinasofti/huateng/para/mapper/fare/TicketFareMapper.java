package com.chinasofti.huateng.para.mapper.fare;

import com.chinasofti.huateng.para.entity.fare.TicketFare;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketFareMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<TicketFare> list);
}
