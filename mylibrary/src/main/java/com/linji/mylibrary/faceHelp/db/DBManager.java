package com.linji.mylibrary.faceHelp.db;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import com.linji.mylibrary.faceHelp.listener.DBLoadListener;
import com.linji.mylibrary.faceHelp.model.Group;
import com.linji.mylibrary.faceHelp.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 数据库管理类
 */
public class DBManager {
    /**
     * The constant TAG
     */
    private static final String TAG = "DBManager";

    private AtomicInteger mOpenCounter = new AtomicInteger();
    private static DBManager instance;
    private static SQLiteOpenHelper mDBHelper;
    private SQLiteDatabase mDatabase;
    private boolean allowTransaction = true;
    private Lock writeLock = new ReentrantLock();
    private volatile boolean writeLocked = false;
    // 默认组别是0
    public static final String GROUP_ID = "0";

    /**
     * 单例模式，初始化DBManager
     *
     * @return DBManager实例
     */
    public static synchronized DBManager getInstance() {
        if (instance == null) {
            instance = new DBManager();
        }
        return instance;
    }


    /**
     * 数据库初始化
     *
     * @param context 当前上下文
     */
    public void init(Context context) {
        if (context == null) {
            return;
        }

        if (mDBHelper == null) {
            mDBHelper = new DBHelper(context.getApplicationContext());
        }
    }

    /**
     * 释放数据库
     */
    public void release() {
        if (mDBHelper != null) {
            mDBHelper.close();
            mDBHelper = null;
        }
        instance = null;
    }

    /**
     * 打开数据库
     */
    public synchronized SQLiteDatabase openDatabase() {
        if (mOpenCounter.incrementAndGet() == 1) {
            // Opening new database
            try {
                mDatabase = mDBHelper.getWritableDatabase();
            } catch (Exception e) {
                Log.e(TAG, "openDatabase e = " + e.getMessage());
                mDatabase = mDBHelper.getReadableDatabase();
            }
        }
        return mDatabase;
    }

    /**
     * 关闭数据库
     */
    public synchronized void closeDatabase() {
        if (mOpenCounter.decrementAndGet() == 0) {
            // Closing database
            mDatabase.close();
        }
    }

    // ---------------------------------------用户组相关（暂时废弃） start--------------------------------------

    /**
     * 添加用户组
     */
    public boolean addGroup(Group group) {
        if (mDBHelper == null) {
            return false;
        }
        Cursor cursor = null;

        SQLiteDatabase db = mDBHelper.getReadableDatabase();
        String where = "group_id = ? ";
        String[] whereValue = {group.getGroupId()};
        // 查询该groupId是否在数据库中，如果在，则不添加
        cursor = db.query(DBHelper.TABLE_USER_GROUP, null, where, whereValue,
                null, null, null);
        if (cursor == null) {
            return false;
        }

        if (cursor.getCount() > 0) {
            return true;
        }

        mDatabase = mDBHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("group_id", group.getGroupId());
        cv.put("desc", group.getDesc() == null ? "" : group.getDesc());
        cv.put("update_time", System.currentTimeMillis());
        cv.put("ctime", System.currentTimeMillis());

        long rowId = -1;
        try {
            rowId = mDatabase.insert(DBHelper.TABLE_USER_GROUP, null, cv);
        } catch (Exception e) {
            Log.e(TAG, "addGroup e = " + e.getMessage());
            e.printStackTrace();
        }
        if (rowId < 0) {
            return false;
        }
        Log.i(TAG, "insert group success:" + rowId);
        closeCursor(cursor);
        return true;
    }

