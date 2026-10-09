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

    /** 按三方用户ID、手机号或卡ID分页查询支付宝注册用户。 */
    @GetMapping("/search")
    public ResultVO<PageInfo<AlipayUserSearchView>> search(AlipayUserSearchQuery query) {
        if (query == null || !StringUtils.hasText(query.getQueryType()) || !StringUtils.hasText(query.getKeyword())) {
            return ResultMapper.illegalParams("查询类型和查询关键字不能为空");
        }
        PageInfo<AlipayUserSearchView> page = alipayUserPageQueryService.search(
                query.getQueryType(), query.getKeyword(), query.getPageNum(), query.getPageSize());
        if (page == null) {
            return ResultMapper.illegalParams("不支持的查询类型");
        }
        return ResultMapper.ok(page);
    }
}
