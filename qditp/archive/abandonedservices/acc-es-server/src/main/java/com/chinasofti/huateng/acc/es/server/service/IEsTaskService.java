package com.chinasofti.huateng.acc.es.server.service;

import com.chinasofti.huateng.acc.es.server.model.TblTktEsAssign;
import com.chinasofti.huateng.acc.es.server.model.TblTktEsTask;
import com.chinasofti.huateng.acc.es.server.netty.data.EsTaskReport;
import com.chinasofti.huateng.common.response.ResultVO;

import java.util.List;

public interface IEsTaskService {

    /**
     * 保存任务 (发行，预赋值)
     *
     * @param esTask
     * @return
     */
    ResultVO<?> save(TblTktEsTask esTask);

    /**
     * 根据taskNO查询任务 (发行，预赋值)
     *
     * @param taskNo
     * @return
     */
    ResultVO<?> selectByTaskNo(Integer taskNo);

    /**
     * 更新任务 (发行，预赋值)
     *
     * @param esTask
     * @return
     */
    ResultVO<?> updateTask(TblTktEsTask esTask);

    /**
     * 通过taskNo删除任务 (发行，预赋值)
     *
     * @param taskNo
     * @return
     * @Param esCode
     */
    ResultVO<?> deleteById(Integer taskNo, String esCode);

    /**
     * 分页查询任务 (发行，预赋值)
     *
     * @param task
     * @return
     */
    ResultVO<?> selectTasksByPage(Integer pageNum, Integer pageSize, TblTktEsTask task);

    int changeAssignStat(Integer taskNo, String assigned);

    /**
     * 保存任务 （用于缴销/重编码/注销）
     *
     * @param esTask
     * @return
     */
    ResultVO<?> save2(TblTktEsTask esTask);

    /**
     * 删除任务 （用于缴销/重编码/注销）
     *
     * @param taskNo
     * @return
     */
    ResultVO<?> delete2(Integer taskNo, String esCode);

    /**
     * 更新任务
     *
     * @param task
     * @return
     */
    ResultVO<?> update2(TblTktEsTask task);

    /**
     * 分页查询
     *
     * @param task
     * @return
     */
    ResultVO<?> page2(Integer pageNum, Integer pageSize, TblTktEsTask task);

    /**
     * 通过es节点编号和日期获取任务
     *
     * @param esNodeId
     * @param date
     * @return
     */
    List<TblTktEsTask> tasksByNodeIdAndDate(String esNodeId, String date);

    /**
     * 根据任务No获取当前任务的类型
     *
     * @param taskNo
     * @return
     */
    String getTaskType(Integer taskNo);

    /**
     * 打印机执行任务后发送任务报告，根据任务报告，修改任务的信息，并保存卡信息到数据库中
     *
     * @param report
     */
    boolean report(EsTaskReport report);

    /**
     * 获取个性化任务
     *
     * @param task
     * @return
     */
    ResultVO<?> getCustomTasks(Integer pageNum, Integer pageSize, TblTktEsTask task);


    /**
     * 保存个性化任务
     *
     * @param task
     * @return
     */
    ResultVO<?> saveCustom(TblTktEsTask task);

    /**
     * 产生对应的任务文件并上传到ftp服务器
     *
     * @param esAssign
     */
    void customFile(TblTktEsAssign esAssign);
}
