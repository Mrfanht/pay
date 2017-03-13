package cn.com.dubbo.action;

import java.io.UnsupportedEncodingException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class TestAction {

	@RequestMapping(value = "/test")
	public void getWxImage(HttpServletRequest request, HttpServletResponse response){
		String body = "测试";
		try {
			body = new String(body.getBytes(),"ISO-8859-1");
			body = new String(body.getBytes("ISO-8859-1"),"gbk"); 
//			System.out.println(body);
			System.out.println(request.getParameter("merReserved"));
			System.out.println(request.getParameter("merReserved2"));
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public static void main(String[] args) {
		String body = "测试";
		try {
			body = new String(body.getBytes(),"ISO-8859-1");
			body = new String(body.getBytes("ISO-8859-1"),"utf-8"); 
			System.out.println(body);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
