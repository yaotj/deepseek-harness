package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktPrePerson;
import com.chinasofti.huateng.common.response.ResultVO;
import java.util.List;

public interface ITblTktPrePersonService {

    ResultVO<?> insertPersonInfoBatch(Integer taskNo, String fileName);

    List<TblTktPrePerson> selectSelective(TblTktPrePerson prePerson);

    TblTktPrePerson selectByPrimary(TblTktPrePerson p);
}
