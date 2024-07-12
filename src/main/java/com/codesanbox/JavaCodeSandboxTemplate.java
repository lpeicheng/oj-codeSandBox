package com.codesanbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;
import com.codesanbox.model.ExecuteCodeRequest;
import com.codesanbox.model.ExecuteCodeResponse;
import com.codesanbox.model.ExecuteMessage;
import com.codesanbox.model.JudgeInfo;
import com.codesanbox.model.enums.JudgeInfoMessageEnum;
import com.codesanbox.model.enums.QuestionSubmitStatusEnum;
import com.codesanbox.util.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * java原生实现代码沙箱
 * 缺点：相对简单且响应及时（同步），但存在不够安全的问题
 */
@Slf4j
public class JavaCodeSandboxTemplate implements CodeSandbox{
    public static final String USER_DIR="tmpCode";
    public static final String JAVA_NAME="Main.java";
    public static final int TIME_OUT=5000;
    /**
     * Java代码黑名单
     * 黑名单检测通常用于辅助安全策略，而不是作为唯一的安全手段
     */
    private static final List<String> blackList = Arrays.asList(
            // 文件操作相关
            "Files", "File", "FileInputStream", "FileOutputStream", "RandomAccessFile", "FileReader", "FileWriter", "FileChannel", "FileLock", "Path", "Paths", "File.createTempFile", "File.createTempDirectory", "ZipInputStream", "ZipOutputStream",

            // 网络相关
            "Socket", "ServerSocket", "DatagramSocket", "InetAddress", "URL", "URLConnection", "HttpURLConnection", "SocketChannel", "ServerSocketChannel", "DatagramChannel", "SocketPermission", "ServerSocketPermission",

            // 系统命令执行相关
            "exec", "Runtime.getRuntime().exec", "ProcessBuilder", "SecurityManager", "System.exit", "Runtime.getRuntime().halt", "SecurityManager.checkExec",

            // 反射相关
            "Class.forName", "Method.invoke", "sun.reflect.", "java.lang.reflect.", "Unsafe", "sun.misc.Unsafe", "sun.reflect.Unsafe", "Proxy",

            // 数据库相关
            "Statement", "PreparedStatement", "CallableStatement", "DataSource", "Connection", "ResultSet", "Hibernate", "JPA", // 防止使用 ORM 框架执行不安全的数据库操作
            "createStatement", "prepareStatement", "prepareCall",

            // 不安全的操作
            "Unsafe", "sun.misc.Unsafe", "sun.reflect.Unsafe",

            // 加密解密相关
            "Cipher", "MessageDigest", "KeyGenerator", "KeyPairGenerator", "SecretKeyFactory", "KeyStore", "SecureRandom", "java.security.",

            // 序列化相关
            "ObjectInputStream", "ObjectOutputStream", "Serializable", "Externalizable", "readObject", "writeObject",

            // 线程相关
            "Thread", "Runnable", "Executor", "ExecutorService", "ThreadPoolExecutor", "ThreadGroup", "ThreadLocal", "Thread.sleep", "Thread.yield", "Thread.stop", "Thread.suspend", "Thread.resume", "java.util.concurrent.",

            // 安全管理器相关
            "SecurityManager",

            // 其他可能导致安全问题的操作
            "System.load", "System.loadLibrary", // 防止加载本地库
            "JNI", "Java Native Interface", // 防止使用 JNI 调用本地代码
            "Unsafe.allocateMemory", "Unsafe.freeMemory", // 直接内存操作
            "System.getProperties", "System.setProperty", // 系统属性操作
            "System.getenv", // 获取环境变量
            "System.console", // 控制台访问
            "Runtime.addShutdownHook", // 添加关闭钩子
            "Runtime.load", "Runtime.loadLibrary" // 加载本地库
    );

    /**
     * 代码黑名单字典树
     */
    private static final WordTree WORD_TREE;

