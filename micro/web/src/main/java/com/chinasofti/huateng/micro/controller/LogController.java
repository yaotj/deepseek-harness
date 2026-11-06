package com.chinasofti.huateng.micro.controller;

import com.chinasofti.huateng.log4j2.ErrorInterceptorFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Tag(name = "日志")
@RestController
public class LogController {

    public static Logger log = LoggerFactory.getLogger(LogController.class);

    @Operation(summary = "/当前日志")
    @GetMapping(value = "/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void getLogStream(HttpServletRequest request, HttpServletResponse response) {
        if (!request.getRemoteAddr().startsWith("127.") && !"0:0:0:0:0:0:0:1".equals(request.getRemoteAddr())) {
            return;
        }
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        ErrorInterceptorFilter.openLogEvent = true;
        try (ServletOutputStream out = response.getOutputStream()) {
            while (true) {
                String message = ErrorInterceptorFilter.LOG_QUEUE.poll(1, TimeUnit.SECONDS);
                if (message == null) {
                    continue;
                }
                message = message + "\n";
                out.write(message.getBytes(StandardCharsets.UTF_8));
                out.flush();

                if (!out.isReady()) {
                    break;
                }

                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
        } catch (IOException e) {

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            ErrorInterceptorFilter.openLogEvent = false;
            ErrorInterceptorFilter.LOG_QUEUE.clear();
        }


    }

}
