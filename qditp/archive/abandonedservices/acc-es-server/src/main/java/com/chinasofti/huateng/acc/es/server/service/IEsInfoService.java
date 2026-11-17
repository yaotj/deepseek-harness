package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsInfo;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceSignIn;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceSignOut;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceStatReport;
import com.chinasofti.huateng.common.response.ResultVO;

public interface IEsInfoService {


    boolean esSignIn(DeviceSignIn deviceSignIn);


    ResultVO<?> saveOne(TblTktEsInfo info);


    ResultVO<?> updateByEsCode(TblTktEsInfo info);


    ResultVO<?> page(Integer pageNum, Integer pageSize, TblTktEsInfo esInfo);


    ResultVO<?> deleteOne(String esCode);


    boolean esSignOut(DeviceSignOut deviceSignOut);


    int updateEsStat(DeviceStatReport stat);


    ResultVO<?> getAllESInfo();
}
