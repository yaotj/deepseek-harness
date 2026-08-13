package com.chinasofti.huateng.alipay.account.controller;

import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchQuery;
import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchView;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 支付宝注册用户运营查询，仅查询 ALIPAY_USER_INFO。 */
@RestController
@RequestMapping("/page/user/alipay")
public class AlipayUserPageController {
    private final AlipayUserInfoMapper alipayUserInfoMapper;

    public AlipayUserPageController(AlipayUserInfoMapper alipayUserInfoMapper) {
        this.alipayUserInfoMapper = alipayUserInfoMapper;
    }

    /** 按三方用户ID、手机号或卡ID查询支付宝注册用户。 */
    @GetMapping("/search")
    public ResultVO<List<AlipayUserSearchView>> search(AlipayUserSearchQuery query) {
        if (query == null || !StringUtils.hasText(query.getQueryType()) || !StringUtils.hasText(query.getKeyword())) {
            return ResultMapper.illegalParams("查询类型和查询关键字不能为空");
        }
        String keyword = query.getKeyword().trim();
        List<AlipayUserInfo> users = switch (query.getQueryType()) {
            case "THIRD_USER_ID" -> {
                AlipayUserInfo user = alipayUserInfoMapper.selectByThirdUserId(keyword);
                yield user == null ? List.of() : List.of(user);
            }
            case "CARD_ID" -> {
                AlipayUserInfo user = alipayUserInfoMapper.selectByCardId(keyword);
                yield user == null ? List.of() : List.of(user);
            }
            case "MSISDN" -> alipayUserInfoMapper.selectByMsisdn(keyword);
            default -> null;
        };
        if (users == null) {
            return ResultMapper.illegalParams("不支持的查询类型");
        }
        return ResultMapper.ok(users.stream().map(this::toView).toList());
    }

    private AlipayUserSearchView toView(AlipayUserInfo user) {
        // 手机号和第三方支付标识均按运营展示要求脱敏。
        AlipayUserSearchView view = new AlipayUserSearchView();
        view.setThirdUserId(user.getThirdUserId());
        view.setCardId(user.getCardId());
        view.setCardType(user.getCardType());
        view.setMsisdn(maskPhone(user.getMsisdn()));
        view.setChannel(user.getChannel());
        view.setThirdPayId(maskValue(user.getThirdPayId()));
        view.setStatus(user.getStatus());
        view.setCreateTime(user.getCreateTime());
        view.setUpdateTime(user.getUpdateTime());
        return view;
    }

    private String maskPhone(String value) { return value != null && value.length() >= 7 ? value.substring(0, 3) + "****" + value.substring(value.length() - 4) : value; }
    private String maskValue(String value) { return value != null && value.length() > 6 ? value.substring(0, 3) + "****" + value.substring(value.length() - 3) : value; }
}
