package com.pescod.multiplethreadcontinuabledownloader.Utility;

import android.util.Log;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by Pescod on 2/1/2016.
 */
public class DownloadThread extends Thread {
    private static final String TAG = "DownloadThread";
    private File saveFile;//下载的数据保存到的文件
    private URL downUrl;//下载的URL
    private int block;//每条线程下载的大小
    private int threadId = -1;//初始化线程ID设置
    private int downloadedLength;//该线程已经下载的数据长度
    private boolean isFinished = false;//该线程是否完成下载的标志
    private FileDownloader downloader;

    public DownloadThread(FileDownloader downloader,URL downUrl,File saveFile,
                          int block,int downloadedLength,int threadId){
        this.downloader = downloader;
        this.downUrl = downUrl;
        this.saveFile = saveFile;
        this.block = block;
        this.threadId = threadId;
        this.downloadedLength = downloadedLength;
    }
    @Override
    public void run() {//未下载完成
        if (downloadedLength<block){
            try {
                //开启HttpURLConnection连接
                HttpURLConnection httpURLConnection = (HttpURLConnection)downUrl.openConnection();
                httpURLConnection.setConnectTimeout(5*1000);
                httpURLConnection.setRequestMethod("GET");

            }catch(Exception e){
                this.downloadedLength=-1;
                print("Thread " + this.threadId+":"+e);
            }
        }
    }

    private static void print(String msg){
        Log.i(TAG,msg);
    }

    public boolean isFinished(){
        return isFinished;
    }

    public long getDownloadedLength(){
        return downloadedLength;
    }
}
