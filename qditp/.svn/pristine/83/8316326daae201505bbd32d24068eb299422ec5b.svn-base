package com.chinasofti.huateng.acc.es.server.service.impl;

import com.chinasofti.huateng.acc.es.server.mapper.TblTktEsAccountMapper;
import com.chinasofti.huateng.acc.es.server.model.TblTktEsAccount;
import com.chinasofti.huateng.acc.es.server.service.IEsAccountService;
import com.chinasofti.huateng.acc.es.server.util.DateUtil;
import com.chinasofti.huateng.acc.es.server.util.Md5Util;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * @author rxwnc
 */
@Service
@Slf4j
public class EsAccountServiceImpl implements IEsAccountService {

    @Autowired
    private TblTktEsAccountMapper esAccountMapper;

    @Override
    public Integer getEsUserType(TblTktEsAccount account) {
        log.info("传入的数据：账号：{}", account.getUsername());
        return esAccountMapper.getEsUserTypeByUsernameAndPassword(account);
    }

    @Override
    public ResultVO<?> page(Integer pageNum, Integer pageSize, TblTktEsAccount account) {
        TblTktEsAccount query = account == null ? new TblTktEsAccount() : account;

        PageInfo<TblTktEsAccount> accounts = PageHelper.startPage(pageNum, pageSize).doSelectPageInfo(() -> {
            esAccountMapper.queryAccountByPage(query);
        });
        return ResultMapper.ok(accounts);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<?> save(TblTktEsAccount account) {
        TblTktEsAccount account1 = esAccountMapper.selectByPrimaryKey(account.getUsername());
        if (!Objects.isNull(account1)) {
            return ResultMapper.error("用户已经存在");
        }
        String md5 = Md5Util.string2MD5(account.getUsername() + account.getPassword()).toUpperCase();
        account.setPassword(md5);
        account.setAddDate(DateUtil.dateString8());
        esAccountMapper.insertSelective(account);
        return ResultMapper.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<?> update(TblTktEsAccount account) {
        String md5 = Md5Util.string2MD5(account.getUsername() + account.getPassword()).toUpperCase();
        account.setPassword(md5);
        account.setUpdateDate(DateUtil.dateString8());
        esAccountMapper.updateByPrimaryKeySelective(account);
        return ResultMapper.ok();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultVO<?> deleteAccount(String username) {
        esAccountMapper.deleteByPrimaryKey(username);
        return ResultMapper.ok();
    }
}
