package com.chinasofti.huateng.accsimulator.controller;

import com.chinasofti.huateng.accsimulator.entity.AccEmployeeCard;
import com.chinasofti.huateng.accsimulator.entity.AccSimulationHistory;
import com.chinasofti.huateng.accsimulator.model.AccEmployeeCardSaveRequest;
import com.chinasofti.huateng.accsimulator.model.AccNotifySimulationRequest;
import com.chinasofti.huateng.accsimulator.model.AccUpdateSimulationRequest;
import com.chinasofti.huateng.accsimulator.model.SimulationExchange;
import com.chinasofti.huateng.accsimulator.service.AccSimulatorService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageInfo;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ACC 模拟器管理端控制器。
 *
 * <p>路径前缀 {@code /page/acc-simulator}，供运营后台调用，
 * 提供模拟通知发送、历史记录查询与员工卡管理能力。</p>
 */
@RestController
@RequestMapping("/page/acc-simulator")
public class AccSimulatorController {

    private final AccSimulatorService service;

    /**
     * 构造管理端控制器。
     *
     * @param service ACC 模拟器服务
     */
    public AccSimulatorController(AccSimulatorService service) {
        this.service = service;
    }

    /**
     * 发送员工卡状态通知。
     *
     * @param request 状态通知模拟请求
     * @return 模拟调用的完整交互记录
     */
    @PostMapping("/notify")
    public ResultVO<SimulationExchange> notify(@RequestBody AccNotifySimulationRequest request) {
        return ResultMapper.ok(service.sendNotify(request));
    }

    /**
     * 发送员工资料变更通知。
     *
     * @param request 资料变更通知模拟请求
     * @return 模拟调用的完整交互记录
     */
    @PostMapping("/update-notify")
    public ResultVO<SimulationExchange> updateNotify(@RequestBody AccUpdateSimulationRequest request) {
        return ResultMapper.ok(service.sendUpdateNotify(request));
    }

    /**
     * 分页查询模拟调用历史记录，按创建时间倒序。
     *
     * @param pageNum   页码，从 1 开始
     * @param pageSize  每页条数
     * @param operation 操作类型筛选，精确匹配，可为 null
     * @return 历史记录分页结果
     */
    @GetMapping("/history")
    public ResultVO<PageInfo<AccSimulationHistory>> history(@RequestParam(defaultValue = "1") int pageNum,
                                                            @RequestParam(defaultValue = "10") int pageSize,
                                                            @RequestParam(required = false) String operation) {
        return ResultMapper.ok(service.history(pageNum, pageSize, operation));
    }

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
    @GetMapping("/cards")
    public ResultVO<PageInfo<AccEmployeeCard>> cards(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) String cardNo,
                                                     @RequestParam(required = false) String employeeName,
                                                     @RequestParam(required = false) Integer cardStatus) {
        return ResultMapper.ok(service.cards(pageNum, pageSize, cardNo, employeeName, cardStatus));
    }

    /**
     * 新增或编辑模拟 ACC 员工卡；卡号是业务主键，重复卡号按编辑处理。
     *
     * @param request 员工卡保存请求
     * @return 操作结果
     */
    @PostMapping("/cards")
    public ResultVO<Void> saveCard(@RequestBody AccEmployeeCardSaveRequest request) {
        try {
            service.saveCard(request);
            return ResultMapper.ok();
        } catch (IllegalArgumentException ex) {
            return ResultMapper.illegalParams(ex.getMessage());
        }
    }

    /**
     * 清空所有模拟员工卡记录。
     *
     * @return 操作结果
     */
    @DeleteMapping("/cards")
    public ResultVO<Void> clearCards() {
        service.clearCards();
        return ResultMapper.ok();
    }

    /**
     * 清空所有模拟调用历史记录。
     *
     * @return 操作结果
     */
    @DeleteMapping("/history")
    public ResultVO<Void> clearHistory() {
        service.clearHistory();
        return ResultMapper.ok();
    }
}
