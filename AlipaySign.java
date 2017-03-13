package cn.com.dubbo.action;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.Consts;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import cn.com.dubbo.base.action.PayBaseAction;
import cn.com.dubbo.model.EcPaymentType;
import cn.com.jiuyao.pay.common.util.HttpClientUtils;
import cn.com.jiuyao.util.payments.alipayWap.RSA;

/**
 * 获取支付宝签名
 *
 * @author Administrator
 */
@Controller
public class AlipaySign extends PayBaseAction {

    Logger logger = Logger.getLogger(AlipaySign.class);


    /**
     * 获取支付宝签名
     */
    @RequestMapping(value = "/getAlipaySign")
    public void getSign(HttpServletRequest request, HttpServletResponse response) {
        try {
            // 验签
            String message = getContent(request.getParameterMap());

            if (verifySign(request, response, message)) {
                response.getWriter().print("sign error");
            } else {
                //获取支付方式
                EcPaymentType ecPaymentType = myPayService.findPaymentInfoByNo("alipayApp");
                Map<String, String> ecPaymentTypeParames = myPayService
                        .findPaymentTypeListInfo(ecPaymentType.getPaymentTypeId());
                String private_key = ecPaymentTypeParames.get("private_key"); // 私钥
                String charset = ecPaymentTypeParames.get("charset");//字符集
                //签名0
                String sign = RSA.sign(message, private_key, charset);
                sign = URLEncoder.encode(sign, "UTF-8");
                response.resetBuffer();
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().print(sign);
            }
            response.getWriter().flush();
            response.getWriter().close();
            response.flushBuffer();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * 获取微信openid
     *
     * @param request
     * @param response
     */
    @RequestMapping(value = "/getWeixinOpenId")
    public void getWeixinOpenId(HttpServletRequest request, HttpServletResponse response) {
        String code = request.getParameter("code");
        if (code != null && !"".equals(code)) {
            //获取微信参数
            EcPaymentType ecPaymentType = myPayService.findPaymentInfoByNo("weixinJs");
            Map<String, String> ecPaymentTypeParames = myPayService
                    .findPaymentTypeListInfo(ecPaymentType.getPaymentTypeId());

            //调用接口
            String url = "https://api.weixin.qq.com/sns/oauth2/access_token?appid=" + ecPaymentTypeParames.get("appId")
                    + "&secret=" + ecPaymentTypeParames.get("appKey") + "&code=" + code + "&grant_type=authorization_code";
            String content = "";
            try {
                content = HttpClientUtils.do_post(url, "");
                response.resetBuffer();
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().print(content);
                response.getWriter().flush();
                response.getWriter().close();
                response.flushBuffer();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}
