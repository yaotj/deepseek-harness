package com.chinasofti.huateng.micro.rabbitmq.adaptor;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleProducer {
    public static Logger log = LoggerFactory.getLogger(SimpleProducer.class);

    public static Set<String> returnsCallbackSet = Collections.synchronizedSet(new HashSet<>());
    public static Map<String, Boolean> confirmCallbackMap = new ConcurrentHashMap<>();

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback(confirmCallback);
        rabbitTemplate.setReturnsCallback(returnsCallback);
    }

    public static void clearSet(String id) {
        returnsCallbackSet.remove(id);
        confirmCallbackMap.remove(id);
    }

    RabbitTemplate.ConfirmCallback confirmCallback = new RabbitTemplate.ConfirmCallback() {
        @Override
        public void confirm(CorrelationData correlationData, boolean ack, String cause) {
            if (correlationData != null) {
                confirmCallbackMap.put(correlationData.getId(), ack);
            }
            if (!ack) {
                try {
                    String msg = String.format("confirmCallback msgid:%1$s,cause: %2$s", correlationData.getId(), cause);
                    log.error(msg);
                } catch (Exception e) {
                    log.error("{}", e.getMessage(), e);
                }
            }
        }
    };

    RabbitTemplate.ReturnsCallback returnsCallback = new RabbitTemplate.ReturnsCallback() {
        @Override
        public void returnedMessage(ReturnedMessage returnedMessage) {
            String id = returnedMessage.getMessage().getMessageProperties().getHeader("spring_returned_message_correlation");
            if (id != null) {
                returnsCallbackSet.add(id);
            }
            String msg = String.format("message:%1$s is returnCallback,replyCode is %2$d,replyText:{ %3$s },exchange is %4$s,routingKey is %5$s", new String(returnedMessage.getMessage().getBody()), returnedMessage.getReplyCode(), returnedMessage.getReplyText(), returnedMessage.getExchange(), returnedMessage.getRoutingKey());
            log.error(msg);
        }
    };

    public void send(String exchange, String routingKey, Message msg) {
        rabbitTemplate.send(exchange, routingKey, msg);
    }

    public void send(String exchange, String routingKey, String msg) {
        rabbitTemplate.send(exchange, routingKey, new Message(msg.getBytes(StandardCharsets.UTF_8)));
    }

    public boolean convertSendAndReceive(String exchange, String routingKey, Object msg) {
        return convertSendAndReceive(exchange, routingKey, msg, new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                return message;
            }
        });
    }

    public boolean convertSendAndReceive(String exchange, String routingKey, Object msg, MessagePostProcessor messagePostProcessor) {
        boolean b = false;
        String id = UUID.randomUUID().toString();
        try {
            rabbitTemplate.convertSendAndReceive(exchange, routingKey, msg, messagePostProcessor, new CorrelationData(id));
            if (confirmCallbackMap.containsKey(id) && confirmCallbackMap.get(id) && !returnsCallbackSet.contains(id)) {
                b = true;
            }
        } catch (Exception e) {
            b = false;
            log.error("{}", e.getMessage(), e);
        } finally {
            clearSet(id);
        }
        return b;
    }

}
