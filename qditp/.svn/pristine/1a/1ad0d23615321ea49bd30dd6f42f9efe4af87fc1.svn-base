package com.chinasofti.huateng.acc.es.server.mapper;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */
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
