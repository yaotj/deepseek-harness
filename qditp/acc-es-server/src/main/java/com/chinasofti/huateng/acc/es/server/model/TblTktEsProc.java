package com.chinasofti.huateng.acc.es.server.model;

import java.time.LocalDateTime;
import java.io.Serializable;

import lombok.*;

/**
 * <p>
 * ES报告结果处理
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
@Builder
public class TblTktEsProc implements Serializable {

    private static final long serialVersionUID=1L;

    private Integer taskNo;

    private String esCode;

    private Integer fileSeqNo;

    private String reportDate;

    private String fileNm;

    private Integer recNum;

    private Integer procNum;

    private String procStat;

    private String lastUpdId;

    private LocalDateTime lastUpdTms;

}