    /**
     * 查询用户组（查询所有）
     */
    public List<Group> queryGroups(int start, int offset) {
        Cursor cursor = null;
        List<Group> groupList = new ArrayList<>();
        try {
            if (mDBHelper == null) {
                return null;
            }
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            String limit = start + " , " + offset;
            cursor = db.query(DBHelper.TABLE_USER_GROUP, null, null, null, null, null, null, limit);
            while (cursor != null && cursor.getCount() > 0 && cursor.moveToNext()) {
                int dbId = cursor.getInt(cursor.getColumnIndex("_id"));
                String groupId = cursor.getString(cursor.getColumnIndex("group_id"));
                String desc = cursor.getString(cursor.getColumnIndex("desc"));
                long updateTime = cursor.getLong(cursor.getColumnIndex("update_time"));
                long ctime = cursor.getLong(cursor.getColumnIndex("ctime"));

                Group group = new Group();
                group.setGroupId(groupId);
                group.setDesc(desc);
                group.setCtime(ctime);
                groupList.add(group);
            }
        } finally {
            closeCursor(cursor);
        }
        return groupList;
    }
    /**
     * 查询用户组（根据groupId）
     */
    public List<Group> queryGroupsByGroupId(String groupId) {
        ArrayList<Group> groupList = new ArrayList<>();
        Cursor cursor = null;

        try {
            if (mDBHelper == null) {
                return groupList;
            }
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            String where = "group_id = ? ";
            String[] whereValue = {groupId};
            cursor = db.query(DBHelper.TABLE_USER_GROUP, null, where, whereValue, null, null, null);
            while (cursor != null && cursor.getCount() > 0 && cursor.moveToNext()) {
                int dbId = cursor.getInt(cursor.getColumnIndex("_id"));
                String desc = cursor.getString(cursor.getColumnIndex("desc"));
                long updateTime = cursor.getLong(cursor.getColumnIndex("update_time"));
                long ctime = cursor.getLong(cursor.getColumnIndex("ctime"));

                Group group = new Group();
                group.setGroupId(groupId);
                group.setDesc(desc);
                group.setCtime(ctime);
                groupList.add(group);
            }
        } catch (Exception e) {
            Log.e(TAG, "queryGroupsByGroupId e = " + e.getMessage());
        } finally {
            closeCursor(cursor);
        }
        return groupList;
    }

    /**
     * 删除用户组
     */
    public boolean deleteGroup(String groupId) {
        boolean success = false;
        try {
            mDatabase = mDBHelper.getWritableDatabase();
            beginTransaction(mDatabase);

            if (!TextUtils.isEmpty(groupId)) {
                String where = "group_id = ?";
                String[] whereValue = {groupId};

                if (mDatabase.delete(DBHelper.TABLE_USER, where, whereValue) < 0) {
                    return false;
                }
                if (mDatabase.delete(DBHelper.TABLE_USER_GROUP, where, whereValue) < 0) {
                    return false;
                }

                setTransactionSuccessful(mDatabase);
                success = true;
            }

        } finally {
            endTransaction(mDatabase);
        }
        return success;
    }

    // ---------------------------------------用户组相关（暂时废弃） end----------------------------------------

    // ---------------------------------------用户相关 start----------------------------------------

    /**
     * 添加用户
     */
    public boolean addUser(User user) {
        if (mDBHelper == null) {
            return false;
        }
        try {
            mDatabase = mDBHelper.getWritableDatabase();
            beginTransaction(mDatabase);

            ContentValues cv = new ContentValues();
            cv.put("user_id", user.getUserId());
            cv.put("user_name", user.getUserName());
            cv.put("user_info", user.getUserInfo());
            cv.put("group_id", GROUP_ID);
            cv.put("face_token", user.getFaceToken());
            cv.put("feature", user.getFeature());
            cv.put("image_name", user.getImageName());
            cv.put("ctime", System.currentTimeMillis());
            cv.put("update_time", System.currentTimeMillis());

            long rowId = mDatabase.insert(DBHelper.TABLE_USER, null, cv);
            if (rowId < 0) {
                return false;
            }

            setTransactionSuccessful(mDatabase);
            Log.i(TAG, "insert user success:" + rowId);
        } catch (Exception e) {
            Log.e(TAG, "addUser e = " + e.getMessage());
            return false;
        } finally {
            endTransaction(mDatabase);
        }
        return true;
    }

