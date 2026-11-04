package com.chinasofti.huateng.blacklist.controller;

import com.chinasofti.huateng.blacklist.entity.Graylist;
import com.chinasofti.huateng.blacklist.entity.GraylistOperateLog;
import com.chinasofti.huateng.blacklist.service.GraylistService;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 灰名单运营端接口（对标旧 ITP「灰名单管理」，观察/预警名单，只进流水不进拦截）。
 */
@RestController
public class GraylistController {

    @Autowired
    private GraylistService graylistService;

    /** 后台分页查询灰名单，支持卡ID、三方用户ID与进灰时间范围。 */
    @GetMapping("/page/graylist")
    public ResultVO<PageInfo<Graylist>> page(@RequestParam(required = false) String cardId,
                                             @RequestParam(required = false) String thirdUserId,
                                             @RequestParam(required = false) String createTimeBegin,
                                             @RequestParam(required = false) String createTimeEnd,
                                             @RequestParam(defaultValue = "1") Integer pageNum,
                                             @RequestParam(defaultValue = "10") Integer pageSize) {
        return graylistService.page(cardId, thirdUserId, createTimeBegin, createTimeEnd, pageNum, pageSize);
    }

    /** 后台进灰（运维手工，发起方服务端定死 09），body 为 cardId/thirdUserId/cardType/bizNo/reason/operator。 */
    @PostMapping("/page/graylist")
    public ResultVO<Void> createForPage(@RequestBody Map<String, String> body) {
        if (body == null) {
            return graylistService.addGray(null, null, null, null, null, null);
        }
        return graylistService.addGray(body.get("cardId"), body.get("thirdUserId"), body.get("cardType"),
                body.get("bizNo"), body.get("reason"), body.get("operator"));
    }

    /** 后台移出灰名单，reason/operator 记录流水。 */
    @DeleteMapping("/page/graylist/{cardId}")
    public ResultVO<Void> removeForPage(@PathVariable String cardId,
                                        @RequestParam(required = false) String reason,
                                        @RequestParam(required = false) String operator) {
        return graylistService.removeGray(cardId, reason, operator);
    }

    /** 灰名单操作流水分页（cardId / operateType 可选）。 */
    @GetMapping("/page/graylist/operate-logs")
    public ResultVO<PageInfo<GraylistOperateLog>> operateLogs(@RequestParam(required = false) String cardId,
                                                              @RequestParam(required = false) String operateType,
                                                              @RequestParam(defaultValue = "1") Integer pageNum,
                                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return graylistService.operateLogPage(cardId, operateType, pageNum, pageSize);
    }
}
