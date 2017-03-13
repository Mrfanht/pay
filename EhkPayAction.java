package cn.com.dubbo.action;

import cn.com.dubbo.base.action.PayBaseAction;


import cn.com.dubbo.constant.PinganConfig;
import cn.com.dubbo.model.EcPaymentType;
import cn.com.dubbo.model.OrderInfo;
import cn.com.dubbo.model.OrderPaymentLog;
import cn.com.dubbo.service.payment.constant.Constants;

import cn.com.dubbo.service.payment.factory.Factory;
import cn.com.dubbo.service.payment.platform.Platform;
import cn.com.jiuyao.pay.common.util.HttpClientTools;
import cn.com.jiuyao.pay.common.util.StringUtil;
import cn.com.jiuyao.util.payments.alipay.config.AlipayConfig;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jiuyao.ec.common.type.OrderBusinessType;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;


/**
 * Created by fanhongtao
 * Date 2017-01-05 10:26
 */
@Controller
public class EhkPayAction extends PayBaseAction {

    Logger logger=Logger.getLogger(EhkPayAction.class);
    private static final  String errUrl="jsp/myec/pay_done.jsp";
    private static final  String resultUrl="jsp/myec/ehkresult.jsp";
    @Resource(name = "pinganConfig")
    private PinganConfig pinganConfig;



    /**
     * 订单查询
     * @param request
     * @return
     */
    @RequestMapping(value = "ehkQuery")
    public  String ehkQuery(HttpServletRequest request){
            String requestId=request.getParameter("requestId");
        if (!verify(requestId)){
            return errUrl;
        }
        OrderInfo orderInfo=orderService.findOrderById(requestId);
        if (orderInfo==null || orderInfo.getPaymentTypeId()==null){
           logger.info("order is not pay:{}"+orderInfo.getOrderNo());
            return  errUrl;
        }
        OrderPaymentLog orderPaymentLog=new OrderPaymentLog();
        EcPaymentType ecPaymentType=myPayService.findPaymentInfoById(orderInfo.getPaymentTypeId().toString());
        Map<String,String> map=myPayService.findPaymentTypeListInfo(ecPaymentType.getPaymentTypeId());
        orderPaymentLog.setBusinessId(Long.parseLong(requestId));
        orderPaymentLog.setBusinessType(OrderBusinessType.ORDER);
        orderPaymentLog=myPayService.findOrderPaymentLog(orderPaymentLog,false);
        orderPaymentLog.setEcPaymentTypeParames(map);
        orderPaymentLog.setBusinessId(Long.parseLong(requestId));
        Platform platform = Factory.createPlatform(ecPaymentType.getPaymentTypeNo());
        Map<String,String> queryMap= null;
        try {
            queryMap = platform.query(orderPaymentLog);
        } catch (Exception e) {
            logger.error("order query exception:"+e);
            e.printStackTrace();
        }
        request.setAttribute("result", queryMap.get("result"));
        return resultUrl;
    }



    /**
     * 退款查询
     * @param request
     * @return
     */
    @RequestMapping(value = "ehkQueryRefund")
    public  String ehkQueryRefund(HttpServletRequest request){
       /* String merchantId=request.getParameter("merchantId");*/
        String requestId=request.getParameter("requestId");

        if (!verify(requestId)){
            return errUrl;
        }
        OrderPaymentLog orderPaymentLog=new OrderPaymentLog();
        EcPaymentType ecPaymentType=myPayService.findPaymentInfoByNo(Constants.YHJ);
        Map<String,String> map=myPayService.findPaymentTypeListInfo(ecPaymentType.getPaymentTypeId());
        orderPaymentLog.setBusinessId(Long.parseLong(requestId));
        orderPaymentLog.setBusinessType(OrderBusinessType.REFUND);
        orderPaymentLog=myPayService.findOrderPaymentLog(orderPaymentLog,false);
        orderPaymentLog.setEcPaymentType(ecPaymentType);
        orderPaymentLog.setEcPaymentTypeParames(map);
        Platform platform = Factory.createPlatform(Constants.YHJ);
        Map<String,String> queryMap= null;
        try {
            queryMap = platform.refundQuery(orderPaymentLog);
            //如果退款成功调用退款接口 修改状态
            if(map != null && map.size() > 0){
                JSONObject jsonObject = JSONArray.parseObject(map.get("responseCode").toString());
                if ("0000".equals(jsonObject.get("code"))){
                    Map mapRequest = new HashMap();
                    mapRequest.put("orderNo",requestId);
                    mapRequest.put("applyStatus", AlipayConfig.REFUNDSTATE);
                    HttpClientTools.doGet(pinganConfig.getRefundUrl(), mapRequest);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        request.setAttribute("result", queryMap.get("result"));
        return resultUrl;
    }

    public boolean verify(String requestId){
        if (StringUtils.isEmpty(requestId)){
            logger.info("====veryParam,requestId is null");
            return   false;
        }
        return true;
    }
}
