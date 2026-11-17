package com.chinasofti.huateng.blacklist.controller;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.service.BlacklistService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageInfo;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.DeleteBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListReqDTO;
import com.chinasofti.huateng.model.app.QueryBlackListResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class BlacklistController {
    private static final Logger log = LoggerFactory.getLogger(BlacklistController.class);

    @Autowired
    private BlacklistService blacklistService;

    @GetMapping("/page/blacklist")
    public ResultVO<PageInfo<Blacklist>> page(@RequestParam(required = false) String cardId,
                                              @RequestParam(required = false) String thirdUserId,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String channelSyncStatus,
                                              @RequestParam(required = false) String createTimeBegin,
                                              @RequestParam(required = false) String createTimeEnd,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return blacklistService.page(cardId, thirdUserId, status, channelSyncStatus,
                createTimeBegin, createTimeEnd, pageNum, pageSize);
    }

    @PostMapping("/page/blacklist")
    public ResultVO<Void> createForPage(@RequestBody AddBlackListReqDTO request) {
        if (request != null) {
            request.setBlackSource("09");
        }
        return mapOperateResult(blacklistService.addBlackList(request));
    }

    @DeleteMapping("/page/blacklist/{cardId}")
    public ResultVO<Void> deleteForPage(@PathVariable String cardId,
                                        @RequestParam(required = false) String releaseReason,
                                        @RequestParam(required = false) String releaseBy) {
        DeleteBlackListReqDTO request = new DeleteBlackListReqDTO();
        request.setCardId(cardId);
        request.setReleaseReason(releaseReason);
        request.setReleaseBy(releaseBy);
        return mapOperateResult(blacklistService.deleteBlackList(request));
    }

    private ResultVO<Void> mapOperateResult(BlackListOperateResult result) {
        if (result != null && "0000".equals(result.getRetCode())) {
            return ResultMapper.ok();
        }
        return ResultMapper.error(result == null ? "黑名单操作失败" : result.getRetMsg());
    }


    @PostMapping("/queryBlackList")
    public QueryBlackListResult queryBlackList(@RequestBody QueryBlackListReqDTO request) {
        log.info("接收到查询黑名单接口报文, cardId: {}", request == null ? null : request.getCardId());
        return blacklistService.queryBlackList(request);
    }


    @PostMapping("/addBlackList")
    public BlackListOperateResult addBlackList(@RequestBody AddBlackListReqDTO request) {
        log.info("接收到新增黑名单接口报文, cardId: {}", request == null ? null : request.getCardId());
        return blacklistService.addBlackList(request);
    }


    @PostMapping("/deleteBlackList")
    public BlackListOperateResult deleteBlackList(@RequestBody DeleteBlackListReqDTO request) {
        log.info("接收到删除黑名单接口报文, cardId: {}", request == null ? null : request.getCardId());
        return blacklistService.deleteBlackList(request);
    }
}
