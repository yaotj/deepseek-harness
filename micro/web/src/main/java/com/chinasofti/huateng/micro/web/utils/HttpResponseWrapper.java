package com.chinasofti.huateng.micro.web.utils;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;

public class HttpResponseWrapper extends HttpServletResponseWrapper {

    public static Logger log = LoggerFactory.getLogger(HttpResponseWrapper.class);

    private String body;

    private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    public HttpResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return new ServletOutputStream() {

            @Override
            public void flush() throws IOException {
                byteArrayOutputStream.flush();
                getResponse().getOutputStream().flush();
            }

            @Override
            public void write(int b) throws IOException {
                byteArrayOutputStream.write(b);
                getResponse().getOutputStream().write(b);
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {

            }
        };
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return new PrintWriter(getOutputStream());
    }

    private String getBody(int logResponseMaxSize) {
        if (body == null) {
            try {
                byteArrayOutputStream.close();
            } catch (IOException e) {
                log.error("{}", e.getMessage(), e);
            }
            if (byteArrayOutputStream.size() > logResponseMaxSize) {
                body = MessageFormat.format("response size more than {0} byte", logResponseMaxSize);
                return body;
            }
            body = new String(byteArrayOutputStream.toByteArray());
        }
        return body;
    }

    public static String getResponseBody(ServletResponse response, int logResponseMaxSize) {
        if (response instanceof HttpResponseWrapper) {
            if (response.getContentType() != null && response.getContentType().toLowerCase().contains("application/vnd")) {
                return "";
            }
            return ((HttpResponseWrapper) response).getBody(logResponseMaxSize);
        } else {
            return "";
        }
    }
}
