package com.chinasofti.huateng.acc.es.server.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * <p>
 * ES任务计划
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TblTktTaskPlan implements Serializable {

    private static final long serialVersionUID=1L;

    private Integer planNo;

    private String taskType;

    private Integer ticketMainType;

    private Integer ticketType;

    private Integer ticketSubType;

    private Integer planNum;

    private Integer actNum;

    private Integer assignNum;

    private Integer initAmt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT+8")
    private LocalDateTime planTms;

    private String taskPlanStat;

    private String lastUpdId;

    private LocalDateTime lastUpdTms;

    private String createUser;

    private String approveUser;

    private String ticketStatus;

}
