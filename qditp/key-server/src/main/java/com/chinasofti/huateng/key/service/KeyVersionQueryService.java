package com.chinasofti.huateng.key.service;

import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.key.page.KeyVersionView;

import java.util.List;


public interface KeyVersionQueryService {


    ResultVO<List<KeyVersionView>> versions();
}
