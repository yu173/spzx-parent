package com.spzx.common.rabbit.service;

import com.alibaba.fastjson2.JSON;
import com.spzx.common.rabbit.entity.GuiguCorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class RabbitService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 发送消息
     *
     * @param exchange   交换机
     * @param routingKey 路由键
     * @param message    消息
     */
    public boolean sendMessage(String exchange, String routingKey, Object message) {
        GuiguCorrelationData correlationData = new GuiguCorrelationData();
        String uuid = "mq:" + UUID.randomUUID().toString().replaceAll("-", "");
        correlationData.setId(uuid);
        correlationData.setMessage(message);
        correlationData.setExchange(exchange);
        correlationData.setRoutingKey(routingKey);

        //将关联的数据存储到redis中，用于后期的重发消息  只能在10分钟之内重发消息
        redisTemplate.opsForValue().set(uuid, JSON.toJSONString(correlationData),10, TimeUnit.MINUTES);

        //携带关联数据发送消息
        rabbitTemplate.convertAndSend(exchange, routingKey, message,correlationData);
        return true;//发送成功返回true
    }

    /**
     * 发送延迟消息方法
     * @param exchange 交换机
     * @param routingKey 路由键
     * @param message 消息数据
     * @param delayTime 延迟时间，单位为：秒
     */
    public boolean sendDealyMessage(String exchange, String routingKey, Object message, int delayTime) {
        //1.创建自定义相关消息对象-包含业务数据本身，交换器名称，路由键，队列类型，延迟时间,重试次数
        GuiguCorrelationData correlationData = new GuiguCorrelationData();
        String uuid = "mq:" + UUID.randomUUID().toString().replaceAll("-", "");
        correlationData.setId(uuid);
        correlationData.setMessage(message);
        correlationData.setExchange(exchange);
        correlationData.setRoutingKey(routingKey);
        correlationData.setDelay(true);//延迟消息
        correlationData.setDelayTime(delayTime);//延迟时间


        //2.将相关消息存入Redis  Key：UUID  相关消息对象  10 分钟
        redisTemplate.opsForValue().set(uuid, JSON.toJSONString(correlationData), 10, TimeUnit.MINUTES);

        //3.将相关消息封装到发送消息方法中
        rabbitTemplate.convertAndSend(exchange, routingKey, message,message1 -> {
            message1.getMessageProperties().setDelay(delayTime*1000);//MQ中延迟时间是毫秒
            return message1;
        }, correlationData);

        return true;
    }
}