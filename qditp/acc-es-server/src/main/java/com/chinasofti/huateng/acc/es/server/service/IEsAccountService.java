package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsAccount;
import com.chinasofti.huateng.common.response.ResultVO;


public interface IEsAccountService {


    Integer getEsUserType(TblTktEsAccount account);


    ResultVO<?> page(Integer pageNum ,Integer pageSize,TblTktEsAccount account);


    ResultVO<?> save(TblTktEsAccount account);


    ResultVO<?> update(TblTktEsAccount account);


    ResultVO<?> deleteAccount(String username);
}
