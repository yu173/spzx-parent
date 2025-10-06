package com.spzx.payment.configure;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Slf4j
@Configuration
public class AlipayConfig {

    @Value("${alipay.alipay_url}")
    private String alipay_url;

    @Value("${alipay.app_private_key}")
    private String app_private_key;//私钥：  将数据加密传递给调用支付宝接口

    @Value("${alipay.app_id}")
    private String app_id;//和支付宝支付产品进行绑定的应用程序（电商网站）


    public final static String format = "json";
    public final static String charset = "utf-8";
    public final static String sign_type = "RSA2";//数据机密算法


    public static String return_payment_url;//同步通知地址：通知h5前端支付结果

    public static String notify_payment_url;//异步通知地址：通知商品甄选后端，支付结果

//    public static  String return_order_url;

    public static String alipay_public_key;//用于解密：支付宝返回给商品甄选的数据

    @Value("${alipay.alipay_public_key}")
    public void setAlipay_public_key(String alipay_public_key) {
        AlipayConfig.alipay_public_key = alipay_public_key;
    }

    @Value("${alipay.return_payment_url}")
    public void setReturn_url(String return_payment_url) {
        AlipayConfig.return_payment_url = return_payment_url;
    }

    @Value("${alipay.notify_payment_url}")
    public void setNotify_url(String notify_payment_url) {
        AlipayConfig.notify_payment_url = notify_payment_url;
    }

/*    @Value("${alipay.return_order_url}")
    public   void setReturn_order_url(String return_order_url) {
        AlipayConfig.return_order_url = return_order_url;
    }*/

    @Bean
    public AlipayClient alipayClient() {
        //可以用于调用支付宝：支付接口、退款接口、查询接口等
        AlipayClient alipayClient = new DefaultAlipayClient(alipay_url, app_id, app_private_key, format, charset, alipay_public_key, sign_type);
        return alipayClient;
    }

    //异步回调接口
    @RequestMapping("callback/notify")
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String, String> paramMap, HttpServletRequest request) {
        log.info("AlipayController...alipayNotify方法执行了...");
        System.out.println("===================支付宝扣款后返回给商品甄选后端返回的数据=========================");
        System.out.println(paramMap);
        System.out.println("===================支付宝扣款后返回给商品甄选后端返回的数据=========================");

        //业务包括：1、修改支付信息   2、修改订单状态   3、减库存
        return "success" ;//业务完成后，给支付宝返回成功字符串："success"
    }

}