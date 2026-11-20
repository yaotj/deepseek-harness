package com.chinasofti.huateng.para.mapper.network;

import com.chinasofti.huateng.para.entity.network.LineInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LineInfoMapper {
    int deleteByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    int insertBatch(@Param("list") List<LineInfo> list);


    List<LineInfo> selectByParaVerNo(@Param("paraVerNo") Long paraVerNo);

    List<LineInfo> selectByParaVerNoAndCondition(
            @Param("paraVerNo") Long paraVerNo,
            @Param("lineCode") String lineCode,
            @Param("lineNm") String lineNm);
}