    /**
     * 查询所有用户（按时间降序排序）
     */
    public void queryAllUsers(DBLoadListener dbLoadListener) {
        Cursor cursor = null;
        List<User> features = new ArrayList<>();
        int mSuccessCount = 0;        // 已获取成功的人脸数量
        int count = 0;// 总数
        try {
            if (mDBHelper == null) {
                return;
            }
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            String where = "group_id = ? order by ctime desc";
            String[] whereValue = {GROUP_ID};
            cursor = db.query(DBHelper.TABLE_USER, null, where, whereValue, null, null, null);
            count = cursor.getCount();
            dbLoadListener.onStart(count);
            while (cursor != null && cursor.getCount() > 0 && cursor.moveToNext()) {
                int dbId = cursor.getInt(cursor.getColumnIndex("_id"));
                String userId = cursor.getString(cursor.getColumnIndex("user_id"));
                String userName = cursor.getString(cursor.getColumnIndex("user_name"));
                String userInfo = cursor.getString(cursor.getColumnIndex("user_info"));
                String faceToken = cursor.getString(cursor.getColumnIndex("face_token"));
                byte[] feature = cursor.getBlob(cursor.getColumnIndex("feature"));
                String imageName = cursor.getString(cursor.getColumnIndex("image_name"));
                long updateTime = cursor.getLong(cursor.getColumnIndex("update_time"));
                long ctime = cursor.getLong(cursor.getColumnIndex("ctime"));

                User user = new User();
                user.setId(dbId);
                user.setUserId(userId);
                user.setGroupId(GROUP_ID);
                user.setUserName(userName);
                user.setCtime(ctime);
                user.setUpdateTime(updateTime);
                user.setUserInfo(userInfo);
                user.setFaceToken(faceToken);
                user.setFeature(feature);
                user.setImageName(imageName);
                features.add(user);
                mSuccessCount++;
                if (dbLoadListener != null){
                    dbLoadListener.onLoad(mSuccessCount , count ,
                            ((float)mSuccessCount / (float)count));
                }
            }
            if (dbLoadListener != null){
                dbLoadListener.onComplete(features , count);
            }
        } catch (Exception e){
            if (dbLoadListener != null){
                dbLoadListener.onFail(mSuccessCount , count , features);
            }
        } finally {
            closeCursor(cursor);
        }
    }


    /**
     * 查询所有用户（按时间降序排序）
     */
    public List<User> queryAllUsers() {
        Cursor cursor = null;
        List<User> users = new ArrayList<>();
        try {
            if (mDBHelper == null) {
                return null;
            }
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            String where = "group_id = ? order by ctime desc";
            String[] whereValue = {GROUP_ID};
            cursor = db.query(DBHelper.TABLE_USER, null, where, whereValue, null, null, null);
            while (cursor != null && cursor.getCount() > 0 && cursor.moveToNext()) {
                int dbId = cursor.getInt(cursor.getColumnIndex("_id"));
                String userId = cursor.getString(cursor.getColumnIndex("user_id"));
                String userName = cursor.getString(cursor.getColumnIndex("user_name"));
                String userInfo = cursor.getString(cursor.getColumnIndex("user_info"));
                String faceToken = cursor.getString(cursor.getColumnIndex("face_token"));
                byte[] feature = cursor.getBlob(cursor.getColumnIndex("feature"));
                String imageName = cursor.getString(cursor.getColumnIndex("image_name"));
                long updateTime = cursor.getLong(cursor.getColumnIndex("update_time"));
                long ctime = cursor.getLong(cursor.getColumnIndex("ctime"));

                User user = new User();
                user.setId(dbId);
                user.setUserId(userId);
                user.setGroupId(GROUP_ID);
                user.setUserName(userName);
                user.setCtime(ctime);
                user.setUpdateTime(updateTime);
                user.setUserInfo(userInfo);
                user.setFaceToken(faceToken);
                user.setFeature(feature);
                user.setImageName(imageName);
                users.add(user);
            }
        } finally {
            closeCursor(cursor);
        }
        return users;
    }

