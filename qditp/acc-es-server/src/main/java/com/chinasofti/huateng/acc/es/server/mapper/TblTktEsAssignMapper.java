package com.chinasofti.huateng.acc.es.server.mapper;


import com.chinasofti.huateng.acc.es.server.model.TblTktEsAssign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */
@Mapper
public interface TblTktEsAssignMapper {

    TblTktEsAssign queryById(@Param("taskNo") Integer taskNo, @Param("esCode") String esCode);

    int updateByTaskNoAndEsCode(TblTktEsAssign assign);

    int deleteByPrimaryKey(@Param("taskNo") Integer taskNo,@Param("esCode") String esCode);

    int insert(TblTktEsAssign record);

    int insertSelective(TblTktEsAssign record);

    TblTktEsAssign selectByPrimaryKey(@Param("taskNo") Integer taskNo,@Param("esCode") String esCode);

    int updateByPrimaryKeySelective(TblTktEsAssign record);

    int updateByPrimaryKey(TblTktEsAssign record);

    TblTktEsAssign selectByTaskNo(Integer taskNo);

    int deleteByTaskNo(Integer taskNo);

    int deleteByPlanNo(Integer planNo);
}
