package com.chinasofti.huateng.model.front.response;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ResponseBuilder {
    public static <T> HttpResponseBody<T> buildResponse(String code, String msg, String reqId, T dataBody) {
        ResponseHead head = new ResponseHead();
        head.setReqId(reqId);
        head.setRespCode(code);
        head.setRespMsg(msg);
        head.setRespTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));


        HttpResponseBody<T> wrapper = new HttpResponseBody<>();
        wrapper.setDataHead(head);
        wrapper.setDataBody(dataBody);
        return wrapper;
    }
}

