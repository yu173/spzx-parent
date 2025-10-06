package com.spzx.common.rabbit.config;

import com.alibaba.fastjson2.JSON;
import com.spzx.common.core.utils.StringUtils;
import com.spzx.common.rabbit.entity.GuiguCorrelationData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 服务器启动时，执行rabbitTemplate初始化，设置确认函数和回退函数
 * ApplicationEvent      一些子事件的父类。
 * ApplicationReadyEvent 具体子事件。表示应用程序启动好，IOC容器初始化好，存在相关bean对象了。再进行相关的初始化。
 * 也可以使用相关的注解替代： @EventListener
 */
@Slf4j
@Component
public class RabbitInitConfigApplicationListener implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RedisTemplate redisTemplate;


    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        this.setUpInitRabbitTemplete();
    }


    private void setUpInitRabbitTemplete() {
        /**
         * 只确认消息是否正确到达 Exchange 中,成功与否都会回调
         *
         * @param correlation 相关数据  非消息本身业务数据
         * @param ack             应答结果
         * @param reason           如果发送消息到交换器失败，错误原因
         */
        this.rabbitTemplate.setConfirmCallback((correlationData, ack, reason) -> {
            if (ack) {
                //消息到交换器成功
                log.info("消息发送到Exchange成功：{}", correlationData);
            } else {
                //消息到交换器失败
                log.error("消息发送到Exchange失败：{}", reason);

                //交换机没有收到消息，执行消息重发
                this.retrySendMsg(correlationData);
            }
        });

        /**
         * 消息没有正确到达队列时触发回调，如果正确到达队列不执行
         */
        this.rabbitTemplate.setReturnsCallback(returnedMessage -> {
            log.error("Returned: " + returnedMessage.getMessage() + "\nreplyCode: " + returnedMessage.getReplyCode()
                    + "\nreplyText: " + returnedMessage.getReplyText() + "\nexchange/rk: "
                    + returnedMessage.getExchange() + "/" + returnedMessage.getRoutingKey());

            //队列没收到消息，将消息退回。关联数据并没有退回。
            String correlationDataUuid = returnedMessage.getMessage().getMessageProperties().getHeader("spring_returned_message_correlation");
            String correlationDataJSON = (String) redisTemplate.opsForValue().get(correlationDataUuid);
            if (StringUtils.hasText(correlationDataJSON)) {
                GuiguCorrelationData correlationData = JSON.parseObject(correlationDataJSON, GuiguCorrelationData.class);
                boolean isDelay = correlationData.isDelay();
                if (!isDelay) {
                    this.retrySendMsg(correlationData);
                }
            }
        });
    }

    //专业重发消息的辅助方法
    private void retrySendMsg(CorrelationData correlationData) {
        GuiguCorrelationData guiguCorrelationData = (GuiguCorrelationData) correlationData;
        int retryCount = guiguCorrelationData.getRetryCount();
        if (retryCount >= 3) {//retryCount = [0,1,2] 重发消息，等于3不重新发送
            log.info("消息重复次数不能超过3次");
            return;
        }
        ++retryCount;
        guiguCorrelationData.setRetryCount(retryCount);
        //更新一下缓存，否则retryCount值还是0
        redisTemplate.opsForValue().set(guiguCorrelationData.getId(), JSON.toJSONString(guiguCorrelationData), 10, TimeUnit.MINUTES);

        /*try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {

        }*/

        //重新发送消息，关联数据依然带上
        this.rabbitTemplate.convertAndSend(guiguCorrelationData.getExchange(), guiguCorrelationData.getRoutingKey(), guiguCorrelationData.getMessage(), guiguCorrelationData);
        log.info("消息重新发送！retryCount=" + retryCount);
    }


}
