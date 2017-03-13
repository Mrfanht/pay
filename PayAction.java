package cn.com.dubbo.action;

import cn.com.dubbo.base.action.PayBaseAction;

import cn.com.jiuyao.pay.common.constant.AccountConstants;
import cn.com.jiuyao.pay.common.constant.ResponseCodeConstants;
import cn.com.jiuyao.pay.common.factory.PaymentParamFactory;
import cn.com.dubbo.model.PaymentParam;
import cn.com.jiuyao.util.payments.ehk.B2CBank;
import com.jiuyao.ec.common.model.SysCode;
import com.jiuyao.ec.common.type.OrderBusinessType;

import cn.com.jiuyao.pay.common.util.StringUtil;
import cn.com.dubbo.model.AccountInfo;
import cn.com.dubbo.model.AccountLog;
import cn.com.dubbo.model.EcPaymentType;
import cn.com.dubbo.model.OrderPaymentLog;
import cn.com.dubbo.service.payment.constant.Constants;
import cn.com.dubbo.service.payment.factory.Factory;
import cn.com.dubbo.service.payment.platform.Platform;
import cn.com.dubbo.model.OrderInfo;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * 类 <code>PayAction</code>
 * 支付平台接口，对外提供三个接口
 * 1、pay 商城支付入口
 * 2、receivePayReturn 接收银行或第三方支付平台同步返回信息
 * 3、receivePayNotify 接收银行或第三方支付平台异步通知信息
 * 增加银行或第三方支付机构需要在payment.platform包中添加类，无需修改本类
 *
 * @author qun.su
 * @version 2014-3-5
 */
@Controller
public class PayAction extends PayBaseAction {

