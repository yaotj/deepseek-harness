package com.chinasofti.huateng.acc.es.server.mapper;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsProc;
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
public interface TblTktEsProcMapper {

    int deleteByPrimaryKey(@Param("taskNo") String taskNo, @Param("esCode") String esCode, @Param("fileSeqNo") String fileSeqNo);

    int insert(TblTktEsProc record);

    int insertSelective(TblTktEsProc record);

    TblTktEsProc selectByPrimaryKey(@Param("taskNo") String taskNo, @Param("esCode") String esCode, @Param("fileSeqNo") String fileSeqNo);

    int updateByPrimaryKeySelective(TblTktEsProc record);

    int updateByPrimaryKey(TblTktEsProc record);

    List<TblTktEsProc> queryAll(TblTktEsProc esProc);
}
