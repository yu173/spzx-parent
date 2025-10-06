package com.spzx.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.spzx.order.api.domain.OrderInfo;
import com.spzx.order.domain.vo.OrderForm;
import com.spzx.order.domain.vo.TradeVo;

import java.util.List;

public interface IOrderInfoService extends IService<OrderInfo> {
    /**
     * 查询订单列表
     *
     * @param orderInfo 订单
     * @return 订单集合
     */
    public List<OrderInfo> selectOrderInfoList(OrderInfo orderInfo);

    /**
     * 查询订单
     *
     * @param id 订单主键
     * @return 订单
     */
    public OrderInfo selectOrderInfoById(Long id);

    /**
     * 去结算
     * @return
     */
    TradeVo trade();

    /**
     * 下单
     * @return
     */
    Long submitOrder(OrderForm orderForm);

    /**
     * 延迟消息关闭（等待支付时间）
     * @param orderId 订单id
     */
    void processCloseOrder(Long orderId);

    /**
     * 根据订单号查询订单
     * @param orderNo 订单号
     * @return  订单详情
     */
    OrderInfo getByOrderNo(String orderNo);
}