    Logger logger = Logger.getLogger(PayAction.class);
    /**
     * 支付充值接口
     * 1、检查商城上送参数格式
     * 2、验签
     * 3、订单有效性校验
     * 4、订单支付方式促销优惠计算
     * 5、添加订单支付日志
     * 6、构建报文数据
     * 7、添加请求信息日志
     *
     * @param request  request
     * @param response response
     * @return 支付结果页面
     */
    @RequestMapping(value = "/pay")
    public String pay(HttpServletRequest request, HttpServletResponse response) {
        try {
            OrderPaymentLog orderPaymentLog = new OrderPaymentLog();
            //获取提交的参数
            System.out.println("=======================");
            String orderId = request.getParameter("orderId");
            String paymentTypeNo = request.getParameter("paymentTypeNo");
            String memberId = request.getParameter("memberId");
            String businessType = request.getParameter("businessType");
            String paymentFee = request.getParameter("paymentFee");
//			String commitTime = request.getParameter("commitTime");
//			String other = request.getParameter("other");
            String channel = request.getParameter("channel");
            String returnUrl = request.getParameter("returnUrl");

            String paymentModeCode=request.getParameter("paymentModeCode");



            // 检查参数格式
            if (!verifyData(request, response)) {
                return errorRetrun(request);
            }

            //如果是易汇金添加额外验证
            if (Constants.YHJ.equals(paymentTypeNo) && "pc".equals(channel)){
                if (!verifyAccEnkData(request,response)){
                    return errorRetrun(request);
                }
            }
            // 验签
            String message = getContent(request.getParameterMap());
            if (!verifySign(request, response, message)) {
                return errorRetrun(request);
            }

            //获取支付方式
            EcPaymentType ecPaymentType = myPayService.findPaymentInfoByNo(paymentTypeNo);
            Map<String, String> ecPaymentTypeParames = myPayService
                    .findPaymentTypeListInfo(ecPaymentType.getPaymentTypeId());

            orderPaymentLog.setPaymentTypeId(ecPaymentType.getPaymentTypeId());
            orderPaymentLog.setBusinessId(Long.parseLong(orderId));
            orderPaymentLog.setMemberId(Long.valueOf(memberId));
            orderPaymentLog.setChannel(channel);
            orderPaymentLog.setFieldOne(paymentModeCode);//通过支付方式编码实现直连支付方式
            // 订单有效性校验
            if (businessType.equals(OrderBusinessType.ORDER)) {
                OrderInfo orderInfo = orderService.findOrderById(orderId);
                String jspUrl = verifyOrder(request, response, orderInfo);
                if (!"".equals(jspUrl)) {
                    request.setAttribute("returnUrl", returnUrl);
                    return jspUrl;
                }
                orderPaymentLog.setStartTime(orderInfo.getCommitTime());
                orderPaymentLog.setPaymentFee(orderInfo.getOrderPayFee());
                // 订单支付方式优惠计算(不支持多支付方式支付：如果订单已经使用其他支付方式进行过支付，则不进行订单支付方式优惠计算)
                Double sumPaid = myPayService.getPaidFeeSumByLog(orderPaymentLog);
                if (null == sumPaid) {
                    BigDecimal promotePayFee = paymentPormote(orderPaymentLog);
                    //orderPaymentLog.setPaymentFee(promotePayFee); 摄者paidFee ，paidFee和paymentFee 的关系
                    orderPaymentLog.setPaidFee(promotePayFee);//实付金额
                }
            }

            // 添加订单支付日志
           // orderPaymentLog.setMemberId(Long.parseLong(memberId));
            orderPaymentLog.setBusinessType(businessType);
            orderPaymentLog.setReturnUrl(returnUrl);
            if (!businessType.equals(OrderBusinessType.ORDER)) {
                orderPaymentLog.setPaymentFee(new BigDecimal(paymentFee));
            }
            orderPaymentLog = doSaveOrderPaymentLog(orderPaymentLog,paymentTypeNo);

            // 获取支付参数，构建数据报文
            orderPaymentLog.setEcPaymentType(ecPaymentType);
            orderPaymentLog.setEcPaymentTypeParames(ecPaymentTypeParames);
            Platform platform = Factory.createPlatform(paymentTypeNo);
            String requestMessage = platform.requestMessagePackage(request, response, orderPaymentLog);

            if (StringUtils.isEmpty(requestMessage)) {
                //返回错误提示页面
                logger.error("=================" + paymentTypeNo + " orderId:" + orderId + ": platform.requestMessagePackage error");
                request.setAttribute("paidSuccess", "对不起，支付初始化失败，请重新支付。" + request.getAttribute("paidSuccess"));
                request.setAttribute("orderId", orderId);
                request.setAttribute("payFlag", "failure");
                request.setAttribute("returnUrl", returnUrl);
                return "jsp/myec/pay_done.jsp";
            }



            // 添加请求信息日志
            if (!paymentTypeNo.equals(Constants.WXPAY) && !paymentTypeNo.equals(Constants.WEIXIN)) {//微信支付比较特殊,日志已记录
                  if (Constants.YHJ.equals(paymentTypeNo)){
                      paymentMessageLog(orderPaymentLog.getPaymentLogId(), "response", "return",
                              requestMessage, memberId);
                  }else {
                      paymentMessageLog(orderPaymentLog.getPaymentLogId(), "request", "",
                              requestMessage, memberId);
                  }
            }

            if (StringUtil.isNotEmpty(ecPaymentTypeParames.get("post_url"))) {
                request.setAttribute("postUrl", ecPaymentTypeParames.get("post_url"));
            }
            if (ecPaymentTypeParames.get("post_jsp") != null && !"".equals(ecPaymentTypeParames.get("post_jsp"))) {
               return ecPaymentTypeParames.get("post_jsp");
            } else {
                return null;
            }
            //return ecPaymentTypeParames.get("post_jsp");

        } catch (Exception e) {
            e.printStackTrace();
            return errorRetrun(request);
        }
    }

