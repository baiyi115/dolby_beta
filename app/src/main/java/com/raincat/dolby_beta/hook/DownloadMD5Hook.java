package com.raincat.dolby_beta.hook;
import com.raincat.dolby_beta.xposed.XposedCompat;
import com.raincat.dolby_beta.xposed.MethodHook;
import static com.raincat.dolby_beta.xposed.XposedCompat.*;

import android.content.Context;

import com.raincat.dolby_beta.helper.ClassHelper;

import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.security.MessageDigest;

public class DownloadMD5Hook {
    public DownloadMD5Hook(Context context) {
        hookMethod(ClassHelper.DownloadTransfer.getCheckMd5Method(context), new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                final Object[] array = (Object[]) param.args[3];
                String path = param.args[0].toString();
                array[5] = fileToMD5(path);
                param.args[3] = array;
            }
        });

        hookMethod(ClassHelper.DownloadTransfer.getCheckDownloadStatusMethod(context), new MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Method[] methods = param.args[0].getClass().getDeclaredMethods();
                for (Method m : methods) {
                    if (m.getReturnType() == long.class) {
                        long length = (long) XposedCompat.callMethod(param.args[0], m.getName());
                        param.setResult(length);
                        break;
                    }
                }
            }
        });
    }

    private String fileToMD5(String filePath) {
        try (InputStream inputStream = new FileInputStream(filePath)) {
            byte[] buffer = new byte[1024];
            MessageDigest digest = MessageDigest.getInstance("MD5");
            int numRead = 0;
            while (numRead != -1) {
                numRead = inputStream.read(buffer);
                if (numRead > 0)
                    digest.update(buffer, 0, numRead);
            }
            byte[] md5Bytes = digest.digest();
            return convertHashToString(md5Bytes);
        } catch (Exception e) {
            return null;
        }
    }

    private String convertHashToString(byte[] hashBytes) {
        StringBuilder returnVal = new StringBuilder();
        for (byte hashByte : hashBytes) {
            returnVal.append(Integer.toString((hashByte & 0xff) + 0x100, 16).substring(1));
        }
        return returnVal.toString().toLowerCase();
    }
}
