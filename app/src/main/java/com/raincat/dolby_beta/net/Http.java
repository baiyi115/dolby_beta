package com.raincat.dolby_beta.net;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import com.raincat.dolby_beta.xposed.XposedCompat;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

public class Http {
    private Request mRequest = new Request();
    // Daemon threads: a non-daemon fixed pool keeps ten threads (and the process) alive for the
    // lifetime of the host app, which is pure overhead for the few calls this class makes.
    private static ExecutorService exec = Executors.newFixedThreadPool(10, runnable -> {
        Thread thread = new Thread(runnable, "dolby-http");
        thread.setDaemon(true);
        return thread;
    });

    public Http(final String method, final String url, final HashMap<String, Object> header, final String param) {
        mRequest.header = header;
        mRequest.method = method;
        mRequest.param = param;
        mRequest.url = url;
    }

    public Http(final String method, final String url, final HashMap<String, Object> param, final HashMap<String, Object> header) {
        StringBuilder stringBuilder = new StringBuilder();
        if (param != null)
            for (Map.Entry entry : param.entrySet()) {
                stringBuilder.append(entry.getKey());
                stringBuilder.append("=");
                stringBuilder.append(Uri.encode(entry.getValue().toString()));
                stringBuilder.append("&");
            }
        if (stringBuilder.length() != 0)
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);

        mRequest.header = header;
        mRequest.method = method;
        mRequest.param = stringBuilder.toString();
        mRequest.url = url;
    }

    public String getResult() {
        return doHttp(mRequest);
    }

    private String doHttp(final Request request) {
        FutureTask<Pair<Integer, String>> future = new FutureTask<>(new Callable<Pair<Integer, String>>() {
            public Pair<Integer, String> call() {
                return post(request);
            }
        });
        exec.execute(future);
        try {
            // Single attempt on purpose: no caller ever configured a retry, and a silent second
            // attempt would only double the wait on an unreachable network. Failures come back as
            // the exception text, which callers validate before parsing.
            return future.get().second;
        } catch (Exception e) {
            XposedCompat.log("Http request failed: " + request.url);
            XposedCompat.log(e);
        }
        return "";
    }

    private static Pair<Integer, String> post(Request request) {
        String result;
        int errorCode = 0;

        HttpURLConnection connection = null;
        InputStream is = null;
        try {
            URL url = new URL(request.url);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(request.method);
            connection.setUseCaches(false);
            connection.setConnectTimeout(request.timeout);
            connection.setReadTimeout(request.timeout);
            connection.setInstanceFollowRedirects(true);
            if (request.method.equals("POST")) {
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setChunkedStreamingMode(0);
            }
            connection.setRequestProperty("Charset", "UTF-8");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setRequestProperty("Cookie", "os=android");

            if (request.header != null)
                for (Map.Entry<String, Object> entry : request.header.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue().toString());
                }
            connection.connect();

            if (request.method.equals("POST") && !TextUtils.isEmpty(request.param)) {
                DataOutputStream out = new DataOutputStream(connection.getOutputStream());
                out.writeBytes(request.param);
                out.flush();
                out.close();
            }

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK)
                is = connection.getInputStream();
            else {
                is = connection.getErrorStream();
                errorCode = connection.getResponseCode();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            result = response.toString();
        } catch (SocketException e) {
            errorCode = 2;
            result = e.getMessage();
            logFailure("socket error", request.url, e);
        } catch (OutOfMemoryError e) {
            errorCode = 3;
            result = e.getMessage();
            logFailure("out of memory", request.url, e);
        } catch (SocketTimeoutException e) {
            errorCode = 4;
            result = e.getMessage();
            logFailure("timeout", request.url, e);
        } catch (Exception e) {
            errorCode = -1;
            result = e.getMessage();
            logFailure("request failed", request.url, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    logFailure("stream close failed", request.url, e);
                }
            }
        }

        return new Pair<>(errorCode, result);
    }

    /** Single funnel for HTTP failures: they used to go to stderr and were invisible in LSPosed. */
    private static void logFailure(String what, String url, Throwable t) {
        XposedCompat.log("Http " + what + ": " + url);
        XposedCompat.log(t);
    }
}
