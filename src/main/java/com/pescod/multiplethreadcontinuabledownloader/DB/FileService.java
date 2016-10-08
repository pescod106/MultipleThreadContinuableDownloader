package com.pescod.multiplethreadcontinuabledownloader.DB;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Pescod on 2/1/2016.
 */
public class FileService {
    //声明数据库管理器
    private DBOpenHelper openHelper;

    public FileService(Context context) {
        //根据上下文对象实例化数据库管理器
        openHelper = new DBOpenHelper(context);
    }

    /**
     * 获取特定URI的每条线程已经下载的文件长度
     * @param path
     * @return
     */
    public Map<Integer,Integer> getData(String path){
        //获取可读的数据库句柄，一般情况下，在该操作的内部实现中，
        // 其返回的其实是可写的数据库句柄
        SQLiteDatabase db = openHelper.getReadableDatabase();
        //根据下载路径查询所有线程下载数据，返回的Cursor指向第一条记录之前
        Cursor cursor = db.rawQuery("SELECT threadId,downLength FROM filedownlog" +
                "WHERE downPath = ?",new String[]{path});
        //建立一个哈希表用于存放每条线程的已经下载的文件长度
        Map<Integer,Integer> data = new HashMap<Integer,Integer>();
        //从第一条记录开始遍历Cursor对象
        while(cursor.moveToNext()){
            //把线程ID和该线程以下载的长度设置进data哈希表中
            //data.put(cursor.getInt(0),cursor.getInt(1));
            data.put(cursor.getInt(cursor.getColumnIndexOrThrow("threadId")),
                    cursor.getInt(cursor.getColumnIndexOrThrow("downLength")));
        }
        cursor.close();//关闭cursor，释放资源
        db.close();//关闭数据库
        return data;//返回获得的每条线程和每条线程的下载长度
    }

    /**
     * 保存每条线程已经下载的文件长度
     * @param path 下载的路径
     * @param map 现在的ID和已经下载的长度的集合
     */
    public void save(String path,Map<Integer,Integer> map){
        //获得可写的数据库句柄
        SQLiteDatabase db = openHelper.getWritableDatabase();
        //开始事务，因为此处要插入多批数据
        db.beginTransaction();
        try{
            for (Map.Entry<Integer,Integer> entry:map.entrySet()){
                //插入特定下载路径，特定线程ID,已经下载的数据的长度
                db.execSQL("INSERT INTO filedownlog(downPath,threadId,downLength) VALUES(?,?,?)"
                        ,new Object[]{path,entry.getKey(),entry.getValue()});
            }
            //设置事务执行的标志为成功
            db.setTransactionSuccessful();
        }finally {
            //结束一个事务，如果事务设立了成功标志，则提交事务，否则会滚事务
            db.endTransaction();
        }
        db.close();//关闭数据库，释放相关资源
    }

    /**
     * 实时更新每条线程已经下载的文件长度
     * @param path 下载路径
     * @param threadID 线程ID
     * @param pos 下载的文件的长度
     */
    public void update(String path,int threadID,int pos){
        SQLiteDatabase db = openHelper.getWritableDatabase();
        //更新特定下载路径，特定线程，已经下载的文件长度
        db.execSQL("UPDATE filedownlog SET downLength = ? WHERE downPath = ? AND threadId = ?",
                new Object[]{pos, path, threadID});
        db.close();
    }

    /**
     * 当文件现在完成后，删除对应的下载记录
     * @param path
     */
    public void delete(String path){
        SQLiteDatabase db = openHelper.getWritableDatabase();
        //删除特定下载路径下的所有线程记录
        db.execSQL("DELETE FROM filedownlog WHERE downPath = ?",
                new String[]{path});
        db.close();
    }

}
