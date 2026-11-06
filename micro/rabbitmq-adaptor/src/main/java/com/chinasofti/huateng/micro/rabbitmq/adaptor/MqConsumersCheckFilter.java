package com.chinasofti.huateng.micro.rabbitmq.adaptor;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@WebFilter(urlPatterns = {"/actuator/health"})
public class MqConsumersCheckFilter implements Filter {

    @Autowired
    RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;

    Lock tasklock = new ReentrantLock();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        chain.doFilter(request, response);
        if (tasklock.tryLock()) {
            rabbitListenerEndpointRegistry.start();
            tasklock.unlock();
        }

    }

}