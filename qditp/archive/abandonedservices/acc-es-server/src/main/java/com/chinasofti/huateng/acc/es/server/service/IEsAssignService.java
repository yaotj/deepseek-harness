package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsAssign;
import com.chinasofti.huateng.common.response.ResultVO;

public interface IEsAssignService {
    /**
     * 任务分配
     * @param esAssign
     * @return
     */
    ResultVO<?> save(TblTktEsAssign esAssign);

    /**
     * 按照ID进行查询
     * @param taskId
     * @param esId
     * @return
     */
    ResultVO<?> assign(Integer taskId, String esId);

    ResultVO<?> updateByTaskNoAndEsCode(Integer taskNo, String esNo,TblTktEsAssign assign);

    int delete(Integer taskNo, String esCode);

    void customFile(TblTktEsAssign esAssign);

    TblTktEsAssign getAssignByTaskNo(Integer taskNo);
}