    @RequestMapping(value = "/pay2")
    public String pay2(HttpServletRequest request, HttpServletResponse response) {
        try {
            OrderPaymentLog orderPaymentLog = new OrderPaymentLog();
            PaymentParam paymentParam;
            try {
                paymentParam = PaymentParamFactory.createPaymentParam(request);
            } catch (Exception e) {
                logger.error(e.getMessage());
                return errorRetrun(request);
            }
            // 验签
            String message = getContent(request.getParameterMap());
            if (!verifySign(request, response, message)) {
                logger.error(paymentParam);
                return errorRetrun(request);
            }

            //获取支付方式
            EcPaymentType ecPaymentType = myPayService.findPaymentInfoByNo(paymentParam.getPaymentTypeNo());
            Map<String, String> ecPaymentTypeParames = myPayService.findPaymentTypeListInfo(ecPaymentType.getPaymentTypeId());
            orderPaymentLog.setPaymentTypeId(ecPaymentType.getPaymentTypeId());
            orderPaymentLog.setBusinessId(paymentParam.getOrderId());
            orderPaymentLog.setMemberId(paymentParam.getMemberId());
            orderPaymentLog.setChannel(paymentParam.getChannel());
            // 订单有效性校验
            if (paymentParam.getBusinessType().equals(OrderBusinessType.ORDER)) {
                OrderInfo orderInfo = orderService.findOrderById(paymentParam.getOrderId() + "");
                String jspUrl = verifyOrder(request, response, orderInfo);
                if (!"".equals(jspUrl)) {
                    request.setAttribute("returnUrl", paymentParam.getReturnUrl());
                    return jspUrl;
                }
                orderPaymentLog.setStartTime(orderInfo.getCommitTime());
                orderPaymentLog.setPaymentFee(orderInfo.getOrderPayFee());
                // 订单支付方式优惠计算(不支持多支付方式支付：如果订单已经使用其他支付方式进行过支付，则不进行订单支付方式优惠计算)
                Double sumPaid = myPayService.getPaidFeeSumByLog(orderPaymentLog);
                if (null == sumPaid) {
                    BigDecimal promotePayFee = paymentPormote(orderPaymentLog);//订单有效性校验:支付的不是自己的订单
                    orderPaymentLog.setPaymentFee(promotePayFee);
                }
            }

            // 添加订单支付日志
            orderPaymentLog.setMemberId(paymentParam.getMemberId());
            orderPaymentLog.setBusinessType(paymentParam.getBusinessType());
            orderPaymentLog.setReturnUrl(paymentParam.getReturnUrl());
            if (!paymentParam.getBusinessType().equals(OrderBusinessType.ORDER)) {
                orderPaymentLog.setPaymentFee(paymentParam.getPaymentFee());
            }
            orderPaymentLog = doSaveOrderPaymentLog(orderPaymentLog);

            // 获取支付参数，构建数据报文
            orderPaymentLog.setEcPaymentType(ecPaymentType);
            orderPaymentLog.setEcPaymentTypeParames(ecPaymentTypeParames);
            Platform platform = Factory.createPlatform(paymentParam.getPaymentTypeNo());
            String requestMessage = platform.requestMessagePackage(request, response, orderPaymentLog);

            if ("".equals(requestMessage)) {
                //返回错误提示页面
                request.setAttribute("paidSuccess", "对不起，支付初始化失败，请重新支付");
                request.setAttribute("orderId", paymentParam.getOrderId());
                request.setAttribute("payFlag", "failure");
                request.setAttribute("returnUrl", paymentParam.getReturnUrl());
                return "jsp/myec/pay_done.jsp";
            }
            // 添加请求信息日志
            if (!paymentParam.getPaymentTypeNo().equals(Constants.WXPAY)) {//主站微信支付比较特殊，只生成二维码图片，不记录日志
                paymentMessageLog(orderPaymentLog.getPaymentLogId(), "request", "",
                        requestMessage, paymentParam.getMemberId().toString());
            }

            if (null != ecPaymentTypeParames.get("post_url")) {
                request.setAttribute("postUrl", ecPaymentTypeParames.get("post_url"));
            }

            return ecPaymentTypeParames.get("post_jsp");
        } catch (Exception e) {
            e.printStackTrace();
            return errorRetrun(request);
        }
    }

    /**
     * 银行或第三方同步返回信息接收
     *
     * @param request       request
     * @param response      response
     * @param paymentTypeNo 支付方式编码
     * @throws Exception 说明：根据paymentTypeNo跳转到具体的返回信息处理类
     */
    @RequestMapping(value = "/receivePayReturn/{paymentTypeNo}")
    public void receivePayReturn(HttpServletRequest request,
                                 HttpServletResponse response, @PathVariable String paymentTypeNo)
            throws Exception {
        // 根据paymentTypeNo跳转到具体的返回信息处理类
        Platform platform = Factory.createPlatform(paymentTypeNo);
        platform.returnMessageHandle(request, response, paymentTypeNo);
    }

    /**
     * 银行或第三方异步通知信息接收
     *
     * @param request       request
     * @param response      response
     * @param paymentTypeNo 支付方式编码
     * @throws Exception 说明：根据paymentTypeNo跳转到具体的返回信息处理类
     */
    @RequestMapping(value = "/receivePayNotify/{paymentTypeNo}")
    public void receivePayNotify(HttpServletRequest request,
                                 HttpServletResponse response, @PathVariable String paymentTypeNo)
            throws Exception {
        // 根据paymentTypeNo跳转到具体的返回信息处理类
        Platform platform = Factory.createPlatform(paymentTypeNo);
        String url = platform.notifyMessageHandle(request, response, paymentTypeNo);
        //部分支付平台直接异步通知后，然后跳转到同步返回的页面进行信息提示，url为同步返回链接
        if (!StringUtil.isEmpty(url)) {
            response.sendRedirect(url);
        }
    }


