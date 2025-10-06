package com.spzx.payment.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeWapPayRequest;
import com.alipay.api.response.AlipayTradeWapPayResponse;
import com.spzx.payment.configure.AlipayConfig;
import com.spzx.payment.domain.PaymentInfo;
import com.spzx.payment.service.IAlipayService;
import com.spzx.payment.service.IPaymentInfoService;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class AlipayServiceImpl implements IAlipayService {

    @Autowired
    AlipayClient alipayClient;

    @Autowired
    IPaymentInfoService paymentInfoService;

    @SneakyThrows  //等价于 throws Exception
    @Override
    public String submitAlipay(String orderNo) {
        //1、保存支付信息（不能重复保存）
        PaymentInfo paymentInfo = paymentInfoService.savePaymentInfo(orderNo);
        //2、调用支付接口
        AlipayTradeWapPayRequest request = new AlipayTradeWapPayRequest();
    // 同步回调
        // return_payment_url=http://sph-payment.atguigu.cn/alipay/callback/return
        request.setReturnUrl(AlipayConfig.return_payment_url);
        // 异步回调
        request.setNotifyUrl(AlipayConfig.notify_payment_url);//在公共参数中设置回跳和通知地址
        // 参数
        // 声明一个map 集合
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no", paymentInfo.getOrderNo());
        map.put("product_code", "QUICK_WAP_WAY");//手机网页支付产品
        map.put("total_amount",paymentInfo.getAmount());
        //map.put("total_amount", "0.01");
        map.put("subject", paymentInfo.getContent());
//        map.put("time_expire", "2025-03-29 17:09:00"); //设置订单绝对超时时间
        request.setBizContent(JSON.toJSONString(map));

        //3、获取h5表单
        AlipayTradeWapPayResponse response = alipayClient.pageExecute(request, "POST");
        String h5From = response.getBody();
        System.out.println("==========================h5表单==============================");
        System.out.println(h5From);
        System.out.println("==========================h5表单==============================");
        return "";
    }
}
