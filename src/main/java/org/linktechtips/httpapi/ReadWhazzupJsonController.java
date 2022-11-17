/*
 * Copyright (c) 2022 LinkTechTips
 */

package org.linktechtips.httpapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.linktechtips.httpapi.httpApiManage.whazzupJsonFile;

public class ReadWhazzupJsonController implements HttpHandler {
    private final static Logger LOGGER = LoggerFactory.getLogger(ReadWhazzupJsonController.class);
    @Override
    public void handle(HttpExchange httpExchange) {
        try {
            StringBuilder responseText = new StringBuilder();
            String whazzup = FileUtils.readFileToString(new File(whazzupJsonFile),StandardCharsets.UTF_8);
            responseText.append(whazzup);
            handleResponse(httpExchange, responseText.toString());
        } catch (NullPointerException e) {
            LOGGER.info("[HTTP/ReadWhazzupJson]: Cannot find whazzup file");
            e.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    private void handleResponse(HttpExchange httpExchange, String responsetext) throws Exception {
        //生成html
        byte[] responseContentByte = responsetext.getBytes(StandardCharsets.UTF_8);

        //设置响应头，必须在sendResponseHeaders方法之前设置！
        httpExchange.getResponseHeaders().add("Content-Type:", "application/json;charset=utf-8");

        //设置响应码和响应体长度，必须在getResponseBody方法之前调用！
        httpExchange.sendResponseHeaders(200, responseContentByte.length);

        OutputStream out = httpExchange.getResponseBody();
        out.write(responseContentByte);
        out.flush();
        out.close();
    }
}
