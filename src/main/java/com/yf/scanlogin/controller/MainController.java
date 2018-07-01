package com.yf.scanlogin.controller;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.yf.scanlogin.config.WebSecurityConfig;
import com.yf.scanlogin.model.LoginResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 控制器
 *
 * @author 刘冬博客http://www.cnblogs.com/GoodHelper
 *
 */
@Controller
@Slf4j
public class MainController {

    @RequestMapping("/login")
    public String toLogin(){
        return "login";
    }

    /**
     * 存储登录状态
     */
    private static  final  Map<String, LoginResponse> loginMap = new ConcurrentHashMap<>();

    @GetMapping({ "/", "index" })
    public String index(Model model, @SessionAttribute(WebSecurityConfig.SESSION_KEY) String user) {
        model.addAttribute("user", user);
        return "index";
    }

    /**
     * 获取二维码
     * @return
     * @throws Exception
     */
    @GetMapping("login/getQrCode")
    public @ResponseBody Map<String, Object> getQrCode(HttpServletRequest request) throws Exception {
        Map<String, Object> result = new HashMap<>();
        UUID uuid = UUID.randomUUID();
        result.put("loginId",uuid );

        // app端登录地址
        String loginUrl = "http://192.168.8.102/login/"+uuid+"/user/";
        result.put("loginUrl", loginUrl);
        result.put("image", createQrCode(loginUrl));
        if(!loginMap.containsKey(uuid.toString())){
            LoginResponse loginResponse = new LoginResponse();
            loginResponse.getCodeTime=System.currentTimeMillis();
            loginMap.put(uuid.toString(), loginResponse);
        }
        return result;
    }


    @PostMapping("/{loginId}/auth")
    @ResponseBody
    public  Map<String, Object> auth(@PathVariable String loginId,String user){
        if (loginMap.containsKey(loginId)) {
            LoginResponse loginResponse = loginMap.get(loginId);

            // 赋值登录用户
            loginResponse.user=user;

            // 唤醒登录等待线程
            loginResponse.latch.countDown();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("loginId", loginId);
        result.put("user", user);
        result.put("code","0");
        return result;
    }


    /**
     * app二维码登录地址
     *
     * @param loginId
     * @return
     */
    @GetMapping("login/{loginId}/user/")
    public  String setUser(@PathVariable String loginId,Model model) {
        model.addAttribute("loginId", loginId);
        return "auth";
    }

    /**
     * 等待二维码扫码结果的长连接
     *
     * @param loginId
     * @param session
     * @return
     */
    @GetMapping("login/getResponse/{loginId}")
    public @ResponseBody
    Map<String, Object> getResponse(@PathVariable String loginId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        result.put("loginId", loginId);
        try {
            LoginResponse loginResponse=loginMap.get(loginId);
            log.info("loginMap:["+loginMap+"]");

            // 第一次判断
            // 判断是否登录,如果已登录则写入session
            if (loginResponse.user != null) {
                session.setAttribute(WebSecurityConfig.SESSION_KEY, loginResponse.user);
                result.put("success", true);
                if (loginMap.containsKey(loginId)) {
                    loginMap.remove(loginId);
                }
                return result;
            }

            if (loginResponse.latch == null) {
                loginResponse.latch = new CountDownLatch(1);
            }
            try {
                // 线程等待
                loginResponse.latch.await(2, TimeUnit.SECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 再次判断
            // 判断是否登录,如果已登录则写入session
            if (loginResponse.user != null) {
                session.setAttribute(WebSecurityConfig.SESSION_KEY, loginResponse.user);
                result.put("success", true);
                if (loginMap.containsKey(loginId)) {
                    loginMap.remove(loginId);
                }
                return result;
            }

            Long getCodeTime= loginResponse.getCodeTime;
            long currentTimeMillis = System.currentTimeMillis();
            if(currentTimeMillis-getCodeTime>loginResponse.expirTime){
                if (loginMap.containsKey(loginId)) {
                    loginMap.remove(loginId);
                }
                result.put("isExpire","1");
            }else {
                result.put("isExpire","0");
            }

            result.put("success", false);
            return result;
        } finally {
            // 移除登录请求

        }
    }

    /**
     * 生成base64二维码
     *
     * @param content
     * @return
     * @throws Exception
     */
    private String createQrCode(String content) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix bitMatrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 400, 400, hints);
            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    image.setRGB(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            ImageIO.write(image, "JPG", out);
            return Base64.encodeBase64String(out.toByteArray());
        }
    }

}