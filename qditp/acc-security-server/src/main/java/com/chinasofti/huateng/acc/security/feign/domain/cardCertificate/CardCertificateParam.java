package com.chinasofti.huateng.acc.security.feign.domain.cardCertificate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @author houkepan
 * @date 2020/12/9 15:31
 */
public class CardCertificateParam extends CardCertificate {

    /**
     * 记录头
     */
    @NotBlank(message = "记录头不能为空")
    @Size(min = 2, max = 2, message = "记录头长度为2")
    private String begin_Identifier;

    /**
     * 服务标识
     */
    @NotBlank(message = "服务标识不能为空")
    @Size(min = 8, max = 8, message = "服务标识长度范围6-8")
    private String service_id;

    public String getBegin_Identifier() {
        return begin_Identifier;
    }

    public void setBegin_Identifier(String begin_Identifier) {
        this.begin_Identifier = begin_Identifier;
    }

    public String getService_id() {
        return service_id;
    }

    public void setService_id(String service_id) {
        this.service_id = service_id;
    }
}

