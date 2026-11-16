package com.chinasofti.huateng.acc.es.server.model;


import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.web.multipart.MultipartFile;

/**
 * <p>
 * ES任务拆分
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */
@Data
public class TblTktEsTask  implements Serializable {

    private static final long serialVersionUID=1L;

    private Integer taskNo;

    private Integer planNo;

    private String checkoutNo;

    private String taskType;

    private String planDate;

    private String actDate;

    private Integer ticketMainType;

    private Integer ticketType;

    private Integer ticketSubType;

    private String testFlg;

    /**
     * 版本
     */
    private String verNo;

    private String batchNo;

    private String ticketBatchNo;

    private Integer taskNum;

    private Integer beginNo;

    private Integer svtTicketType;

    private Integer endNo;

    private Integer depAmt;

    private Integer initAmt;

    private Integer rewardAmt;

    private Integer validDays;

    private String beginDate;

    private Integer finishNum;

    private Integer wasteNum;

    private Long tickUseTimes;

    private String isMonthCard;

    private String ridePrimession;

    private String isNamedCard;

    private String isActive;

    private String isPrintPhoto;

    private String taskAssnStat;

    private String taskExecStat;

    private LocalDateTime genTms;

    private String lastUpdId;

    private LocalDateTime lastUpdTms;

    private String isMemorial;

    private String walletUnit;

    private String apprDate;

    private String apprUser;

    private String apprStat;

    private String fileName;

    private String rideNumType;

    private String specifyStations;

    private String specifyAreas;

    private String specifyLines;

    private TblTktEsAssign esAssign;

    private MultipartFile file;

    private String createUser;

    private String ticketStatus;

}
