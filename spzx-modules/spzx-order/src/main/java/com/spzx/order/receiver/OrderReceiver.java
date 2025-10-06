package com.spzx.order.receiver;

import com.rabbitmq.client.Channel;
import com.spzx.common.core.utils.StringUtils;
import com.spzx.common.rabbit.constant.MqConst;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.service.IOrderInfoService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OrderReceiver {

    @Autowired
    private IOrderInfoService orderInfoService;

    @Autowired
    private RabbitService rabbitService;

/*    *//**
     * 监听消息
     * @param message
     *//*
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_TEST, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_TEST, durable = "true"),
            key = MqConst.ROUTING_TEST
    ))
    public void test(String content, Message message, Channel channel) {
        try {
            //都可以
            log.info("接收消息：{}", content);
            log.info("接收消息：{}", new String(message.getBody()));
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, false);
        }
    }

    *//**
     * 监听确认消息
     * @param message
     *//*
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_TEST, durable = "true"),
            value = @Queue(value = MqConst.QUEUE_CONFIRM, durable = "true"),
            key = MqConst.ROUTING_CONFIRM
    ))
    public void confirm(String content, Message message, Channel channel) {
        log.info("接收确认消息：{}", content);

        // false 确认一个消息，true 批量确认
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }*/

    /**
     * 延迟关闭订单消费者
     * @param orderId
     * @throws IOException
     */
    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_CANCEL_ORDER)
    public void processCloseOrder(String orderId, Message message, Channel channel) throws IOException {
        //1.处理业务
        if (StringUtils.isNotEmpty(orderId)) {
            log.info("【订单微服务】关闭订单消息：{}", orderId);
            orderInfoService.processCloseOrder(Long.parseLong(orderId));
        }
        //2.手动应答
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

}