package com.chinasofti.huateng.acc.es.server.controller;


import com.chinasofti.huateng.acc.es.server.model.TblTktEsAccount;
import com.chinasofti.huateng.acc.es.server.service.IEsAccountService;
import com.chinasofti.huateng.common.response.ResultVO;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@AllArgsConstructor
@RequestMapping("/account")
public class EsAccountController {

    private final IEsAccountService esAccountService;


    @PostMapping("/page")
    public ResultVO<?> queryAccountByPage(@RequestParam Integer pageNum, @RequestParam Integer pageSize, @RequestBody(required = false) TblTktEsAccount account) {
        return esAccountService.page(pageNum, pageSize, account);
    }


    @PostMapping("/save")
    public ResultVO<?> insertAccount(@RequestBody TblTktEsAccount account) {
        return esAccountService.save(account);
    }


    @PostMapping("/update")
    public ResultVO<?> updateAccount(@RequestBody TblTktEsAccount account) {
        return esAccountService.update(account);
    }


    @PostMapping("/delete")
    public ResultVO<?> deleteAccount(@RequestBody TblTktEsAccount account) {
        String username = account.getUsername();
        return esAccountService.deleteAccount(username);
    }
}
