package com.chinasofti.huateng.acc.es.server.mapper;


import com.chinasofti.huateng.acc.es.server.model.TblStlPersonInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Entity generate.TblStlPersonInfo
 */
@Mapper
public interface TblStlPersonInfoMapper {
    /**
     * @mbg.generated
     */
    int deleteByPrimaryKey(String ticketId);

    /**
     * @mbg.generated
     */
    int insert(TblStlPersonInfo record);

    /**
     * @mbg.generated
     */
    int insertSelective(TblStlPersonInfo record);

    /**
     * @mbg.generated
     */
    TblStlPersonInfo selectByPrimaryKey(String ticketId);

    /**
     * @mbg.generated
     */
    int updateByPrimaryKeySelective(TblStlPersonInfo record);

    /**
     * @mbg.generated
     */
    int updateByPrimaryKeyWithBLOBs(TblStlPersonInfo record);

    /**
     * @mbg.generated
     */
    int updateByPrimaryKey(TblStlPersonInfo record);
}