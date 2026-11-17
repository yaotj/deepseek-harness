package com.chinasofti.huateng.acc.es.server.mapper;


import com.chinasofti.huateng.acc.es.server.model.TblTktPrePerson;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface TblTktPrePersonMapper {


    int insert(TblTktPrePerson record);


    int insertSelective(TblTktPrePerson record);



    int updateByPrimaryKeySelective(TblTktPrePerson record);


    int updateByPrimaryKey(TblTktPrePerson record);


    List<TblTktPrePerson> selectSelective(TblTktPrePerson prePerson);

    TblTktPrePerson selectByPrimary(TblTktPrePerson p);
}