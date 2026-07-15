package com.chinasofti.huateng.model.front.request;

import jakarta.validation.Valid;

public class HttpRequestBody<T> {

    @Valid
    private RequestHead dataHead;

    @Valid
    private T dataBody;

    public T getDataBody() {
        return dataBody;
    }

    public void setDataBody(T dataBody) {
        this.dataBody = dataBody;
    }

    public RequestHead getDataHead() {
        return dataHead;
    }

    public void setDataHead(RequestHead dataHead) {
        this.dataHead = dataHead;
    }

    @Override
    public String toString() {
        return "{" + "dataHead=" + dataHead + ", dataBody=" + dataBody + '}';
    }
}
