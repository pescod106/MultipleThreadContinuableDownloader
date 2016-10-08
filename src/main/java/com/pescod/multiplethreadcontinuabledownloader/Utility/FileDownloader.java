package com.pescod.multiplethreadcontinuabledownloader.Utility;

import android.content.Context;
import android.util.Log;

import com.pescod.multiplethreadcontinuabledownloader.DB.FileService;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Pescod on 2/1/2016.
 */
public class FileDownloader  {
    private static final String TAG = "FileDownloader";//设置标签，方便Logcat日志记录
    private static final int RESPONSEOK = 200;//响应码为200，即为访问成功
    private Context context;//应用程序的上下文对象
    private FileService fileService;//获取本地数据库的业务Bean
    private boolean exited;//停止下载标志
    private int downloadedSize = 0;//已下载文件长度
    private int fileSize = 0;//原始文件长度
    private DownloadThread[] threads;//根据线程数设置下载线程池
    private File saveFile;//数据保存到的本地文件
    private Map<Integer,Integer> data = new ConcurrentHashMap<Integer,Integer>();
    private int block;//每条线程下载的长度
    private String downloadUrl;//下载路径

    /**]
     * 获得线程数
     * @return
     */
    public int getThreadSize(){
        return threads.length;
    }

    /**
     * 退出下载
     */
    public void exit(){
        this.exited = true;
    }

    /**
     * 获得文件大小
     * @return
     */
    public int getFileSize(){
        return fileSize;
    }

    /**
     * 累计已下载大小
     * 使用同步关键字解决并发访问问题
     * @param size
     */
    protected synchronized void append(int size){
        //把实时下载的长度加入到总下载长度
        downloadedSize+=size;
    }

    /**
     * 更新指定线程最后下载的位置
     * @param threadId 线程ID
     * @param pos 最后下载的位置
     */
    protected synchronized void update(int threadId,int pos){
        //把指定线程ID的线程赋予最新的下载长度，以前的值会被覆盖掉
        this.data.put(threadId,pos);
        //更新数据库中指定线程的下载长度
        this.fileService.update(this.downloadUrl,threadId,pos);
    }

    /**
     * 构建文件下载器
     * @param context
     * @param downloadUrl 下载路径
     * @param fileSaveDir 文件保存目录
     * @param threadNum 下载线程数
     */
    public FileDownloader(Context context, String downloadUrl,File fileSaveDir,int threadNum) {
        try{
            this.context = context;//对上下文对象赋值
            this.downloadUrl = downloadUrl;//对下载路径赋值
            //实例化数据库操作业务Bean，此需要使用Context，因此此处的数据库是应用程序私有的
            fileService = new FileService(this.context);
            URL url = new URL(this.downloadUrl);//根据下载路径实例化URL
            //如果指定的文件不存在，则创建目录，此处可以创建多层目录
            if (!fileSaveDir.exists()){
                fileSaveDir.mkdir();
            }
            //根据下载的线程数目创建下载线程池
            this.threads = new DownloadThread[threadNum];
            //建立一个远程连接句柄，此处尚未真正连接
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
//            connection.setRequestMethod("GET");
//            connection.setConnectTimeout(5*1000);
            connection.connect();//和远程资源建立真正的连接，但尚未有返回的数据流
            printResponseHeader(connection);//答应返回的HTTP头字段的集合
            if (connection.getResponseCode()==RESPONSEOK){//此处的请求会打开返回六并获取放回的状态码，
                //用于检测是否请求成功，当返回码为200时执行下面的代码
                this.fileSize = connection.getContentLength();//根据响应获取文件大小
                if (this.fileSize<=0){
                    //当文件大小为小于等于0时抛出运行时异常
                    throw new RuntimeException("Unknow file size");
                }
                String fileName = getFileName(connection);//获取文件名称
                this.saveFile = new File(fileSaveDir,fileName);//根据文件保存目录和文件名构建保存文件
                Map<Integer,Integer> logdata = fileService.getData(downloadUrl);//获取下载记录
                if (logdata.size()>0){//如果存在下载记录
                    for (Map.Entry<Integer,Integer> entry:logdata.entrySet()){//遍历集合中的数据
                        //把各线程已经下载的数据长度放入data中
                        data.put(entry.getKey(),entry.getValue());
                    }
                }
                if (this.data.size()==this.threads.length){//如果已经下载的数据的线程数和现在设置的
                // 线程数相同，则计算所有线程已经下载的数据总长度
                    for (int i=0;i<this.threads.length;i++){//便利每条线程已经下载的数据
                        //计算已经下载的数据之和
                        this.downloadedSize+=this.data.get(i+1);
                    }
                    //打印已经下载的数据总和
                    print("已经下载的长度："+this.downloadedSize+"个字符");
                }
                //计算每条线程下载的数据长度
                this.block = (this.fileSize%this.threads.length)==0?
                        this.fileSize/this.threads.length:this.fileSize/this.threads.length+1;
            }else {
                print("服务器响应错误："+connection.getResponseCode()
                        +connection.getResponseMessage());//打印错误
                throw new RuntimeException("server response error");//抛出运行时服务器返回异常
            }
        }catch (Exception e){
            print(e.toString());
            throw new RuntimeException("Can't connection this url");
        }
    }