    /**
     * 查询用户（根据userId）
     */
    public User queryUser(String userId) {
        Cursor cursor = null;

        try {
            if (mDBHelper == null) {
                return null;
            }
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            String where = "user_id = ? and group_id = ? ";
            String[] whereValue = {userId, GROUP_ID};
            cursor = db.query(DBHelper.TABLE_USER, null, where, whereValue, null, null, null);
            if (cursor != null && cursor.getCount() > 0 && cursor.moveToNext()) {
                int dbId = cursor.getInt(cursor.getColumnIndex("_id"));
                String userName = cursor.getString(cursor.getColumnIndex("user_name"));
                long updateTime = cursor.getLong(cursor.getColumnIndex("update_time"));
                long ctime = cursor.getLong(cursor.getColumnIndex("ctime"));

                User user = new User();
                user.setId(dbId);
                user.setUserId(userId);
                user.setGroupId(GROUP_ID);
                user.setUserName(userName);
                user.setCtime(ctime);
                user.setUpdateTime(updateTime);
                return user;
            }
        } finally {
            closeCursor(cursor);
        }
        return null;
    }

    /**
     * 查询用户（根据userName精确查询）
     */
    public List<User> queryUserByUserNameAccu(String userName) {
        Cursor cursor = null;
        List<User> users = new ArrayList<>();
        try {
            if (mDBHelper == null) {
                return null;
            }
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            String where = "user_name = ? and group_id = ? ";
            String[] whereValue = {userName, GROUP_ID};
            cursor = db.query(DBHelper.TABLE_USER, null, where, whereValue, null, null, null);
            if (cursor != null && cursor.getCount() > 0 && cursor.moveToNext()) {
                int dbId = cursor.getInt(cursor.getColumnIndex("_id"));
                String userId = cursor.getString(cursor.getColumnIndex("user_id"));
                String userInfo = cursor.getString(cursor.getColumnIndex("user_info"));
                String faceToken = cursor.getString(cursor.getColumnIndex("face_token"));
                byte[] feature = cursor.getBlob(cursor.getColumnIndex("feature"));
                String imageName = cursor.getString(cursor.getColumnIndex("image_name"));
                long updateTime = cursor.getLong(cursor.getColumnIndex("update_time"));
                long ctime = cursor.getLong(cursor.getColumnIndex("ctime"));

                User user = new User();
                user.setId(dbId);
                user.setUserId(userId);
                user.setGroupId(GROUP_ID);
                user.setUserName(userName);
                user.setCtime(ctime);
                user.setUpdateTime(updateTime);
                user.setUserInfo(userInfo);
                user.setFeature(feature);
                user.setImageName(imageName);
                user.setFaceToken(faceToken);
                users.add(user);
            }
        } finally {
            closeCursor(cursor);
        }
        return users;
    }
    /**
     * 查询单一用户（根据userName模糊查询）
     */
    public User queryUserByUserNameItem(String userName) {
        Cursor cursor = null;
        User user = null;
        try {
            if (mDBHelper == null) {
                return null;
            }
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            String where = "user_name LIKE ? and group_id = ? order by ctime desc";
            String[] whereValue = {"%" + userName + "%", GROUP_ID};
            cursor = db.query(DBHelper.TABLE_USER, null, where, whereValue, null, null, null);
            if (cursor != null && cursor.getCount() > 0 && cursor.moveToNext()) {
                int dbId = cursor.getInt(cursor.getColumnIndex("_id"));
                String userId = cursor.getString(cursor.getColumnIndex("user_id"));
                String userNameQ = cursor.getString(cursor.getColumnIndex("user_name"));
                String userInfo = cursor.getString(cursor.getColumnIndex("user_info"));
                String faceToken = cursor.getString(cursor.getColumnIndex("face_token"));
                byte[] feature = cursor.getBlob(cursor.getColumnIndex("feature"));
                String imageName = cursor.getString(cursor.getColumnIndex("image_name"));
                long updateTime = cursor.getLong(cursor.getColumnIndex("update_time"));
                long ctime = cursor.getLong(cursor.getColumnIndex("ctime"));

                user = new User();
                user.setId(dbId);
                user.setUserId(userId);
                user.setGroupId(GROUP_ID);
                user.setUserName(userNameQ);
                user.setCtime(ctime);
                user.setUpdateTime(updateTime);
                user.setUserInfo(userInfo);
                user.setFeature(feature);
                user.setImageName(imageName);
                user.setFaceToken(faceToken);
            }
        } finally {
            closeCursor(cursor);
        }
        return user;
    }
    /**
     * 查询用户（根据userName模糊查询）
     */
    public List<User> queryUserByUserNameVag(String userName) {
        Cursor cursor = null;
        List<User> users = new ArrayList<>();
        try {
            if (mDBHelper == null) {
                return null;
            }
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            String where = "user_name LIKE ? and group_id = ? order by ctime desc";
            String[] whereValue = {"%" + userName + "%", GROUP_ID};
            cursor = db.query(DBHelper.TABLE_USER, null, where, whereValue, null, null, null);
            while (cursor != null && cursor.getCount() > 0 && cursor.moveToNext()) {
                int dbId = cursor.getInt(cursor.getColumnIndex("_id"));
                String userId = cursor.getString(cursor.getColumnIndex("user_id"));
                String userNameQ = cursor.getString(cursor.getColumnIndex("user_name"));
                String userInfo = cursor.getString(cursor.getColumnIndex("user_info"));
                String faceToken = cursor.getString(cursor.getColumnIndex("face_token"));
                byte[] feature = cursor.getBlob(cursor.getColumnIndex("feature"));
                String imageName = cursor.getString(cursor.getColumnIndex("image_name"));
                long updateTime = cursor.getLong(cursor.getColumnIndex("update_time"));
                long ctime = cursor.getLong(cursor.getColumnIndex("ctime"));

                User user = new User();
                user.setId(dbId);
                user.setUserId(userId);
                user.setGroupId(GROUP_ID);
                user.setUserName(userNameQ);
                user.setCtime(ctime);
                user.setUpdateTime(updateTime);
                user.setUserInfo(userInfo);
                user.setFeature(feature);
                user.setImageName(imageName);
                user.setFaceToken(faceToken);
                users.add(user);
            }
        } finally {
            closeCursor(cursor);
        }
        return users;
    }