    /**
     * 账户支付
     *
     * @param request  request
     * @param response response
     * @return 支付结果 Json格式
     * @throws java.io.UnsupportedEncodingException
     */
    @RequestMapping(value = "/balance")
    @ResponseBody
    public String balance(HttpServletRequest request,
                          HttpServletResponse response) throws UnsupportedEncodingException {
        Map resultMap = new HashMap<String, Object>();
        OrderPaymentLog orderPaymentLog = new OrderPaymentLog();
        try {
            //获取提交的参数
            String orderId = request.getParameter("orderId");
            String memberId = request.getParameter("memberId");
            String businessType = request.getParameter("businessType");
            String paymentFee = request.getParameter("paymentFee");
            String channel = request.getParameter("channel");
            String tradePassword = request.getParameter("tradePassword");

            // 检查参数格式
            if (!verifyAccData(request, response)) {
                resultMap.put("responseCode", ResponseCodeConstants.DATAFORMAT_FAIL);//参数格式有误
                JSONObject promoteJson = JSONObject.fromObject(resultMap);
                return URLEncoder.encode(promoteJson.toString(), "UTF-8");
            }
            // 验签
            String message = getContent(request.getParameterMap());
            if (!verifySign(request, response, message)) {
                resultMap.put("responseCode", ResponseCodeConstants.SIGN_FAIL);//签名无效
                JSONObject promoteJson = JSONObject.fromObject(resultMap);
                return URLEncoder.encode(promoteJson.toString(), "UTF-8");
            }
            //订单有效性校验
            if (businessType.equals(OrderBusinessType.ORDER)) {
                OrderInfo orderInfo = orderService.findOrderById(orderId);
                resultMap = verifyOrderMap(orderInfo, memberId, businessType);
                String status = resultMap.get("status").toString();
                if (!status.equals("success")) {
                    resultMap.put("responseCode", resultMap.get("status").toString());
                    JSONObject promoteJson = JSONObject.fromObject(resultMap);
                    return URLEncoder.encode(promoteJson.toString(), "UTF-8");
                }
            }

            // 添加订单账户支付日志
            EcPaymentType BalanceEcPaymentType = myPayService.findPaymentInfoByNo(Constants.BALANCE);
            orderPaymentLog.setPaymentTypeId(BalanceEcPaymentType.getPaymentTypeId());
            orderPaymentLog.setMemberId(Long.parseLong(memberId));
            orderPaymentLog.setBusinessType(businessType);
            orderPaymentLog.setBusinessId(Long.parseLong(orderId));
            orderPaymentLog.setChannel(channel);
            orderPaymentLog.setPaymentFee(new BigDecimal(paymentFee));
            orderPaymentLog = doSaveOrderPaymentLog(orderPaymentLog);
            //调用预存账户消费接口，获取返回结果 支付完成/余额不足，待支付金额，已支付金额
            AccountLog al = new AccountLog();
            al.setBusinessId(Long.parseLong(orderId));
            al.setMemberId(Long.parseLong(memberId));
            al.setMoney(new BigDecimal(paymentFee));
            al.setType(AccountConstants.CONSUME);
            AccountInfo ai = new AccountInfo();
            ai.setTradePassword(tradePassword);
//			orderPaymentLog.setAccountInfo(ai);
//			orderPaymentLog.setAccountLog(al);
            Platform balancePlatform = Factory.createPlatform(Constants.BALANCE);
            Map map = (Map) balancePlatform.extra(orderPaymentLog);
            map.put("paymentNo", orderPaymentLog.getPaymentNo());
            JSONObject promoteJson = JSONObject.fromObject(map);
            return URLEncoder.encode(promoteJson.toString(), "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            resultMap.put("responseCode", ResponseCodeConstants.FAIL);
            JSONObject promoteJson = JSONObject.fromObject(resultMap);
            return URLEncoder.encode(promoteJson.toString(), "UTF-8");
        }
    }

    /**
     * 错误页面返回
     *
     * @param request request
     * @return 跳转到支付完成页面
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
     * 检查参数格式
     * 使用正则表达式判断参数格式合法性
     *
     * @param request request
     * @throws IOException
     * @throws javax.servlet.ServletException
     */
    public boolean verifyData(HttpServletRequest request,
                              HttpServletResponse response) throws ServletException, IOException {
        Boolean flag = true;
        // 订单号
        if (StringUtil.isEmpty(request.getParameter("orderId"))
                || !matche(Constants.ORDERID_REGEX,
                request.getParameter("orderId"))) {
            logger.error("===verifyData==orderId：" + request.getParameter("orderId"));
            flag = false;
        }
        //支付流水号
//		if (StringUtil.isEmpty(request.getParameter("paymentNo"))) {
//			logger.error("===verifyData==paymentNo："+request.getParameter("paymentNo"));
//			flag = false;
//		}
        // 支付方式编码
        if (StringUtil.isEmpty(request.getParameter("paymentTypeNo"))
                || !matche(Constants.PAYMENTTYPENO_REGEX,
                request.getParameter("paymentTypeNo"))) {
            logger.error("===verifyData==paymentTypeNo：" + request.getParameter("paymentTypeNo"));
            flag = false;
        }
        // 会员号
        if (StringUtil.isEmpty(request.getParameter("memberId"))
                || !matche(Constants.MEMBERID_REGEX,
                request.getParameter("memberId"))) {
            logger.error("===verifyData==memberId：" + request.getParameter("memberId"));
            flag = false;
        }
        // 提交时间
        if (StringUtil.isEmpty(request.getParameter("commitTime"))
                || !matche(Constants.COMMITTIME_REGEX,
                request.getParameter("commitTime"))) {
            logger.error("===verifyData==commitTime：" + request.getParameter("commitTime"));
            flag = false;
        }
        //业务类型
        if (StringUtil.isEmpty(request.getParameter("businessType"))) {
            logger.error("===verifyData==businessType：" + request.getParameter("businessType"));
            flag = false;
        }
        //应付金额
        if (StringUtil.isEmpty(request.getParameter("paymentFee"))
                || !matche(Constants.PAYMENTFEE_REGEX,
                request.getParameter("paymentFee"))) {
            logger.error("===verifyData==paymentFee:" + request.getParameter("paymentFee"));
            flag = false;
        }
        // 渠道
        if (StringUtil.isEmpty(request.getParameter("channel"))) {
            logger.error("===verifyData== channel is null");
            flag = false;
        }
        //签名信息
        if (StringUtil.isEmpty(request.getParameter("sign"))) {
            logger.error("===verifyData== sign is null");
            flag = false;
        }
        return flag;
    }

    /**
     * 账户支付检查参数格式
     * 使用正则表达式判断参数格式合法性
     *
     * @param request request
     * @throws java.io.IOException
     * @throws javax.servlet.ServletException
     */
    public boolean verifyAccData(HttpServletRequest request,
                                 HttpServletResponse response) throws ServletException, IOException {
        Boolean flag = true;
        // 订单号
        if (StringUtil.isEmpty(request.getParameter("orderId"))
                || !matche(Constants.ORDERID_REGEX,
                request.getParameter("orderId"))) {
            logger.error("===verifyAppData==orderId：" + request.getParameter("orderId"));
            flag = false;
        }
        // 支付方式编码
//		if (StringUtil.isEmpty(request.getParameter("paymentTypeNo"))
//				|| !matche(Constants.PAYMENTTYPENO_REGEX,
//				request.getParameter("paymentTypeNo"))) {
//			logger.error("===verifyAppData==paymentTypeNo："+request.getParameter("paymentTypeNo"));
//			flag = false;
//		}
        //业务类型
        if (StringUtil.isEmpty(request.getParameter("businessType"))) {
            logger.error("===verifyAppData==businessType：" + request.getParameter("businessType"));
            flag = false;
        }
        //渠道
        if (StringUtil.isEmpty(request.getParameter("channel"))) {
            logger.error("===verifyAppData== channel is null");
            flag = false;
        }
        // 会员号
        if (StringUtil.isEmpty(request.getParameter("memberId"))) {
            logger.error("===verifyData==memberId："
                    + request.getParameter("memberId"));
            flag = false;
        }
        // 支付金额
        if (StringUtil.isEmpty(request.getParameter("paymentFee"))
                || !matche(Constants.PAYMENTFEE_REGEX,
                request.getParameter("paymentFee"))) {
            logger.error("===verifyData==paymentFee："
                    + request.getParameter("paymentFee"));
            flag = false;
        }
        // 账户支付密码
        if (StringUtil.isEmpty(request.getParameter("tradePassword"))) {
            logger.error("===verifyData==tradePassword："
                    + request.getParameter("tradePassword"));
            flag = false;
        }
        return flag;
    }


    /**
     * 易汇金商品信息验证
     * @param request
     * @param response
     * @return
     * @throws ServletException
     * @throws IOException
     */
    public boolean verifyAccEnkData(HttpServletRequest request,
                                 HttpServletResponse response) throws ServletException, IOException {

        //银行
        if (StringUtil.isEmpty(request.getParameter("paymentModeCode")) || !B2CBank.bankMap().containsKey(request.getParameter("paymentModeCode"))) {
            logger.error("===verifyEhkData==paymentModeCode：" + request.getParameter("paymentModeCode"));
            return false;
        }

        return true;
    }
}
