package com.chinasofti.huateng.acc.es.server.mapper;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsFileProcLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


@Mapper
public interface TblTktEsFileProcLogMapper {

    int deleteByPrimaryKey(@Param("taskNo") Integer taskNo, @Param("esCode") String esCode, @Param("recordNo") Integer recordNo);

    int insert(TblTktEsFileProcLog record);

    int insertSelective(TblTktEsFileProcLog record);

    TblTktEsFileProcLog selectByPrimaryKey(@Param("taskNo") Integer taskNo, @Param("esCode") String esCode, @Param("recordNo") Integer recordNo);

    int updateByPrimaryKeySelective(TblTktEsFileProcLog record);

    int updateByPrimaryKey(TblTktEsFileProcLog record);


}
