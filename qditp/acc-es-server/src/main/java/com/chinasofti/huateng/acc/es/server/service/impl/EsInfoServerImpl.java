package com.chinasofti.huateng.acc.es.server.service.impl;

import com.chinasofti.huateng.acc.es.server.enumns.LoginStat;
import com.chinasofti.huateng.acc.es.server.mapper.TblTktEsInfoMapper;
import com.chinasofti.huateng.acc.es.server.model.TblTktEsInfo;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceSignIn;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceSignOut;
import com.chinasofti.huateng.acc.es.server.netty.data.DeviceStatReport;
import com.chinasofti.huateng.acc.es.server.service.IEsInfoService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @program: cloud-acc-server
 * @description: ES设备信息管理
 * @author: fc
 * @create: 2020-09-18 10:47
 */
@Service
public class EsInfoServerImpl implements IEsInfoService {

    @Autowired
    private TblTktEsInfoMapper esInfoMapper;

    @Value("${spring.application.name}")
    private String serverName;

    @Value("${pageConfig.defaultSize:10}")
    private int defaultPageSize;

    @Override
    public boolean esSignIn(DeviceSignIn deviceSignIn) {
        boolean isSingn=false;
        // 更新设备登录状态
        TblTktEsInfo esInfo=new TblTktEsInfo();
        esInfo.setLoginStat(LoginStat.SIGN_IN.getKey());
        esInfo.setEsCode(deviceSignIn.getEsNodeId());
        esInfo.setLoginUser(deviceSignIn.getOperatorCode());
        int  com=esInfoMapper.updateByPrimaryKeySelective(esInfo);
        if(com>0) {
            isSingn=true;
        }
        return isSingn;
    }

    @Override
    public ResultVO<?> saveOne(TblTktEsInfo info) {
        info.setLastUpdId(serverName);
        info.setLastUpdTms(null);
        esInfoMapper.insert(info);
        return ResultMapper.ok();
    }


    @Override
    public ResultVO<?> updateByEsCode(TblTktEsInfo info) {
        info.setLastUpdId(serverName);
        info.setLastUpdTms(null);
        esInfoMapper.updateByPrimaryKey(info);
        return ResultMapper.ok();
    }

    @Override
    public ResultVO<?> page(Integer pageNum,Integer pageSize, TblTktEsInfo esInfo) {
        TblTktEsInfo query = esInfo == null ? new TblTktEsInfo() : esInfo;

        PageInfo<TblTktEsInfo> pageInfo = PageHelper.startPage(pageNum, pageSize).doSelectPageInfo(() -> {
            esInfoMapper.queryAll(query);
        });
        return ResultMapper.ok(pageInfo);
    }

    @Override
    public ResultVO<?> deleteOne(String esCode) {
        esInfoMapper.deleteByPrimaryKey(esCode);
        return ResultMapper.ok();
    }

    @Override
    public boolean esSignOut(DeviceSignOut deviceSignOut) {
        return esInfoMapper.signOut(deviceSignOut);
    }

    @Override
    public int updateEsStat(DeviceStatReport stat) {
        return esInfoMapper.updateStat(stat);
    }

    @Override
    public ResultVO<?> getAllESInfo() {
        return ResultMapper.ok(esInfoMapper.selectAll());
    }


}
