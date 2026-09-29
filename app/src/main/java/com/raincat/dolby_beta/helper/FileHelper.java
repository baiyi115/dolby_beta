package com.raincat.dolby_beta.helper;

import android.content.res.AssetManager;

import com.raincat.dolby_beta.xposed.XposedCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class FileHelper {

    public static boolean deleteDirectory(String filePath) {
        boolean flag;
        if (filePath == null || filePath.length() == 0)
            return false;

        if (!filePath.endsWith(File.separator)) {
            filePath = filePath + File.separator;
        }
        File dirFile = new File(filePath);
        try {
            if (!dirFile.exists() || !dirFile.isDirectory()) {
                return false;
            }
            flag = true;
            File[] files = dirFile.listFiles();

            for (File file : files) {
                if (file.isFile()) {

                    flag = deleteFile(file.getAbsolutePath());
                } else {

                    flag = deleteDirectory(file.getAbsolutePath());
                }
                if (!flag) break;
            }
            if (!flag) return false;
        } catch (Exception e) {
            return false;
        }

        return dirFile.delete();
    }

    static boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.isFile() && file.exists()) {
            return file.delete();
        }
        return false;
    }

    static List<String> readFileFromSD(String path) {
        List<String> list = new ArrayList<>();
        File file = new File(path);
        if (!file.isDirectory()) {
            try {
                InputStream inputStream = new FileInputStream(file);
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    list.add(line);
                }
                inputStream.close();
            } catch (Exception e) {
                XposedCompat.log("FileHelper read failed: " + path);
                XposedCompat.log(e);
            }
        }
        return list;
    }

    static void writeFileFromSD(String path, List<String> content) {
        BufferedWriter out = null;
        try {
            File file = new File(path);
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, false), "utf-8"));
            for (String s : content) {
                out.write(s);
                out.write("\n");
            }
        } catch (Exception e) {
            XposedCompat.log("FileHelper write failed: " + path);
            XposedCompat.log(e);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                XposedCompat.log("FileHelper write close failed");
                XposedCompat.log(e);
            }
        }
    }

    public static void copyFilesAssets(AssetManager assetManager, String oldPath, String codePath) {
        try {
            String[] fileNames = assetManager.list(oldPath);
            if (fileNames.length > 0) {
                File file = new File(codePath);
                file.mkdirs();
                for (String fileName : fileNames) {
                    copyFilesAssets(assetManager, oldPath + File.separator + fileName, codePath + File.separator + fileName);
                }
            } else {
                InputStream is = assetManager.open(oldPath);
                FileOutputStream fos = new FileOutputStream(new File(codePath));
                byte[] buffer = new byte[1024];
                int byteCount = 0;
                while ((byteCount = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, byteCount);
                }
                fos.flush();
                is.close();
                fos.close();
            }
        } catch (Exception e) {
            XposedCompat.log("FileHelper asset copy failed: " + oldPath);
            XposedCompat.log(e);
        }
    }

    public static boolean unzipFile(String zipFileString, String outPathString, String fileParentName, String fileName) {
        try {
            File outPath = new File(outPathString);
            if (!outPath.exists()) {
                outPath.mkdirs();
            }

            ZipFile zipFile = new ZipFile(zipFileString);
            InputStream is;
            Enumeration<? extends ZipEntry> e = zipFile.entries();
            ZipEntry entry;
            while (e.hasMoreElements()) {
                entry = e.nextElement();
                if (entry.getName().contains(fileParentName) && entry.getName().contains(fileName) && !entry.isDirectory()) {
                    is = zipFile.getInputStream(entry);
                    File dstFile = new File(outPathString + "/" + fileName);
                    FileOutputStream fos = new FileOutputStream(dstFile);
                    int len;
                    byte[] buffer = new byte[8192];
                    while ((len = is.read(buffer, 0, buffer.length)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();
                    fos.close();
                    is.close();
                    break;
                }
            }
        } catch (IOException e) {
            XposedCompat.log("FileHelper unzipFile failed: " + zipFileString);
            XposedCompat.log(e);
            return false;
        }
        return true;
    }

    public static boolean unzipFiles(String zipFileString, String outPathString) {
        try {
            File outPath = new File(outPathString);
            if (!outPath.exists()) {
                outPath.mkdirs();
            }

            ZipFile zipFile = new ZipFile(zipFileString);
            Enumeration<? extends ZipEntry> e = zipFile.entries();
            ZipEntry entry;
            String szName = "";
            while (e.hasMoreElements()) {
                entry = e.nextElement();
                if (entry.isDirectory()) {
                    szName = entry.getName();
                    szName = szName.substring(0, szName.length() - 1);
                    File folder = new File(outPathString + File.separator + szName);
                    folder.mkdirs();
                } else {
                    InputStream is = zipFile.getInputStream(entry);
                    File dstFile = new File(outPathString + "/" + entry.getName());
                    FileOutputStream fos = new FileOutputStream(dstFile);
                    int len;
                    byte[] buffer = new byte[8192];
                    while ((len = is.read(buffer, 0, buffer.length)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();
                    fos.close();
                    is.close();
                }
            }
        } catch (IOException e) {
            XposedCompat.log("FileHelper unzipFiles failed: " + zipFileString);
            XposedCompat.log(e);
            return false;
        }
        return true;
    }
}
