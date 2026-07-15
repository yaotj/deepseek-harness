package com.chinasofti.huateng.para.mapper.ticket;

import com.chinasofti.huateng.para.entity.ticket.TotalSalePart;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TotalSalePartMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<TotalSalePart> list);
}
