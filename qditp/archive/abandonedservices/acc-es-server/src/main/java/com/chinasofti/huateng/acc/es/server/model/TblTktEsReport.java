package com.chinasofti.huateng.acc.es.server.model;


import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * <p>
 * ES任务报告
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
public class TblTktEsReport  implements Serializable {

    private static final long serialVersionUID=1L;

    private Integer taskNo;

    private String taskType;

    private String esCode;

    private String checkinNo;

    private Integer taskNum;

    private Integer beginNo;

    private Integer endNo;

    private Integer finishNum;

    private Integer wasteNum;

    private String beginTime;

    private String endTime;

    private String lastUpdId;

    private LocalDateTime lastUpdTms;


}
