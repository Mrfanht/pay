package cn.com.dubbo.action;

import cn.com.dubbo.base.action.PayBaseAction;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by fanhongtao
 * Date 2017-03-01 13:41
 */
@Controller
@RequestMapping(value = "qRCode")
public class QRCode extends PayBaseAction {
    Logger logger = LoggerFactory.getLogger(QRCode.class);

    /**
     *
     * @param folderName 存储二维码图片的文件夹名
     * @param imageName 二维码图片名称
     * @param content 是在二维码中写入的内容，这里我传入的是URL：指定我判断软件类型的控制器
     * @return
     */
    public String makeQRCode(String folderName, String imageName, String content) {
        String fileName = imageName + ".png";
        try {
            // 检查是否存在imageQR目录，不存在则先创建
            File file = new File(folderName);
            if (!file.exists() && !file.isDirectory()) {
                file.mkdir();
            }
            folderName = file.getAbsolutePath();
            int width = 200; // 图像宽度
            int height = 200; // 图像高度
            String format = "png";// 图像类型
            Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, width, height, hints);// 生成矩阵
            Path path = FileSystems.getDefault().getPath(folderName, fileName);
            MatrixToImageWriter.writeToPath(bitMatrix, format, path);// 输出图像
            logger.info("二维码已经生成," + path);
            fileName = path.toString();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            fileName = null;
        }
        return fileName;
    }
}
