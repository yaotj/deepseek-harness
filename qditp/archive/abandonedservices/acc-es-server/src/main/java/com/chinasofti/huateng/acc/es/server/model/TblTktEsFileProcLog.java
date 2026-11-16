package com.chinasofti.huateng.acc.es.server.model;

import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * <p>
 * ES报告文件处理日志
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public class TblTktEsFileProcLog implements Serializable {

    private static final long serialVersionUID=1L;

    private Integer taskNo;

    private String esCode;

    private Integer recordNo;

    private String taskType;

    private String fileNm;

    private String fileProcState;

    private Integer procRecNum;

    private Integer procSuccessNum;

    private Integer procFailNum;

    private String recordContent;

    private String lastUpdId;

    private LocalDateTime lastUpdTms;
}
