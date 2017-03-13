package cn.com.dubbo.action;

import cn.com.dubbo.base.action.PayBaseAction;
import cn.com.jiuyao.util.HexUtil;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * 图片处理类
 */
@Controller
@RequestMapping(value = "/image")
public class ImageAction extends PayBaseAction {
    Logger logger = Logger.getLogger(ImageAction.class);

    public static final String CHARSET = "UTF-8";
    public static final int WIDTH = 249;
    public static final int HEIGHT = 249;
    public static final String JPG = "jpg";

    /**
     * 获取微信二维码图片
     */
    @RequestMapping(value = "/getWxImage")
    public void getWxImage(HttpServletRequest request, HttpServletResponse response) {
        try {
            String content = new String(HexUtil.hexString2Bytes(request.getParameter("content")));
            Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
            // 指定编码格式
            // hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            // 指定纠错级别(L--7%,M--15%,Q--25%,H--30%)
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            // 编码内容,编码类型(这里指定为二维码),生成图片宽度,生成图片高度,设置参数
            BitMatrix bitMatrix;
            bitMatrix = new MultiFormatWriter().encode(new String(content.getBytes(CHARSET), "ISO-8859-1"), BarcodeFormat.QR_CODE, WIDTH, HEIGHT, hints);

            // 生成的二维码图片默认背景为白色,前景为黑色,但是在加入logo图像后会导致logo也变为黑白色,至于是什么原因还没有仔细去读它的源码
            // 所以这里对其第一个参数黑色将ZXing默认的前景色0xFF000000稍微改了一下0xFF000001,最终效果也是白色背景黑色前景的二维码,且logo颜色保持原有不变
            MatrixToImageConfig config = new MatrixToImageConfig(0xFF000001, 0xFFFFFFFF);
            // 这里要显式指定MatrixToImageConfig,否则还会按照默认处理将logo图像也变为黑白色(如果打算加logo的话,反之则不须传MatrixToImageConfig参数)
            // MatrixToImageWriter.writeToFile(bitMatrix, imagePath.substring(imagePath.lastIndexOf(".") + 1), new File(imagePath), config);
            MatrixToImageWriter.writeToStream(bitMatrix, JPG, response.getOutputStream(), config);
        } catch (WriterException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