    /**
     * 查询用户（根据dbId）
     */
    public List<User> queryUserById(int _id) {
        List<User> users = new ArrayList<>();
        Cursor cursor = null;
        try {
            if (mDBHelper == null) {
                return null;
            }
            SQLiteDatabase db = mDBHelper.getReadableDatabase();
            String where = "_id = ? ";
            String[] whereValue = {String.valueOf(_id)};
            cursor = db.query(DBHelper.TABLE_USER, null, where, whereValue, null, null, null);
            if (cursor != null && cursor.getCount() > 0 && cursor.moveToNext()) {
                String groupId = cursor.getString(cursor.getColumnIndex("group_id"));
                String userId = cursor.getString(cursor.getColumnIndex("user_id"));
                String userName = cursor.getString(cursor.getColumnIndex("user_name"));
                String userInfo = cursor.getString(cursor.getColumnIndex("user_info"));
                String faceToken = cursor.getString(cursor.getColumnIndex("face_token"));
                byte[] feature = cursor.getBlob(cursor.getColumnIndex("feature"));
                String imageName = cursor.getString(cursor.getColumnIndex("image_name"));
                long updateTime = cursor.getLong(cursor.getColumnIndex("update_time"));
                long ctime = cursor.getLong(cursor.getColumnIndex("ctime"));

                User user = new User();
                user.setId(_id);
                user.setUserId(userId);
                user.setGroupId(groupId);
                user.setUserName(userName);
                user.setCtime(ctime);
                user.setUpdateTime(updateTime);
                user.setUserInfo(userInfo);
                user.setFeature(feature);
                user.setImageName(imageName);
                user.setFaceToken(faceToken);
                users.add(user);
            }
        } finally {
            closeCursor(cursor);
        }
        return users;
    }

