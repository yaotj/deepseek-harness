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


@RestController
@RequestMapping("/page/user/alipay")
public class AlipayUserPageController {
    private final AlipayUserPageQueryService alipayUserPageQueryService;

    public AlipayUserPageController(AlipayUserPageQueryService alipayUserPageQueryService) {
        this.alipayUserPageQueryService = alipayUserPageQueryService;
    }


    @GetMapping("/search")
    public ResultVO<PageInfo<AlipayUserSearchView>> search(AlipayUserSearchQuery query) {
        if (query == null || !hasAnyFilter(query)) {
            return ResultMapper.illegalParams("请至少填写一个查询条件");
        }
        return ResultMapper.ok(alipayUserPageQueryService.search(query));
    }

    private boolean hasAnyFilter(AlipayUserSearchQuery query) {
        return StringUtils.hasText(query.getMsisdn())
                || StringUtils.hasText(query.getCardId())
                || StringUtils.hasText(query.getThirdUserId())
                || StringUtils.hasText(query.getReqContractNo());
    }
}
