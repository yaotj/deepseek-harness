package com.chinasofti.huateng.alipay.account.service.impl;

import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchQuery;
import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchView;
import com.chinasofti.huateng.alipay.account.service.AlipayUserPageQueryService;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class AlipayUserPageQueryServiceImpl implements AlipayUserPageQueryService {
    private final AlipayUserInfoMapper alipayUserInfoMapper;

    public AlipayUserPageQueryServiceImpl(AlipayUserInfoMapper alipayUserInfoMapper) {
        this.alipayUserInfoMapper = alipayUserInfoMapper;
    }

    @Override
    public PageInfo<AlipayUserSearchView> search(AlipayUserSearchQuery query) {
        List<AlipayUserInfo> users = alipayUserInfoMapper.selectByFilters(
                trimToNull(query.getMsisdn()), trimToNull(query.getCardId()),
                trimToNull(query.getThirdUserId()), trimToNull(query.getReqContractNo()));
        return slicePage(users, safePageNum(query.getPageNum()), safePageSize(query.getPageSize()));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }


    private PageInfo<AlipayUserSearchView> slicePage(List<AlipayUserInfo> users, int pageNum, int pageSize) {
        List<AlipayUserSearchView> views = users == null ? List.of() : users.stream().map(this::toView).toList();
        int total = views.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        PageInfo<AlipayUserSearchView> result = new PageInfo<>(views.subList(from, to));
        result.setTotal(total);
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        result.setPages((total + pageSize - 1) / pageSize);
        return result;
    }

    private AlipayUserSearchView toView(AlipayUserInfo user) {
        AlipayUserSearchView view = new AlipayUserSearchView();
        view.setThirdUserId(user.getThirdUserId());
        view.setCardId(user.getCardId());
        view.setCardType(user.getCardType());
        view.setMsisdn(maskPhone(user.getMsisdn()));
        view.setChannel(user.getChannel());
        view.setThirdPayId(maskValue(user.getThirdPayId()));
        view.setReqContractNo(user.getReqContractNo());
        view.setStatus(user.getStatus());
        view.setCreateTime(user.getCreateTime());
        view.setUpdateTime(user.getUpdateTime());
        return view;
    }

    private String maskPhone(String value) { return value != null && value.length() >= 7 ? value.substring(0, 3) + "****" + value.substring(value.length() - 4) : value; }
    private String maskValue(String value) { return value != null && value.length() > 6 ? value.substring(0, 3) + "****" + value.substring(value.length() - 3) : value; }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }
}
