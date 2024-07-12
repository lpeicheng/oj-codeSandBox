package com.codesanbox.controller;

import com.codesanbox.JavaCodeSandboxTemplate;
import com.codesanbox.JavaNativeCodeSandbox;
import com.codesanbox.model.ExecuteCodeRequest;
import com.codesanbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/")
public class MainController {
    //定义鉴权请求头和密钥
    private static final String SECRET_KEY="secretKey";
    private static final String SECRET_HEADER="secretHeader";
    @Resource
    private JavaNativeCodeSandbox javaNativeCodeSandbox;
    @PostMapping("/execute")
    public ExecuteCodeResponse execute(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request, HttpServletResponse response){
        String header = request.getHeader(SECRET_HEADER);
        if(!SECRET_KEY.equals(header)){
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return javaNativeCodeSandbox.executeCode(executeCodeRequest);
    }
}
