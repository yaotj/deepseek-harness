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

/**
 * 非支付宝用户运营查询，仅查询 USER_ITP_REG_INFO。
 */
@RestController
@RequestMapping("/page/user/itp")
public class ItpUserPageController {
    /**
     * 批量导入查询的单批卡号上限：远低于 Oracle IN 列表 1000 的硬顶，留出防呆余量。
     */
    private static final int BATCH_SEARCH_LIMIT = 500;

    private final ItpUserQueryService itpUserQueryService;

    public ItpUserPageController(ItpUserQueryService itpUserQueryService) {
        this.itpUserQueryService = itpUserQueryService;
    }

    /**
     * 按三方用户ID、手机号或卡ID查询注册记录。
     * <p>{@code status=ACTIVE} 查有效（DEL_YN=1），{@code status=CANCELED} 查已注销（DEL_YN=0）；
     * 不传时默认 ACTIVE，与「有效用户查询」页保持一致。
     */
    @GetMapping("/search")
    public ResultVO<List<ItpUserSearchView>> search(ItpUserSearchQuery query) {
        if (query == null || !StringUtils.hasText(query.getQueryType()) || !StringUtils.hasText(query.getKeyword())) {
            return ResultMapper.illegalParams("查询类型和查询关键字不能为空");
        }
        String status = StringUtils.hasText(query.getStatus()) ? query.getStatus().trim() : "ACTIVE";
        if (!"ACTIVE".equalsIgnoreCase(status) && !"CANCELED".equalsIgnoreCase(status)) {
            return ResultMapper.illegalParams("状态只能是 ACTIVE（有效）或 CANCELED（已注销）");
        }
        List<ItpUserSearchView> views = itpUserQueryService.search(query.getQueryType(), query.getKeyword(), status);
        if (views == null) {
            return ResultMapper.illegalParams("不支持的查询类型");
        }
        return ResultMapper.ok(views);
    }

    /**
     * 已注销用户查询：数据来源于 {@code USER_ITP_REG_LOG}（{@code OPER_TYPE=2} 销户记录）。
     *
     * <p>由于 IF8A-42 销户后通道已清空，注销成功即触发归档物理删除，
     * {@code USER_ITP_REG_INFO} 中 {@code DEL_YN=0} 的记录窗口极短，
     * 因此「已注销用户」的持久真源是销户日志而非开户表。
     */
    @GetMapping("/canceled-log/search")
    public ResultVO<List<ItpUserCanceledSearchView>> searchCanceledLog(ItpUserSearchQuery query) {
        if (query == null || !StringUtils.hasText(query.getQueryType()) || !StringUtils.hasText(query.getKeyword())) {
            return ResultMapper.illegalParams("查询类型和查询关键字不能为空");
        }
        List<ItpUserCanceledSearchView> views = itpUserQueryService.searchCanceledLog(
                query.getQueryType(), query.getKeyword().trim());
        if (views == null) {
            return ResultMapper.illegalParams("不支持的查询类型");
        }
        return ResultMapper.ok(views);
    }

    /**
     * 查询指定用户、指定票种下已经关联的全部支付渠道和解约参数。
     */
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

    /**
     * 注册量统计：按票种分组计数，可选注册日期窗（yyyy-MM-dd，闭区间）。
     */
    @GetMapping("/reg-stats")
    public ResultVO<List<RegStatView>> regStats(@RequestParam(required = false) String startDate,
                                                @RequestParam(required = false) String endDate) {
        return ResultMapper.ok(itpUserQueryService.regStats(startDate, endDate));
    }

    /**
     * 批量导入逻辑卡号查询手机号（综管台）。
     */
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
}
