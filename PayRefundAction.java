package cn.com.dubbo.action;

import cn.com.dubbo.constant.PinganConfig;
import cn.com.jiuyao.pay.common.util.HttpClientTools;
import cn.com.jiuyao.util.payments.alipay.config.AlipayConfig;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.jiuyao.ec.common.model.SysCode;
import cn.com.jiuyao.util.IPUtil;
import cn.com.jiuyao.pay.common.util.StringUtil;
import cn.com.dubbo.base.action.PayBaseAction;
import cn.com.dubbo.model.OrderPaymentLog;
import cn.com.dubbo.service.payment.constant.Constants;
import cn.com.dubbo.service.payment.factory.Factory;
import cn.com.dubbo.service.payment.platform.Platform;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 订单原路返回退款
 *
 * @author qun.su
 * @version 2015年5月19日
 */
@Controller
public class PayRefundAction extends PayBaseAction {
    Logger logger = Logger.getLogger(PayRefundAction.class);




    /**
     * 退款
     *
     * @param request
     * @param response
     * @return 支付结果页面
     */
    @RequestMapping(value = "/refund")
    public String refund(HttpServletRequest request, HttpServletResponse response) {
        try {
            String oldPaymentNo = request.getParameter("oldPaymentNo");//订单号
           // String paymentNo = request.getParameter("paymentNo");//类型
            String refundAmt = request.getParameter("refundAmt");//退款金额
            if (!verifyParam(oldPaymentNo, refundAmt)){
                return errorRetrun(request);
            }
            OrderPaymentLog orderPaymentLog = new OrderPaymentLog();
            orderPaymentLog.setOldPaymentNo(oldPaymentNo);
            orderPaymentLog.setBusinessId(Long.parseLong(oldPaymentNo));
            orderPaymentLog.setPaymentNo(oldPaymentNo);
            orderPaymentLog.setRefundAmt(refundAmt);
            orderPaymentLog.setReqTxnTime(IPUtil.getIpAddr(request));
            Map map = myPayService.doRefund(orderPaymentLog);

            response.setContentType("text/html;charset=UTF-8");
            response.getWriter().print(map.get("responseCode"));

        } catch (Exception e) {
            e.printStackTrace();

        }
        return null;
    }

    private boolean verifyParam(String oldPaymentNo,  String refundAmt) {
        if (StringUtils.isEmpty(oldPaymentNo)){
            logger.info("verify is not passed, oldPaymentNo is null");
            return  false;
        }
        /*if (StringUtils.isEmpty(paymentNo)){
            logger.info("verify is not passed, paymentNo is null");
            return false;
        }*/
        if ( StringUtil.isEmpty(refundAmt)
                || !matche(Constants.PAYMENTFEE_REGEX,
                refundAmt)){
            logger.info("verify is not passed, refundAmt is null or is not a number");
            return  false;
        }
        return true;
    }

    /**
     * 银行或第三方退款异步通知信息接收
     *
     * @param request
     * @param response
     * @param paymentTypeNo 支付方式编码
     * @throws Exception 说明：根据paymentTypeNo跳转到具体的返回信息处理类
     */
    @RequestMapping(value = "/refundNotify/{paymentTypeNo}")
    public void refundNotify(HttpServletRequest request,
                             HttpServletResponse response, @PathVariable String paymentTypeNo) {
        try {
            // 根据paymentTypeNo跳转到具体的返回信息处理类
            Platform platform = Factory.createPlatform(paymentTypeNo);
            platform.refundNotify(request, response, paymentTypeNo);
        } catch (Exception e) {
            logger.error("退款异步通知异常：" + paymentTypeNo);
            e.printStackTrace();
        }
    }

    /**
     * 错误页面返回
     *
     * @param request
     * @return
     */
    private String errorRetrun(HttpServletRequest request) {
        request.setAttribute("paidSuccess", "对不起，支付初始化失败，请重新支付!");
        request.setAttribute("orderId", request.getParameter("orderId"));
        request.setAttribute("payFlag", "failure");
        //如果返回路径为空，则默认返回到PC端路径上
        if (null == request.getParameter("returnUrl") || "".equals(request.getParameter("returnUrl"))) {
            SysCode code = new SysCode();
            code.setCodeTypeNo("pay");
            code.setCodeNo("pc_return_url");
            SysCode returnCode = systemService.getCode(code);
            request.setAttribute("returnUrl", returnCode.getCodeValue());
        } else {
            request.setAttribute("returnUrl", request.getParameter("returnUrl"));
        }
        return "jsp/myec/pay_done.jsp";
    }

    /**
     * 支付查询参数格式检查
     * 使用正则表达式判断参数格式合法性
     *
     * @param request
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    public boolean verifyRefundData(HttpServletRequest request,
                                    HttpServletResponse response) throws ServletException, IOException {
        Boolean flag = true;
        // 原支付流水号
        if (StringUtil.isEmpty(request.getParameter("oldPaymentNo"))) {
            logger.error("===verifyRefundData==oldPaymentNo：" + request.getParameter("oldPaymentNo"));
            flag = false;
        }
        // 退款流水号
        if (StringUtil.isEmpty(request.getParameter("paymentNo"))) {
            logger.error("===verifyRefundData==paymentNo：" + request.getParameter("paymentNo"));
            flag = false;
        }
        if (StringUtil.isEmpty(request.getParameter("refundAmt"))
                || !matche(Constants.PAYMENTFEE_REGEX,
                request.getParameter("refundAmt"))) {
            logger.error("===verifyRefundData==refundAmt：" + request.getParameter("refundAmt"));
            flag = false;
        }
        return flag;
    }

}
