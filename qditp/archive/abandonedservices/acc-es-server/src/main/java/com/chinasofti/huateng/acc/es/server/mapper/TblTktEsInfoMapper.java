package com.chinasofti.huateng.acc.es.server.mapper;


import com.chinasofti.huateng.acc.es.server.model.TblTktEsInfo;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceSignOut;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceStatReport;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;


@Mapper
public interface TblTktEsInfoMapper{


    boolean signOut(DeviceSignOut deviceSignOut);

    int updateStat(DeviceStatReport stat);

    int deleteByPrimaryKey(String esCode);

    int insert(TblTktEsInfo record);

    int insertSelective(TblTktEsInfo record);

    TblTktEsInfo selectByPrimaryKey(String esCode);

    int updateByPrimaryKeySelective(TblTktEsInfo record);

    int updateByPrimaryKey(TblTktEsInfo record);

    List<TblTktEsInfo> queryAll(TblTktEsInfo esInfo);

    List<TblTktEsInfo> selectAll();
}
