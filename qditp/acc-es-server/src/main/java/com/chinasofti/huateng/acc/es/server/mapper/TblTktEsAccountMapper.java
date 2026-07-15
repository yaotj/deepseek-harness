package com.chinasofti.huateng.acc.es.server.mapper;


import com.chinasofti.huateng.acc.es.server.model.TblTktEsAccount;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author rxwnc
 * @Entity generate.TblStlEsAccount
 */
@Mapper
public interface TblTktEsAccountMapper {
    /**
     * @mbg.generated
     */
    int deleteByPrimaryKey(String username);

    /**
     * @mbg.generated
     */
    int insert(TblTktEsAccount record);

    /**
     * @mbg.generated
     */
    int insertSelective(TblTktEsAccount record);

    /**
     * @mbg.generated
     */
    TblTktEsAccount selectByPrimaryKey(String username);

    /**
     * @mbg.generated
     */
    int updateByPrimaryKeySelective(TblTktEsAccount record);

    /**
     * @mbg.generated
     */
    int updateByPrimaryKey(TblTktEsAccount record);

    /**
     *
     * @param account
     * @return Integer
     */
    Integer getEsUserTypeByUsernameAndPassword(TblTktEsAccount account);

    List<TblTktEsAccount> queryAccountByPage(TblTktEsAccount account);
}