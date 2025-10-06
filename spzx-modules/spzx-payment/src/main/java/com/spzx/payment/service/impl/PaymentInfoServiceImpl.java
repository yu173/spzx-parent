package com.spzx.payment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.common.core.constant.HttpStatus;
import com.spzx.common.core.constant.SecurityConstants;
import com.spzx.common.core.domain.R;
import com.spzx.common.rabbit.service.RabbitService;
import com.spzx.order.api.RemoteOrderInfoService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.api.domain.OrderItem;
import com.spzx.payment.domain.PaymentInfo;
import com.spzx.payment.mapper.PaymentInfoMapper;
import com.spzx.payment.service.IPaymentInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 付款信息Service业务层处理
 */
@Service
public class PaymentInfoServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements IPaymentInfoService {
    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private RemoteOrderInfoService remoteOrderInfoService;

    @Autowired
    private RabbitService rabbitService;

    @Override
    public PaymentInfo savePaymentInfo(String orderNo) {
        //1、先查询保存支付信息，如果有就不重复保存了。如果没有保存过，则保存。
        PaymentInfo paymentInfo = paymentInfoMapper.selectOne(new LambdaQueryWrapper<PaymentInfo>().eq(PaymentInfo::getOrderNo, orderNo));
        if (paymentInfo == null) {
            paymentInfo = new PaymentInfo();
            R<OrderInfo> orderInfoR = remoteOrderInfoService.getByOrderNo(orderNo, SecurityConstants.INNER);
            if(orderInfoR.getCode() == HttpStatus.ERROR){
                throw new RuntimeException(orderInfoR.getMsg());
            }
            OrderInfo orderInfo = orderInfoR.getData();

            paymentInfo = new PaymentInfo();

            paymentInfo.setUserId(orderInfo.getUserId());
            String content = "";
            for(OrderItem item : orderInfo.getOrderItemList()) {
                content += item.getSkuName() + " ";
            }
            paymentInfo.setContent(content);
            paymentInfo.setAmount(orderInfo.getTotalAmount());//注意：调用正式支付宝就填0.01元，沙箱
            paymentInfo.setOrderNo(orderNo);
            paymentInfo.setPaymentStatus("0");//未付款
            paymentInfoMapper.insert(paymentInfo);
        }
        return paymentInfo;
    }
}