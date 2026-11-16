package com.chinasofti.huateng.acc.es.server.mapper;


import com.chinasofti.huateng.acc.es.server.model.TblTktPrePerson;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Entity model.TblTktPrePerson
 */
@Repository
public interface TblTktPrePersonMapper {

    /**
     * @mbg.generated
     */
    int insert(TblTktPrePerson record);

    /**
     * @mbg.generated
     */
    int insertSelective(TblTktPrePerson record);


    /**
     * @mbg.generated
     */
    int updateByPrimaryKeySelective(TblTktPrePerson record);

    /**
     * @mbg.generated
     */
    int updateByPrimaryKey(TblTktPrePerson record);


    List<TblTktPrePerson> selectSelective(TblTktPrePerson prePerson);

    TblTktPrePerson selectByPrimary(TblTktPrePerson p);
}