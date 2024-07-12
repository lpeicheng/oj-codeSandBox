package com.codesanbox;

import com.codesanbox.model.ExecuteCodeRequest;
import com.codesanbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

//java原生实现代码沙箱（复用模板方法）
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate{
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
