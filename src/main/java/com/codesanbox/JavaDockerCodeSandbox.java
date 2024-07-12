package com.codesanbox;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.ArrayUtil;
import com.codesanbox.model.ExecuteCodeRequest;
import com.codesanbox.model.ExecuteCodeResponse;
import com.codesanbox.model.ExecuteMessage;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 使用docker容器实现代码沙箱
 * 缺点：安全但是没有及时性，内存无法获取，原因是内存判断是通过定时获取docker容器状态实现的，显然存在在你获取容器状态之前程序就执行完了的情况
 */
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate{
    private static final long TIME_OUT = 5000L;

    private static final Boolean FIRST_INIT = true;
    //测试
    public static void main(String[] args) {
        JavaDockerCodeSandbox javaNativeCodeSandBox = new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("Main.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = javaNativeCodeSandBox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }
    @Override
    public List<ExecuteMessage> runFile(File file, List<String> inputList) {
        String absolutePath = file.getParentFile().getAbsolutePath();
        //创建实例
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        //拉取镜像
        String image="openjdk:8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback resultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载拉取镜像中：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd.exec(resultCallback).awaitCompletion();
            } catch (Exception e) {
                System.out.println("拉取镜像失败");
                throw new RuntimeException(e);
            }
            System.out.println("拉取镜像成功");
        }
            //创建容器时，可以指定文件路径（Volume） 映射，作用是把本地的文件同步到容器中，可以让容器访问，创建可交互能多次输入输出的容器
            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
            HostConfig hostConfig=new HostConfig();
            //容器挂载目录
            hostConfig.setBinds(new Bind(absolutePath, new Volume("/app")));
            //限制内存
            hostConfig.withMemory(100*100*1000L);
            hostConfig.withCpuCount(1L);
            //内存交换
            hostConfig.withMemorySwap(1000L);
            //设置安全管理，读写权限
            String profile = ResourceUtil.readUtf8Str("profile.json");
            //将一个名为"seccomp"的安全选项添加到Docker容器的安全选项列表
            hostConfig.withSecurityOpts(Arrays.asList("seccomp=" + profile));
            CreateContainerResponse containerResponse = containerCmd
                    .withReadonlyRootfs(true)  //只读
                    .withNetworkDisabled(true) //禁用网络
                    .withHostConfig(hostConfig)
                    .withAttachStderr(true) //开启输入输出
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withTty(true) //开启一个交互终端
                    .exec();
            String containerId = containerResponse.getId();
            System.out.println("创建容器id："+containerId);
            //启动容器
            dockerClient.startContainerCmd(containerId).exec();
            //执行docker exec containtId java -cp /app Main 1 2
            ArrayList<ExecuteMessage> executeMessages=new ArrayList<>();
            // 设置执行消息
            ExecuteMessage execDockerMessage = new ExecuteMessage();
            StopWatch stopWatch=new StopWatch();
            //最大内存占用
            final long[] memory={0L};
            //设置执行信息
            final String[] messageDocker = {null};
            final String[] errorDockerMessage = {null};
            long time = 0L;
            for (String input:inputList) {
                String[] inputArgsArray = input.split(" ");
                //要把命令按照空格拆分，作为一个数组传递，否则可能会被识别为一个字符串，而不是多个参数
                String[] append = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);
                //执行容器
                ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                        .withCmd(append)
                        .withAttachStderr(true)
                        .withAttachStdin(true)
                        .withAttachStdout(true)
                        .exec();
                System.out.println("创建执行命令：" + execCreateCmdResponse.getId());
                String execId = execCreateCmdResponse.getId();
                // 判断超时变量
                final boolean[] isTimeOut = {true};
                if (execId==null){
                    throw new RuntimeException("创建执行命令失败");
                }
                //执行命令，通过回调接口来获取程序的输出结果，并且通过 StreamType 来区分标准输出和错误输出
                ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {

                    @Override
                    public void onComplete() {
                        // 执行完成，设置为 false 不超时
                        isTimeOut[0] = false;
                        super.onComplete();
                    }

                    @Override
                    public void onNext(Frame frame) {
                        // 获取程序执行信息
                        StreamType streamType = frame.getStreamType();
                        if (StreamType.STDERR.equals(streamType)) {
                            errorDockerMessage[0] = new String(frame.getPayload());
                            System.out.println("输出错误结果：" + errorDockerMessage[0]);
                        } else {
                            messageDocker[0] = new String(frame.getPayload());
                            System.out.println("输出结果：" + messageDocker[0]);
                        }
                        super.onNext(frame);
                    }
                };
                //获取占用内存
                StatsCmd statsCmd = dockerClient.statsCmd(containerId);
                ResultCallback<Statistics> statisticsResultCallback = statsCmd.exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onNext(Statistics statistics) {
                        Long usageMemory = statistics.getMemoryStats().getUsage();
                        System.out.println("内存占用：" + usageMemory);
                        memory[0] = Math.max(usageMemory, memory[0]);
                    }

                    @Override
                    public void onStart(Closeable closeable) {

                    }

                    @Override
                    public void onError(Throwable throwable) {

                    }

                    @Override
                    public void onComplete() {

                    }

                    @Override
                    public void close() throws IOException {

                    }
                });
                statsCmd.exec(statisticsResultCallback);

                try {
                    //执行启动命令
                    stopWatch.start();
                    dockerClient.execStartCmd(execId)
                            .exec(execStartResultCallback)
                            .awaitCompletion();
                    stopWatch.stop();
                    time=stopWatch.getLastTaskTimeMillis();
                    //关闭统计
                    statsCmd.close();
                }catch (Exception e){
                    System.out.println("程序执行异常");
                    throw new RuntimeException(e);
                }
                System.out.println("耗时："+time+" ms");
                execDockerMessage.setMessage(messageDocker[0]);
                execDockerMessage.setErrorMessage(errorDockerMessage[0]);
                execDockerMessage.setTime(time);
                execDockerMessage.setMemory(memory[0]);
                executeMessages.add(execDockerMessage);
            }
            return executeMessages;
    }
}