    static
    {
        // 初始化黑名单字典树
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        // 安全控制：限制敏感代码：黑名单检测
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null)
        {
            System.out.println("包含禁止词：" + foundWord.getFoundWord());
            // 返回错误信息
            return new ExecuteCodeResponse(null, new JudgeInfo(JudgeInfoMessageEnum.DANGEROUS_OPERATION.getValue(),null,null), QuestionSubmitStatusEnum.FAILED.getValue(),"包含禁止词：" + foundWord.getFoundWord());
        }
        File file = createFile(code);
        //编译代码
        ExecuteMessage executeMessage=complieCode(file);
        System.out.println("编译结果：" + executeMessage);
        if (executeMessage.getErrorMessage() != null)
        {
            // 返回编译错误信息
            return new ExecuteCodeResponse(null, new JudgeInfo(executeMessage.getErrorMessage(), null, null), QuestionSubmitStatusEnum.FAILED.getValue(), executeMessage.getMessage());
        }
        //执行文件
        List<ExecuteMessage> executeMessages=runFile(file,inputList);
        ExecuteCodeResponse getOutputResponse=getOutputResponse(executeMessages);
        boolean b = deleteFile(file);
        if (!b){
               log.error("删除文件失败：{}",file.getAbsolutePath());
        }
        return getOutputResponse;
    }

    public File createFile(String code){
        String userDir = System.getProperty("user.dir");
        String globalCodePathName=userDir+ File.separator+ USER_DIR;
        //判断代码java文件是否存在，没有就创建
        if (!FileUtil.exist(globalCodePathName)){
            FileUtil.mkdir(globalCodePathName);
        }
        //用户代码分开存放
        String userCodePath=globalCodePathName+File.separator+ UUID.randomUUID();
        String userCode=userCodePath+File.separator+JAVA_NAME;
        return FileUtil.writeString(code, userCode, StandardCharsets.UTF_8);
    }
    public ExecuteMessage complieCode(File file){
        try {
            String compileCmd = String.format("javac -encoding utf-8 %s", file.getAbsolutePath());
            Process process = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runAndProcess(process, "编译");
            if(executeMessage.getExitValue()!=0){
                executeMessage.setExitValue(1);
                executeMessage.setMessage(JudgeInfoMessageEnum.COMPILE_ERROR.getText());
                executeMessage.setErrorMessage(JudgeInfoMessageEnum.COMPILE_ERROR.getValue());
            }
            return executeMessage;
        }catch (Exception e){
            ExecuteMessage executeMessage = new ExecuteMessage();
            executeMessage.setExitValue(1);
            executeMessage.setMessage(e.getMessage());
            executeMessage.setErrorMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
            return executeMessage;
       }
    }

    public List<ExecuteMessage> runFile(File code,List<String> inputList){
        String absolutePath = code.getParentFile().getAbsolutePath();
        List<ExecuteMessage> executeMessages=new ArrayList<>();
        for (String input : inputList)  {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", absolutePath, input);
            try {
                Process exec = Runtime.getRuntime().exec(runCmd);
                //超时控制
                new Thread(()->{
                   try {
                       Thread.sleep(TIME_OUT);
                       System.out.println("超时中断");
                       exec.destroy();
                   }catch (Exception e){
                       throw new RuntimeException(e);
                   }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runAndProcess(exec, "运行");
                System.out.println(executeMessage);
                if (executeMessage.getExitValue() != 0)
                {
                    executeMessage.setExitValue(1);
                    executeMessage.setMessage(JudgeInfoMessageEnum.RUNTIME_ERROR.getText());
                    executeMessage.setErrorMessage(JudgeInfoMessageEnum.RUNTIME_ERROR.getValue());
                }
                executeMessages.add(executeMessage);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return executeMessages;
    }

    //获取输出结果
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessages){
        ExecuteCodeResponse executeCodeResponse=new ExecuteCodeResponse();
        List<String> outputList=new ArrayList<>();
        //去最大值判断是否超时
        long maxTime=0;
        long maxMemory = 0;
        for (ExecuteMessage executeMessage : executeMessages) {
            if(StrUtil.isNotBlank(executeMessage.getErrorMessage())){
                executeCodeResponse.setMessage(executeMessage.getMessage());
                // 用户提交的代码执行中存在错误
                executeCodeResponse.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
                executeCodeResponse.setJudgeInfo(new JudgeInfo(executeMessage.getErrorMessage(), null, null));
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if(time!=null){
                maxTime=Math.max(time,maxTime);
            }
            Long memory = executeMessage.getMemory();
            if (memory != null)
            {
                maxMemory = Math.max(maxMemory, memory);
            }
        }
        //正常运行成功
        if (outputList.size()==executeMessages.size()){
            executeCodeResponse.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
            executeCodeResponse.setMessage(QuestionSubmitStatusEnum.SUCCEED.getText());
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    //删除文件
    public boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String absolutePath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(absolutePath);
            System.out.println("删除"+(del?"成功":"失败"));
            return del;
        }
        return true;
    }
}
