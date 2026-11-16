package com.chinasofti.huateng.acc.es.server.controller;


import com.chinasofti.huateng.acc.es.server.model.TblTktEsAccount;
import com.chinasofti.huateng.acc.es.server.service.IEsAccountService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * @author rxwnc
 */
@RestController
@AllArgsConstructor
@RequestMapping("/account")
public class EsAccountController {

    private final IEsAccountService esAccountService;

    /**
     * 分页查询账户信息
     *
     * @param account
     * @return
     */
    @PostMapping("/page")
    public ResultVO<?> queryAccountByPage(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsAccount account) {
        return esAccountService.page(pageNum, pageSize, account);
    }

    /**
     * 新增es账号信息
     *
     * @param account
     * @return
     */
    @PostMapping("/save")
    public ResultVO<?> insertAccount(@RequestBody TblTktEsAccount account) {
        return esAccountService.save(account);
    }

    /**
     * 修改es账户
     *
     * @param account
     * @return
     */
    @PostMapping("/update")
    public ResultVO<?> updateAccount(@RequestBody TblTktEsAccount account) {
        return esAccountService.update(account);
    }

    /**
     * 通过账户删除
     *
     * @param
     * @return
     */
    @PostMapping("/delete")
    public ResultVO<?> deleteAccount(@RequestBody TblTktEsAccount account) {
        String username = account.getUsername();
        return esAccountService.deleteAccount(username);
    }
}
