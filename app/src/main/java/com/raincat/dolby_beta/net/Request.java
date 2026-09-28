package com.raincat.dolby_beta.net;

import java.util.HashMap;

class Request {
    String method = "";
    String url = "";
    String param = "";
    HashMap<String, Object> header = new HashMap<>();

    int reTry = 0;
    int timeout = 10000;
}