    /**
     * 更新用户（通过传入User类的方式）
     */
    public boolean updateUser(User user) {
        boolean success = false;
        if (mDBHelper == null) {
            return success;
        }

        try {
            mDatabase = mDBHelper.getWritableDatabase();
            beginTransaction(mDatabase);

            if (user != null) {
                mDatabase.beginTransaction();
                String where = "user_id = ? and group_id = ?";
                String[] whereValue = {user.getUserId(), GROUP_ID};
                ContentValues cv = new ContentValues();

                cv.put("user_id", user.getUserId());
                cv.put("user_name", user.getUserName());
                cv.put("group_id", GROUP_ID);
                cv.put("image_name", user.getImageName());
                cv.put("update_time", System.currentTimeMillis());

                if (mDatabase.update(DBHelper.TABLE_USER, cv, where, whereValue) < 0) {
                    return false;
                }
            }
            setTransactionSuccessful(mDatabase);
            success = true;
        } finally {
            endTransaction(mDatabase);
        }
        return success;
    }

    /**
     * 更新用户（通过传入图片、命名的方式）
     */
    public boolean updateUser(String userName, String imageName, byte[] feature) {

        if (mDBHelper == null) {
            return false;
        }
        try {
            mDatabase = mDBHelper.getWritableDatabase();
            beginTransaction(mDatabase);

            String where = "user_name = ? and group_id = ?";
            String[] whereValue = {userName, GROUP_ID};
            ContentValues cv = new ContentValues();

            cv.put("user_name", userName);
            cv.put("group_id", GROUP_ID);
            cv.put("image_name", imageName);
            cv.put("update_time", System.currentTimeMillis());
            cv.put("feature", feature);

            if (mDatabase.update(DBHelper.TABLE_USER, cv, where, whereValue) < 0) {
                return false;
            }
            setTransactionSuccessful(mDatabase);
        } finally {
            endTransaction(mDatabase);
        }
        return true;
    }
    /**
     * 删除用户
     */
    public boolean deleteUser(int id) {
        boolean success = false;
        try {
            mDatabase = mDBHelper.getWritableDatabase();
            beginTransaction(mDatabase);

                String where = "_id = ? and group_id = ?";
                String[] whereValue = {id + "", GROUP_ID};

                if (mDatabase.delete(DBHelper.TABLE_USER, where, whereValue) < 0) {
                    return false;
                }

                setTransactionSuccessful(mDatabase);
                success = true;

        } finally {
            endTransaction(mDatabase);
        }
        return success;
    }
    /**
     * 删除用户
     */
    public boolean deleteUser(String userId) {
        boolean success = false;
        try {
            mDatabase = mDBHelper.getWritableDatabase();
            beginTransaction(mDatabase);

            if (!TextUtils.isEmpty(userId)) {
                String where = "user_id = ? and group_id = ?";
                String[] whereValue = {userId, GROUP_ID};

                if (mDatabase.delete(DBHelper.TABLE_USER, where, whereValue) < 0) {
                    return false;
                }

                setTransactionSuccessful(mDatabase);
                success = true;
            }

        } finally {
            endTransaction(mDatabase);
        }
        return success;
    }

    // ---------------------------------------用户相关 end------------------------------------------

    private void beginTransaction(SQLiteDatabase mDatabase) {
        if (allowTransaction) {
            mDatabase.beginTransaction();
        } else {
            writeLock.lock();
            writeLocked = true;
        }
    }

    private void setTransactionSuccessful(SQLiteDatabase mDatabase) {
        if (allowTransaction) {
            mDatabase.setTransactionSuccessful();
        }
    }

    private void endTransaction(SQLiteDatabase mDatabase) {
        if (allowTransaction) {
            mDatabase.endTransaction();
        }
        if (writeLocked) {
            writeLock.unlock();
            writeLocked = false;
        }
    }

