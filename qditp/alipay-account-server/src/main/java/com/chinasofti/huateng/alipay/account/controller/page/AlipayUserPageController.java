package com.chinasofti.huateng.alipay.account.controller.page;

import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchQuery;
import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchView;
import com.chinasofti.huateng.alipay.account.service.AlipayUserPageQueryService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageInfo;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝注册用户运营查询，仅查询 ALIPAY_USER_INFO。
 *
 * <p>本类只做入参非空校验与路由，查询分派、分页与脱敏都在 {@link AlipayUserPageQueryService}
 * （形态对齐 account-server 的 {@code controller/page/ItpUserPageController}）。
 */
@RestController
@RequestMapping("/page/user/alipay")
public class AlipayUserPageController {
    private final AlipayUserPageQueryService alipayUserPageQueryService;

    public AlipayUserPageController(AlipayUserPageQueryService alipayUserPageQueryService) {
        this.alipayUserPageQueryService = alipayUserPageQueryService;
    }

    /**
     * 按手机号 / 逻辑卡号 / 三方用户ID / 渠道签约流水号的任意组合分页查询支付宝注册用户（条件之间是 AND）。
     */
    @GetMapping("/search")
    public ResultVO<PageInfo<AlipayUserSearchView>> search(AlipayUserSearchQuery query) {
        if (query == null || !hasAnyFilter(query)) {
            return ResultMapper.illegalParams("请至少填写一个查询条件");
        }
        return ResultMapper.ok(alipayUserPageQueryService.search(query));
    }

    /** 四个并列筛选条件里是否至少填了一个；全空即整表扫描，必须在入口拦住。 */
    private boolean hasAnyFilter(AlipayUserSearchQuery query) {
        return StringUtils.hasText(query.getMsisdn())
                || StringUtils.hasText(query.getCardId())
                || StringUtils.hasText(query.getThirdUserId())
                || StringUtils.hasText(query.getReqContractNo());
    }
}
