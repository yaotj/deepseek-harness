package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsInfo;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceSignIn;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceSignOut;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceStatReport;
import com.chinasofti.huateng.common.response.ResultVO;

public interface IEsInfoService {

    /**
     * 设备签到
     *
     * @param deviceSignIn
     * @return
     */
    boolean esSignIn(DeviceSignIn deviceSignIn);

    /**
     * 添加ES设备
     *
     * @param info
     * @return
     */
    ResultVO<?> saveOne(TblTktEsInfo info);

    /**
     * 通过EsCode修改es信息
     *
     * @param info
     * @return
     */
    ResultVO<?> updateByEsCode(TblTktEsInfo info);

    /**
     * 查询所有es
     *
     * @return
     */
    ResultVO<?> page(Integer pageNum, Integer pageSize, TblTktEsInfo esInfo);

    /**
     * 通过esCode删除编码机
     *
     * @param esCode
     * @return
     */
    ResultVO<?> deleteOne(String esCode);

    /**
     * 编码分拣机签退
     *
     * @param deviceSignOut
     * @return
     */
    boolean esSignOut(DeviceSignOut deviceSignOut);

    /**
     * 更新分拣编码机状态
     *
     * @param stat
     * @return
     */
    int updateEsStat(DeviceStatReport stat);

    /**
     * 获取所有的编码机信息
     *
     * @return
     */
    ResultVO<?> getAllESInfo();
}