    private void closeCursor(Cursor cursor) {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Throwable e) {
                Log.e(TAG, "closeCursor e = " + e.getMessage());
            }
        }
    }

    /**
     * 清空表
     */
    public void clearTable() {
        SQLiteDatabase database = mDBHelper.getWritableDatabase();
        database.execSQL("delete from " + DBHelper.TABLE_USER);
    }

    // ---------------------------------------socket相关 start--------------------------------------
    // 获取识别记录
//    public List<ResponseGetRecords> queryRecords(String startTime, String endTime) {
//        List<ResponseGetRecords> responseGetRecords = new ArrayList<>();
//        Cursor cursor = null;
//        try {
//            if (mDBHelper == null) {
//                return null;
//            }
//            SQLiteDatabase db = mDBHelper.getReadableDatabase();
//            if (!TextUtils.isEmpty(startTime) && !TextUtils.isEmpty(endTime)) {
//                String where = "time > ? and time < ? ";
//                String[] whereValue = {startTime, endTime};
//                cursor = db.query(DBHelper.TABLE_RECORDS, null, where, whereValue, null, null, null);
//            } else {
//                String sql = "select * from " + DBHelper.TABLE_RECORDS;
//                cursor = db.rawQuery(sql, null);
//            }
//
//            if (cursor != null && cursor.getCount() > 0) {
//                while (cursor.moveToNext()) {
//                    int dbId = cursor.getInt(cursor.getColumnIndex("_id"));
//                    String userId = cursor.getString(cursor.getColumnIndex("user_id"));
//                    String records = cursor.getString(cursor.getColumnIndex("records"));
//                    String longId = cursor.getString(cursor.getColumnIndex("longId"));
//                    String score = cursor.getString(cursor.getColumnIndex("score"));
//                    String deviceId = cursor.getString(cursor.getColumnIndex("deviceid"));
//                    String time = cursor.getString(cursor.getColumnIndex("time"));
//                    String faceToken = cursor.getString(cursor.getColumnIndex("face_token"));
//                    String groupId = cursor.getString(cursor.getColumnIndex("group_id"));
//                    String userName = cursor.getString(cursor.getColumnIndex("user_name"));
//
//                    ResponseGetRecords responseGetRecord = new ResponseGetRecords();
//                    responseGetRecord.setIndex(String.valueOf(dbId));
//                    responseGetRecord.setUserId(userId);
//                    responseGetRecord.setDeviceId(deviceId);
//                    responseGetRecord.setRecords(records);
//                    responseGetRecord.setLogId(longId);
//                    responseGetRecord.setScore(score);
//                    responseGetRecord.setTime(time);
//                    responseGetRecord.setFaceToken("");
//                    responseGetRecord.setGroupId(groupId);
//                    responseGetRecord.setUserName(userName);
//
//                    responseGetRecords.add(responseGetRecord);
//                }
//            }
//        } catch (Exception e) {
//            Log.e("shangtest", e.getMessage());
//        } finally {
//            closeCursor(cursor);
//        }
//        return responseGetRecords;
//    }
//
//
//    // 添加识别记录
//    public boolean addResponseGetRecords(ResponseGetRecords responseGetRecords) {
//        if (mDBHelper == null) {
//            return false;
//        }
//        try {
//            mDatabase = mDBHelper.getWritableDatabase();
//            beginTransaction(mDatabase);
//
//            ContentValues cv = new ContentValues();
//            cv.put("user_id", responseGetRecords.getUserId());
//            cv.put("records", responseGetRecords.getRecords());
//            cv.put("longId", responseGetRecords.getLogId());
//            cv.put("score", responseGetRecords.getScore());
//            cv.put("deviceid", responseGetRecords.getDeviceId());
//            cv.put("time", responseGetRecords.getTime());
//            cv.put("user_name", responseGetRecords.getUserName());
//            cv.put("group_id", responseGetRecords.getGroupId());
//            cv.put("face_token", responseGetRecords.getFaceToken());
//
//            long rowId = mDatabase.insert(DBHelper.TABLE_RECORDS, null, cv);
//            if (rowId < 0) {
//                return false;
//            }
//
//            setTransactionSuccessful(mDatabase);
//        } catch (Exception e) {
//            return false;
//        } finally {
//            endTransaction(mDatabase);
//        }
//        return true;
//    }
//
//
//    // 根据时间删除识别记录
//    public boolean deleteRecords(String startTime, String endTime) {
//        boolean success = false;
//        try {
//            mDatabase = mDBHelper.getWritableDatabase();
//            beginTransaction(mDatabase);
//
//            if (!TextUtils.isEmpty(startTime) && !TextUtils.isEmpty(endTime)) {
//                String where = " time > ? and time < ?";
//                String[] whereValue = {startTime, endTime};
////                String[] whereValue = {"2019-09-05 10:50:09", "2019-09-05 10:50:13"};
//
//                if (mDatabase.delete(DBHelper.TABLE_RECORDS, where, whereValue) < 0) {
//                    return false;
//                }
//
//                setTransactionSuccessful(mDatabase);
//                success = true;
//            }
//
//        } finally {
//            endTransaction(mDatabase);
//        }
//        return success;
//    }

    // 根据userId删除识别记录
    public boolean deleteRecords(String userName) {
        boolean success = false;
        try {
            mDatabase = mDBHelper.getWritableDatabase();
            beginTransaction(mDatabase);

            if (!TextUtils.isEmpty(userName)) {
                String where = "user_name = ?";
                String[] whereValue = {userName};

                if (mDatabase.delete(DBHelper.TABLE_RECORDS, where, whereValue) < 0) {
                    return false;
                }

                setTransactionSuccessful(mDatabase);
                success = true;
            }

        } finally {
            endTransaction(mDatabase);
        }
        return success;
    }

    // 根据userId删除识别记录
    public int cleanRecords() {
        int number = getRecordsNum();
        boolean success = false;
        try {
            mDatabase = mDBHelper.getWritableDatabase();
            beginTransaction(mDatabase);

            String where = "1 = 1";
            String[] whereValue = {};

            if (mDatabase.delete(DBHelper.TABLE_RECORDS, where, whereValue) < 0) {
                return 0;
            }

            // 自增恢复为0
            String resoreIdSql = "update sqlite_sequence set seq=0 where name='" + DBHelper.TABLE_RECORDS + "'";
            mDatabase.execSQL(resoreIdSql);

            setTransactionSuccessful(mDatabase);
            success = true;

        } finally {
            endTransaction(mDatabase);
        }
        return number;
    }


    public int getRecordsNum() {
        int number = 0;
        {
            Cursor cursor = null;
            try {
                if (mDBHelper == null) {
                    return 0;
                }
                SQLiteDatabase db = mDBHelper.getReadableDatabase();
                cursor = db.rawQuery("select count(*) from " + DBHelper.TABLE_RECORDS, null);
                if (!cursor.moveToFirst()) {
                    number = 0;
                } else {
                    number = (int) cursor.getLong(0);
                }

            } finally {
                closeCursor(cursor);
            }
            return number;
        }
    }

    /**
     * 远程删除用户
     */
    public boolean userDeleteByName(String userNmae) {
        boolean success = false;
        try {
            mDatabase = mDBHelper.getWritableDatabase();
            beginTransaction(mDatabase);

            if (!TextUtils.isEmpty(userNmae)) {
                String where = "user_name = ? and group_id = ?";
                String[] whereValue = {userNmae, GROUP_ID};

                if (mDatabase.delete(DBHelper.TABLE_USER, where, whereValue) < 0) {
                    return false;
                }

                setTransactionSuccessful(mDatabase);
                success = true;
            }

        } finally {
            endTransaction(mDatabase);
        }
        return success;
    }

    // ---------------------------------------socket相关 end----------------------------------------

}