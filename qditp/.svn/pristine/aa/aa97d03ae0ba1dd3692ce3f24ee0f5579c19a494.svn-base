package com.chinasofti.huateng.model.front.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.alibaba.fastjson.annotation.JSONField;


@JsonInclude(JsonInclude.Include.NON_NULL)
public class HttpResponseBody<T> {
    @JSONField(ordinal = 1)
    private ResponseHead dataHead;
    @JSONField(ordinal = 2)

    private T dataBody;

    public ResponseHead getDataHead() {
        return dataHead;
    }

    public void setDataHead(ResponseHead dataHead) {
        this.dataHead = dataHead;
    }

    public T getDataBody() {
        return dataBody;
    }

    public void setDataBody(T dataBody) {
        this.dataBody = dataBody;
    }

    @Override
    public String toString() {
        return "{" +
                "dataHead=" + dataHead +
                ", dataBody=" + dataBody +
                '}';
    }
}