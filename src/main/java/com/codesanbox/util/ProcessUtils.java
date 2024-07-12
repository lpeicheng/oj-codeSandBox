package com.codesanbox.util;

import cn.hutool.core.util.StrUtil;
import com.codesanbox.model.ExecuteMessage;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

//执行进程获取信息
public class ProcessUtils {
   //提前输入答案执行
   public static ExecuteMessage runAndProcess(Process process,String name){  //name：进程名
      ExecuteMessage executeMessage=new ExecuteMessage();
      // 记录初始内存使用情况
      long initialMemory = getUsedMemory();
      try {
         // 设置计时器
         StopWatch stopWatch = new StopWatch();
         stopWatch.start();
         //等待执行获取退出码
         int exitValue= process.waitFor();
         executeMessage.setExitValue(exitValue);
         if (exitValue==0){
            System.out.println(name+"成功");
            //获取执行信息
            BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(process.getInputStream()));
            //使用集合代替StringBuilder，使用StringBuilder会出现得出的结果不一致的问题，有可能是因为StringBuilder是立即进行拼接，每次都重新创建StringBuilder对象，而集合是先创建再添加
            List<String> outputStrList = new ArrayList<>();
            String line;
            while ((line=bufferedReader.readLine())!=null){
               outputStrList.add(line);
            }
            executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
         }else {
            System.out.println(name+"失败，错误码："+exitValue);
            executeMessage.setExitValue(exitValue);
            //获取正常输出
            BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(process.getInputStream()));
            List<String> outputStrList = new ArrayList<>();

            String line;
            while ((line=bufferedReader.readLine())!=null){
               outputStrList.add(line);

            }
            executeMessage.setMessage(StringUtils.join(outputStrList, "\n"));
            //获取错误信息
            BufferedReader errorbufferedReader=new BufferedReader(new InputStreamReader(process.getErrorStream()));
            List<String> errorOutputStrList = new ArrayList<>();
            String errorLine;
            while ((errorLine=errorbufferedReader.readLine())!=null){
               errorOutputStrList.add(errorLine);

            }
            executeMessage.setErrorMessage(StringUtils.join(errorOutputStrList, "\n"));
         }
         // 记录执行后的内存使用情况
         long finalMemory = getUsedMemory();
         // 计算内存使用量，单位字节，转换成kb需要除以1024
         long memoryUsage = finalMemory - initialMemory;
         executeMessage.setMemory(memoryUsage / 1024);
         stopWatch.stop();
         executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
      } catch (Exception e) {
         e.printStackTrace();
      }
      return executeMessage;
   }

   //交互式进程获取信息
   public static ExecuteMessage runInteractProcess(Process process, String args){
      ExecuteMessage executeMessage=new ExecuteMessage();
      try {
         //向控制台输入命令，使用outputStream或inputStream代替输入流都行
         OutputStream outputStream = process.getOutputStream();
         OutputStreamWriter outputStreamWriter=new OutputStreamWriter(outputStream);
         String[] s = args.split(" ");
         String join = StrUtil.join("\n", s)+"\n";
         outputStreamWriter.write(join);
         //回车
         outputStreamWriter.flush();
         //获取正常输出
         InputStream inputStream = process.getInputStream();
         BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(inputStream));
         StringBuilder stringBuilder=new StringBuilder();
         String line;
         while ((line=bufferedReader.readLine())!=null){
            stringBuilder.append(line);
         }
         executeMessage.setMessage(stringBuilder.toString());
         //释放资源，防止卡死
         outputStreamWriter.close();
         outputStream.close();
         inputStream.close();
         process.destroy();
      }catch (Exception e){
         e.printStackTrace();
      }
      return executeMessage;
   }

   /**
    * 获取当前已使用的内存量
    * 单位是byte
    *
    * @return
    */
   public static long getUsedMemory()
   {
      Runtime runtime = Runtime.getRuntime();
      return runtime.totalMemory() - runtime.freeMemory();
   }
}
