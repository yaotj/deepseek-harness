package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsAccount;
import com.chinasofti.huateng.common.response.ResultVO;

/**
 * @author rxwnc
 */
public interface IEsAccountService {

    /**
     * 根据账号密码获取账户的类型  1，操作员   2，管理员
     *
     * @param account rule id
     * @return Integer
     */
    Integer getEsUserType(TblTktEsAccount account);

    /**
     * 分页查询编码机用户信息
     * @param account
     * @return
     */
    ResultVO<?> page(Integer pageNum ,Integer pageSize,TblTktEsAccount account);

    /**
     * 保存用户
     * @param account
     * @return
     */
    ResultVO<?> save(TblTktEsAccount account);

    /**
     * 修改用户
     * @param account
     * @return
     */
    ResultVO<?> update(TblTktEsAccount account);

    /**
     * 删除用户
     * @param username
     * @return
     */
    ResultVO<?> deleteAccount(String username);
}
