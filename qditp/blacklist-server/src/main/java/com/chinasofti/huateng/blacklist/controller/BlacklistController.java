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

/**
 * 黑名单服务内部接口。
 */
@RestController
public class BlacklistController {
    private static final Logger log = LoggerFactory.getLogger(BlacklistController.class);

    @Autowired
    private BlacklistService blacklistService;

    /** 后台分页查询黑名单记录，支持卡ID、三方用户ID和创建时间范围。 */
    @GetMapping("/page/blacklist")
    public ResultVO<PageInfo<Blacklist>> page(@RequestParam(required = false) String cardId,
                                              @RequestParam(required = false) String thirdUserId,
                                              @RequestParam(required = false) String createTimeBegin,
                                              @RequestParam(required = false) String createTimeEnd,
                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return blacklistService.page(cardId, thirdUserId, createTimeBegin, createTimeEnd, pageNum, pageSize);
    }

    /** 后台新增黑名单，复用原业务逻辑以保留操作日志与支付宝渠道同步。 */
    @PostMapping("/page/blacklist")
    public ResultVO<Void> createForPage(@RequestBody AddBlackListReqDTO request) {
        return mapOperateResult(blacklistService.addBlackList(request));
    }

    /** 后台删除指定卡ID，复用原业务逻辑以保留操作日志与支付宝渠道同步。 */
    @DeleteMapping("/page/blacklist/{cardId}")
    public ResultVO<Void> deleteForPage(@PathVariable String cardId) {
        DeleteBlackListReqDTO request = new DeleteBlackListReqDTO();
        request.setCardId(cardId);
        return mapOperateResult(blacklistService.deleteBlackList(request));
    }

    private ResultVO<Void> mapOperateResult(BlackListOperateResult result) {
        // 内部业务接口使用 0000 协议；运营页面统一转换为 ResultVO 响应。
        if (result != null && "0000".equals(result.getRetCode())) {
            return ResultMapper.ok();
        }
        return ResultMapper.error(result == null ? "黑名单操作失败" : result.getRetMsg());
    }

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
