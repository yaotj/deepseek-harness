package com.chinasofti.huateng.blacklist.controller;

import com.chinasofti.huateng.blacklist.service.BlacklistService;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.DeleteBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 黑名单服务内部接口。
 */
@RestController
public class BlacklistController {
    private static final Logger log = LoggerFactory.getLogger(BlacklistController.class);

    @Autowired
    private BlacklistService blacklistService;

    /**
     * 查询卡号是否命中黑名单。
     *
     * @param request 查询黑名单请求参数
     * @return 查询黑名单结果
     */
    @PostMapping("/queryBlackList")
    public QueryBlackListResult queryBlackList(@RequestBody QueryBlackListReqDTO request) {
        log.info("接收到查询黑名单接口报文, cardId: {}", request == null ? null : request.getCardId());
        return blacklistService.queryBlackList(request);
    }

    /**
     * 新增黑名单。
     *
     * @param request 新增黑名单请求参数
     * @return 黑名单操作结果
     */
    @PostMapping("/addBlackList")
    public BlackListOperateResult addBlackList(@RequestBody AddBlackListReqDTO request) {
        log.info("接收到新增黑名单接口报文, cardId: {}", request == null ? null : request.getCardId());
        return blacklistService.addBlackList(request);
    }

    /**
     * 物理删除黑名单。
     *
     * @param request 删除黑名单请求参数
     * @return 黑名单操作结果
     */
    @PostMapping("/deleteBlackList")
    public BlackListOperateResult deleteBlackList(@RequestBody DeleteBlackListReqDTO request) {
        log.info("接收到删除黑名单接口报文, cardId: {}", request == null ? null : request.getCardId());
        return blacklistService.deleteBlackList(request);
    }
}