    /**
     * 开始下载文件
     * @param listener 监听下载数量的变化，如果不需要了解实时下载的数量，可以设置为null
     * @return 已下载文件大小
     * @throws Exception
     */
    public int download(DownloadProgressListener listener) throws Exception{
        try{
            RandomAccessFile randOut = new RandomAccessFile(this.saveFile,"rwd");
            if (this.fileSize>0){
                randOut.setLength(this.fileSize);
            }
            randOut.close();//关闭该文件，使设置生效
            URL url = new URL(this.downloadUrl);
            if (this.data.size()!=this.threads.length){//如果原先未曾下载或
            // 者原先的下载数与现在的线程数不一致
                this.data.clear();//删除Map中所有元素
                for (int i=0;i<this.threads.length;i++){
                    this.data.put(i+1,0);//初始化每条线程已经下载的数据长度为0
                }
                this.downloadedSize = 0;//设置已经下载的长度为0
            }
            for (int i=0;i<this.threads.length;i++){//开始线程进行下载
                int downloadedLength = this.data.get(i+1);//进行特定的线程ID获取该线程已经下载的数据长度
                if (downloadedLength<this.block&&this.downloadedSize<this.fileSize){//判断线程是
                // 否已经完成下载，否则继续下载

                    //初始化特定ID的线程
                    this.threads[i] = new DownloadThread(
                            this,url,this.saveFile,this.block,this.data.get(i+1),i+1);
                    //设置线程的优先级
                    this.threads[i].setPriority(7);
//                    Thread.NORM_PRIORITY = 5;
//                    Thread.MIN_PRIORITY = 1;
//                    Thread.MAX_PRIORITY = 10;
                    this.threads[i].start();//启动线程
                }else{
                    this.threads[i] = null;//表明在线程已经完成下载任务
                }
            }
            fileService.delete(this.downloadUrl);//如果存在下载记录，删除它们，然后重新添加
            fileService.save(this.downloadUrl,this.data);//把已经下载的实时数据写入数据库
            boolean notFinished = true;//下载未完成
            while(notFinished){//循环判断所有线程是否完成下载
                Thread.sleep(900);
                notFinished = false;//假设全部线程下载完毕
                for (int i=0;i<this.threads.length;i++){
                    if (this.threads[i]!=null&&!this.threads[i].isFinished()){//如果发现线程未完成下载
                        notFinished = true;//设置标志微笑在没有完成
                        if (this.threads[i].getDownloadedLength()==-1){//如果下载失败，再重新
                        // 在已经下载的数据的基础上下载
                            //重启开辟下载线程
                            this.threads[i] = new DownloadThread(
                                    this,url,this.saveFile,this.block,this.data.get(i+1),i+1);
                            this.threads[i].setPriority(7);//设置下载的优先级
                            this.threads[i].start();//开始下载线程
                        }
                    }
                }
                if (listener!=null){
                    //通知目前已经下载完成的数据长度
                    listener.onDownloadSize(this.downloadedSize);
                }
            }
            if (this.downloadedSize==this.fileSize){
                //下载完成，删除记录 
                fileService.delete(this.downloadUrl);
            }
        }catch(Exception e){
            print(e.toString());
            throw new RuntimeException("File downloads error");
        }
        return this.downloadedSize;
    }

    /**
     * 获取文件名
     * @param connection
     * @return
     */
    public String getFileName(HttpURLConnection connection){
        //从下载路径的字符串中获取文件名称
        String fileName = this.downloadUrl.substring(
                this.downloadUrl.lastIndexOf("/")+1);
        //如果获取不到文件名称
        if (fileName==null||"".equals(fileName.trim())){
            for (int i=0;;i++){
                //从返回的流中获取特定索引的头字段值
                String mine = connection.getHeaderField(i);
                if (mine==null){
                    break;//如果遍历到了返回头的末尾处，退出循环
                }
                //获取content-disposition返回头字段，里面可能包含文件名
                if ("content-disposition".equals(
                        connection.getHeaderFieldKey(i).toLowerCase())){
                    //使用正则表达式查询文件名
                    Matcher matcher = Pattern.compile(".*fileName=(.*)").
                            matcher(mine.toLowerCase());
                    if (matcher.find()){//如果有符合正则表达规则的字符串
                        return matcher.group(1);
                    }
                }
            }
            //由网卡上的标识数字（每个网卡都有唯一的标识号）及CPU时钟的唯一数字生成的一个
            //16字节的二进制数作为文件名
            fileName = UUID.randomUUID()+".tmp";
        }
        return fileName;
    }
    /**
     * 获取Http响应头字段
     * @param httpURLConnection
     * @return 返回头字段的LinkedHashMap
     */
    public static Map<String,String> getHttpResponseHeader(HttpURLConnection httpURLConnection){
        //使用LinkedHashMap，保证写入和遍历的时候的数序相同，而且允许空值存在
        Map<String,String> header = new LinkedHashMap<String,String>();
        for (int i=0;;i++){
            //getHeaderField(i)用于返回第n个头字段的值
            String fieldValue = httpURLConnection.getHeaderField(i);
            //如果第i个字段没有值了，则表明头字段部分已经循环完毕，退出循环
            if (fieldValue==null){
                break;
            }
            //getHeaderFieldKey(i)用于返回第n个头字段的键
            header.put(httpURLConnection.getHeaderFieldKey(i),fieldValue);
        }
        return header;
    }

    /**
     * 获取HTTP头字段
     * @param httpURLConnection
     */
    public static void printResponseHeader(HttpURLConnection httpURLConnection){
        //获取HTTP响应头字段
        Map<String,String> header = getHttpResponseHeader(httpURLConnection);
        //遍历获取头字段的值，此时遍历的循环和输入的顺序相同
        for (Map.Entry<String,String> entry : header.entrySet()){
            //当有键的时候截获取键，如果没有则为空字符串
            String key  = entry.getKey()!=null?entry.getKey()+":":"";
            print(key+entry.getValue());
        }
    }

    /**
     * 打印信息
     * @param msg 信息字符串
     */
    private static void print(String msg){
        Log.i(TAG,msg);
    }
}
