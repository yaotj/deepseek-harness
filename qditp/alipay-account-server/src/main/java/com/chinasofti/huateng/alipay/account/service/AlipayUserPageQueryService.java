package com.chinasofti.huateng.alipay.account.service;

import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchQuery;
import com.chinasofti.huateng.alipay.account.page.AlipayUserSearchView;
import com.github.pagehelper.PageInfo;


public interface AlipayUserPageQueryService {

    PageInfo<AlipayUserSearchView> search(AlipayUserSearchQuery query);
}
