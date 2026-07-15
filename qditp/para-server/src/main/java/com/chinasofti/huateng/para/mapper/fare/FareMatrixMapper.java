package com.chinasofti.huateng.para.mapper.fare;

import com.chinasofti.huateng.para.entity.fare.FareMatrix;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FareMatrixMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<FareMatrix> list);
}
