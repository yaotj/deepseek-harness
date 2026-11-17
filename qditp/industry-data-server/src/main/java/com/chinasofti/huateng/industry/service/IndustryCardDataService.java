package com.chinasofti.huateng.industry.service;

import com.chinasofti.huateng.model.app.IndustryCardDataBuildReqDTO;
import com.chinasofti.huateng.model.app.IndustryCardDataBuildRespDTO;

public interface IndustryCardDataService {


    IndustryCardDataBuildRespDTO buildCardData(IndustryCardDataBuildReqDTO request);
}
