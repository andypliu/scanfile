package com.example.andy.scanfiles;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

import static android.os.Environment.getExternalStorageDirectory;

/**
 * Created by andy on 12/6/17.
 */

public class RetainedFragment extends Fragment {

    public interface OnDataPass {   // call back interface to pass data back to main activity
        public void onDataPass(String data);
    }

    class ExtensionInfo {  // a helper class to group extension and its frequencies
        private String ext;
        private int count;

        public ExtensionInfo(String ext, int count) {
            this.ext = ext;
            this.count = count;
        }

        public String getExt() {
            return ext;
        }

        public void setExt(String ext) {
            this.ext = ext;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }

    private int MAX_NUMBER_OF_FILE = 10;
    private int MAX_NUMBER_OF_EXT = 5;
    private int NOTIFICATION_ID = 0;

    private int mNumberOfFiles = 0;
    private float mTotalFileSize = 0;
    private boolean mIsScanning;
    private boolean mIsKill;
    private OnDataPass mDataPass;
    private String TAG = RetainedFragment.class.getSimpleName();

    HashMap<String, Integer> mExtensionMap;

    //PriorityQueue is a sorted queue
    PriorityQueue<File> mLargeFiles = new PriorityQueue<File>(MAX_NUMBER_OF_FILE, new Comparator<File>() {
        public int compare(File a, File b) {
            if (a.length() > b.length())
                return 1;
            else if (a.length() == b.length())
                return 0;
            else
                return -1;
        }
    });

    PriorityQueue<ExtensionInfo> mMostFrequentExt = new PriorityQueue<ExtensionInfo>(MAX_NUMBER_OF_EXT, new Comparator<ExtensionInfo>() {
        public int compare(ExtensionInfo a, ExtensionInfo b) {
            if (a.getCount() > b.getCount())
                return 1;
            else if (a == b)
                return 0;
            else
                return -1;
        }
    });

