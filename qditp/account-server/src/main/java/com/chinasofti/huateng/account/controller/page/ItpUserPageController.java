package com.chinasofti.huateng.account.controller.page;

import com.chinasofti.huateng.account.page.ItpPayChannelView;
import com.chinasofti.huateng.account.page.ItpUserCanceledSearchView;
import com.chinasofti.huateng.account.page.ItpUserSearchQuery;
import com.chinasofti.huateng.account.page.ItpUserSearchView;
import com.chinasofti.huateng.account.page.RegStatView;
import com.chinasofti.huateng.account.service.ItpUserQueryService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/page/user/itp")
public class ItpUserPageController {

    private static final int BATCH_SEARCH_LIMIT = 500;

    private final ItpUserQueryService itpUserQueryService;

    public ItpUserPageController(ItpUserQueryService itpUserQueryService) {
        this.itpUserQueryService = itpUserQueryService;
    }


    @GetMapping("/search")
    public ResultVO<List<ItpUserSearchView>> search(ItpUserSearchQuery query) {
        if (query == null || !hasAnyFilter(query)) {
            return ResultMapper.illegalParams("请至少填写一个查询条件");
        }
        String status = StringUtils.hasText(query.getStatus()) ? query.getStatus().trim() : "ACTIVE";
        if (!"ACTIVE".equalsIgnoreCase(status) && !"CANCELED".equalsIgnoreCase(status)) {
            return ResultMapper.illegalParams("状态只能是 ACTIVE（有效）或 CANCELED（已注销）");
        }
        query.setStatus(status);
        return ResultMapper.ok(itpUserQueryService.search(query));
    }


    @GetMapping("/canceled-log/search")
    public ResultVO<List<ItpUserCanceledSearchView>> searchCanceledLog(ItpUserSearchQuery query) {
        if (query == null) {
            return ResultMapper.illegalParams("请至少填写一个查询条件");
        }
        if (StringUtils.hasText(query.getReqContractNo())) {
            return ResultMapper.illegalParams("已注销用户的数据源不含签约流水号，请按手机号 / 逻辑卡号 / 第三方用户 ID 查询");
        }
        if (!hasAnyFilter(query)) {
            return ResultMapper.illegalParams("请至少填写一个查询条件");
        }
        return ResultMapper.ok(itpUserQueryService.searchCanceledLog(query));
    }


    @GetMapping("/pay-channels")
    public ResultVO<List<ItpPayChannelView>> payChannels(
            @RequestParam String thirdUserId,
            @RequestParam String cardId,
            @RequestParam String cardType) {
        if (!StringUtils.hasText(thirdUserId) || !StringUtils.hasText(cardId) || !StringUtils.hasText(cardType)) {
            return ResultMapper.illegalParams("thirdUserId、cardId和cardType不能为空");
        }
        List<ItpPayChannelView> views = itpUserQueryService.payChannels(thirdUserId, cardId, cardType);
        if (views == null) {
            return ResultMapper.illegalParams("未找到有效票种注册信息");
        }
        return ResultMapper.ok(views);
    }


    @GetMapping("/reg-stats")
    public ResultVO<List<RegStatView>> regStats(@RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate) {
        return ResultMapper.ok(itpUserQueryService.regStats(startDate, endDate));
    }


    @PostMapping("/batch-search")
    public ResultVO<List<ItpUserSearchView>> batchSearch(@RequestBody List<String> cardIds) {
        if (cardIds == null || cardIds.isEmpty()) {
            return ResultMapper.illegalParams("逻辑卡号列表不能为空");
        }
        if (cardIds.size() > BATCH_SEARCH_LIMIT) {
            return ResultMapper.illegalParams("单批最多 " + BATCH_SEARCH_LIMIT + " 条逻辑卡号，请分批导入");
        }
        return ResultMapper.ok(itpUserQueryService.batchSearch(cardIds));
    }

    private boolean hasAnyFilter(ItpUserSearchQuery query) {
        return StringUtils.hasText(query.getMsisdn())
                || StringUtils.hasText(query.getCardId())
                || StringUtils.hasText(query.getThirdUserId())
                || StringUtils.hasText(query.getReqContractNo());
    }
}
