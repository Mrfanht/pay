package cn.com.dubbo.action;

import cn.com.jiuyao.pay.common.util.StringUtil;
import cn.com.dubbo.base.action.PayBaseAction;
import cn.com.dubbo.model.OrderPaymentLog;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 跳转到微信二维码页面
 *
 * @author qun.su
 * @version 2015-1-20
 */
@Controller
public class WXPayAction extends PayBaseAction {

    Logger logger = Logger.getLogger(WXPayAction.class);

    /**
     * 支付完成后获取支付平台返回信息
     *
     * @param request
     * @return
     * @throws Exception
     */
    @RequestMapping(value = "/wxpay")
    public String WXPay(HttpServletRequest request) throws Exception {
        String returnUrl = request.getParameter("returnUrl");
        String content = request.getParameter("content");
        String paymentNo = request.getParameter("paymentNo");
        String paymentFee = request.getParameter("paymentFee");
        String orderId = request.getParameter("orderId");
        request.setAttribute("returnUrl", returnUrl);
        request.setAttribute("content", content);
        request.setAttribute("paymentNo", paymentNo);
        request.setAttribute("paymentFee", paymentFee);
        request.setAttribute("orderId", orderId);

        return "jsp/myec/wxpayImage.jsp";
    }

    /**
     * 循环获取订单支付状态
     *
     * @param request
     * @param response
     * @return
     */
    @RequestMapping(value = "/wxpay/poll")
    @ResponseBody
    public String poll(HttpServletRequest request,
                       HttpServletResponse response) {
        String result = null;
        try {
            String paymentNo = request.getParameter("paymentNo");
            OrderPaymentLog orderPaymentLog = new OrderPaymentLog();
            orderPaymentLog.setPaymentNo(paymentNo);
            orderPaymentLog = myPayService.findOrderPaymentLog(orderPaymentLog, false);
            if (null != orderPaymentLog && StringUtil.isNotEmpty(orderPaymentLog.getBackState())) {
                if ("SUCCESS".equals(orderPaymentLog.getBackState())) {
                    result = "SUCCESS";
                }
            }
//			String key = "WX"+paymentNo;
//			if((Boolean)cache.get(key)){
//				result = "SUCCESS";
//			}
        } catch (Exception e) {
            logger.error("微信轮询获取支付结果信息异常！");
        }

        return result;
    }

}