    // this method is only called once for this fragment
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void doScan() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                handleScan();
            }
        }).start();
    }

    public void handleScan() {
        if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
            ///mounted
            addNotification();
            File dir = getExternalStorageDirectory();
            mNumberOfFiles = 0;
            mTotalFileSize = 0;
            mExtensionMap = new HashMap<>();
            mIsKill = false;
            mIsScanning = true;

            if (dir != null) {
                Queue<File> files = new LinkedList<>();
                File[] targetFiles = dir.listFiles();
                if (targetFiles != null) {
                    files.addAll(Arrays.asList(targetFiles));
                    while (!files.isEmpty()) {
                        if (mIsKill) {
                            Log.i(TAG, "User stopped scanning!");
                            return;
                        }
                        File file = files.remove();
                        if (file.isDirectory()) {
                            files.addAll(Arrays.asList(file.listFiles()));
                        } else {   // handle file
                            if (mLargeFiles.size() >= MAX_NUMBER_OF_FILE) {  // when number of file exceeded the max number of file
                                File smallestSizeFile = mLargeFiles.peek();
                                if (file.length() > smallestSizeFile.length()) {  // new file's size is larger than the smallest file size in the list
                                    mLargeFiles.poll();  // remove the smallest file from the queue
                                    mLargeFiles.offer(file);  // add the larger file to the queue
                                }
                            } else {
                                mLargeFiles.offer(file);  // add the file to the queue
                            }

                            mNumberOfFiles++;    // keep counter to perform average file size later
                            mTotalFileSize += file.length();

                            String ext = getExtension(file.getName());

                            if (mExtensionMap.containsKey(ext)) {
                                mExtensionMap.put(ext, mExtensionMap.get(ext) + 1);
                            } else {
                                mExtensionMap.put(ext, 1);
                            }
                            Log.d(TAG, file.getName());
                        }
                    }
                } else {
                    Log.e(TAG, "Not able to get list of files!");
                }
            } else {
                Log.e(TAG, "Not able to get the directory from SD card!");
            }
        } else {
            Log.e(TAG, "SD card not mounted!");
        }

        StringBuilder scanResultSB = new StringBuilder();
        scanResultSB.append("External Storage Report");
        scanResultSB.append("\n\nTop ");
        scanResultSB.append(MAX_NUMBER_OF_FILE);
        scanResultSB.append(" largest files:");

        int i = 1;
        while (mLargeFiles.size() > 0) {
            File file = mLargeFiles.poll();
            scanResultSB.append("\n\nFile ");
            scanResultSB.append(i++);
            scanResultSB.append(": ");
            scanResultSB.append(file.getName());
            scanResultSB.append("\nSize: ");
            scanResultSB.append(getFileSize(file.length()));
        }

        scanResultSB.append("\n\nAverage File Size: ");
        scanResultSB.append(getFileSize((float) (mTotalFileSize / (float)mNumberOfFiles)));

        for (String ext : mExtensionMap.keySet()) {
            if (mMostFrequentExt.size() >= MAX_NUMBER_OF_EXT) {
                ExtensionInfo extensionInfo = mMostFrequentExt.peek();
                Integer newCount = mExtensionMap.get(ext);
                if (newCount >= extensionInfo.getCount()) {     // if the count is greater than the minimum count in the queue
                    mMostFrequentExt.poll();                     // remove the smallest count extension
                    mMostFrequentExt.offer(new ExtensionInfo(ext, newCount));  // add the new larger
                }
            } else {
                mMostFrequentExt.offer(new ExtensionInfo(ext, mExtensionMap.get(ext)));
            }
        }

        scanResultSB.append("\n\nMost Frequent Extension(s): ");
        i = 1;
        while (mMostFrequentExt.size() > 0) {
            ExtensionInfo extensionInfo = mMostFrequentExt.poll();
            scanResultSB.append("\n\nExtension ");
            scanResultSB.append(i++);
            scanResultSB.append(": ");
            scanResultSB.append(extensionInfo.getExt());
            scanResultSB.append("\nCount: ");
            scanResultSB.append(extensionInfo.getCount());
        }

        //  Pass the result back to main activity
        if (mDataPass != null) {
            mDataPass.onDataPass(scanResultSB.toString());
        } else {  // Just in case
            try {
                Thread.sleep(2000);
                if (mDataPass != null) {
                    mDataPass.onDataPass(scanResultSB.toString());
                } else {
                    Log.e(TAG, "There is some issue getting the data pass object!  Will not send the scan result this time!");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // handle post scanning task
        mIsScanning = false;
        cancelNotification(this.getActivity().getApplicationContext(), NOTIFICATION_ID);
    }

    public static String getFileSize(float length) {
        String formattedSize;
        float fileSize;
        fileSize = length;//in Bytes

        if (fileSize < 1024) {
            formattedSize = String.valueOf(fileSize).concat("B");
        } else if (fileSize > 1024 && fileSize < (1024 * 1024)) {
            formattedSize = String.valueOf((Math.round((fileSize / 1024.0 * 100.0) / 100.0))).concat("KB");
        } else {
            formattedSize = String.valueOf((Math.round((fileSize / (1024 * 1204) * 100.0)) / 100.0)).concat("MB");
        }

        return formattedSize;
    }

    public static String getExtension(String fileName) {
        String extension = "NONE";  // Displaying a empty string on screen doesn't look very nice.  So use NONE when the file has no extension
        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
        }
        return extension;
    }

    public boolean isScanning() {
        return mIsScanning;
    }

    public void killScanJob() {
        mIsScanning = false;
        cancelNotification(this.getActivity().getApplicationContext(), NOTIFICATION_ID);
        this.mIsKill = true;
    }

    public void setDataPass(OnDataPass mDataPass) {
        this.mDataPass = mDataPass;
    }

    private void addNotification() {
        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(getActivity().getApplicationContext(), "scan");

        builder.setSmallIcon(android.R.drawable.ic_dialog_info);
        builder.setContentTitle("Scanning External Storage . . .");
        builder.setContentText("Scan all files in external storage to get some statistic");

        Intent notificationIntent = new Intent(this.getActivity().getApplicationContext(), RetainedFragment.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this.getActivity(), 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        // Add as notification
        NotificationManager manager = (NotificationManager) this.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    public void cancelNotification(Context ctx, int notifyId) {
        String ns = Context.NOTIFICATION_SERVICE;
        NotificationManager nMgr = (NotificationManager) ctx.getSystemService(ns);
        nMgr.cancel(notifyId);
    }
}
