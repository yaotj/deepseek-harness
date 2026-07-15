package com.chinasofti.huateng.acc.es.server.service.impl;

import com.chinasofti.huateng.acc.es.server.mapper.TblTktEsProcMapper;
import com.chinasofti.huateng.acc.es.server.model.TblTktEsProc;
import com.chinasofti.huateng.acc.es.server.service.IEsProcService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EsProcServiceImpl implements IEsProcService {

    @Autowired
    private TblTktEsProcMapper esProcMapper;

    @Value("${pageConfig.defaultSize:10}")
    private int defaultPageSize;

    @Override
    public ResultVO<?> selectPage(Integer pageNum, Integer pageSize, TblTktEsProc esProc) {
        TblTktEsProc query = esProc == null ? new TblTktEsProc() : esProc;
        PageInfo<TblTktEsProc> result = PageHelper.startPage(pageNum, pageSize).doSelectPageInfo(() -> {
            esProcMapper.queryAll(query);
        });
        return ResultMapper.ok(result);
    }

    @Override
    public int save(TblTktEsProc esProc) {
        return esProcMapper.insert(esProc);
    }
}
