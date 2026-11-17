package com.chinasofti.huateng.acc.es.server.mapper;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;


@Mapper
public interface TblTktEsReportMapper {

    int deleteByPrimaryKey(@Param("taskNo") String taskNo, @Param("esCode") String esCode);

    int insert(TblTktEsReport record);

    int insertSelective(TblTktEsReport record);

    TblTktEsReport selectByPrimaryKey(@Param("taskNo") String taskNo, @Param("esCode") String esCode);

    int updateByPrimaryKeySelective(TblTktEsReport record);

    int updateByPrimaryKey(TblTktEsReport record);

    List<TblTktEsReport> quaryAll(TblTktEsReport esReport);
}
