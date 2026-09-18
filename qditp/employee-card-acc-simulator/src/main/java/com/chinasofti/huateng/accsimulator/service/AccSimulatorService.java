package com.chinasofti.huateng.accsimulator.service;

import com.chinasofti.huateng.accsimulator.entity.AccEmployeeCard;
import com.chinasofti.huateng.accsimulator.entity.AccSimulationHistory;
import com.chinasofti.huateng.accsimulator.model.AccEmployeeCardSaveRequest;
import com.chinasofti.huateng.accsimulator.model.AccNotifySimulationRequest;
import com.chinasofti.huateng.accsimulator.model.AccUpdateSimulationRequest;
import com.chinasofti.huateng.accsimulator.model.SimulationExchange;
import com.github.pagehelper.PageInfo;

/**
 * ACC 模拟器服务接口。
 *
 * <p>提供模拟 ACC 通知发送、历史记录查询与员工卡管理能力，
 * 供管理端控制器调用。</p>
 */
public interface AccSimulatorService {

    /**
     * 发送员工卡状态通知，同时将卡列表中的卡片信息同步到本地模拟库。
     *
     * @param request 状态通知模拟请求
     * @return 模拟调用的完整交互记录
     */
    SimulationExchange sendNotify(AccNotifySimulationRequest request);

    /**
     * 发送员工资料变更通知，同时将变更信息同步到本地模拟库。
     *
     * @param request 资料变更通知模拟请求
     * @return 模拟调用的完整交互记录
     */
    SimulationExchange sendUpdateNotify(AccUpdateSimulationRequest request);

    /**
     * 分页查询模拟调用历史记录，按创建时间倒序。
     *
     * @param pageNum   页码，从 1 开始
     * @param pageSize  每页条数
     * @param operation 操作类型筛选，精确匹配，可为 null
     * @return 历史记录分页结果
     */
    PageInfo<AccSimulationHistory> history(int pageNum, int pageSize, String operation);

    /**
     * 清空所有模拟调用历史记录。
     */
    void clearHistory();

    /**
     * 按条件分页查询模拟员工卡，按更新时间倒序。
     *
     * @param pageNum      页码，从 1 开始
     * @param pageSize     每页条数
     * @param cardNo       员工号，模糊匹配，可为 null
     * @param employeeName 员工姓名，模糊匹配，可为 null
     * @param cardStatus   电子卡状态，精确匹配，可为 null
     * @return 员工卡分页结果
     */
    PageInfo<AccEmployeeCard> cards(int pageNum, int pageSize, String cardNo, String employeeName, Integer cardStatus);

    /**
     * 保存管理端维护的模拟 ACC 员工卡；卡号是业务主键，重复卡号按编辑处理。
     *
     * @param request 员工卡保存请求
     * @throws IllegalArgumentException 员工号为空或状态值不在 1-4 范围内
     */
    void saveCard(AccEmployeeCardSaveRequest request);

    /**
     * 清空所有模拟员工卡记录。
     */
    void clearCards();

    /**
     * 按员工号查询员工卡。
     *
     * @param cardNo 员工号/实体卡号
     * @return 员工卡记录，不存在返回 null
     */
    AccEmployeeCard findCard(String cardNo);
}
