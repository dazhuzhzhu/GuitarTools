package com.example.musicsearch.controller;

import com.alibaba.fastjson.JSONObject;
import com.example.musicsearch.service.BilibiliService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.springframework.http.ContentDisposition;

@RestController
@RequestMapping("/api/bilibili")
public class BilibiliController {

    @Autowired
    private BilibiliService bilibiliService;

    @GetMapping("/info")
    public JSONObject getVideoInfo(@RequestParam String url, @RequestParam(defaultValue = "64") int qn) {
        return bilibiliService.getVideoInfo(url, qn);
    }

    @GetMapping("/play")
    public void playVideo(@RequestParam String bvid,
                          @RequestParam long cid,
                          @RequestParam(defaultValue = "64") int qn,
                          HttpServletRequest request,
                          HttpServletResponse response) {
        try {
            String downloadUrl = bilibiliService.getDownloadUrl(bvid, cid, qn);
            if (downloadUrl == null) {
                response.sendError(500, "无法获取播放链接");
                return;
            }

            URL url = new URL(downloadUrl);
            String ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
            String referer = "https://www.bilibili.com/video/" + bvid;

            // 关键：使用 Apache HttpClient 代替 HttpURLConnection，绕过部分防盗链
            org.apache.http.client.HttpClient httpClient = org.apache.http.impl.client.HttpClients.createDefault();
            org.apache.http.client.methods.HttpGet httpGet = new org.apache.http.client.methods.HttpGet(downloadUrl);
            httpGet.setHeader("User-Agent", ua);
            httpGet.setHeader("Referer", referer);
            httpGet.setHeader("Origin", "https://www.bilibili.com");

            String rangeHeader = request.getHeader("Range");
            if (rangeHeader != null) {
                httpGet.setHeader("Range", rangeHeader);
            }

            org.apache.http.HttpResponse httpResponse = httpClient.execute(httpGet);
            int statusCode = httpResponse.getStatusLine().getStatusCode();

            if (statusCode == 403) {
                System.err.println("[B站播放] 403 Forbidden，防盗链拦截");
                response.sendError(403, "视频加载失败：B站防盗链拦截，请尝试下载后本地播放");
                return;
            }

            // 透传 B 站的 Content-Type（不要固定 video/mp4）
            org.apache.http.Header contentType = httpResponse.getFirstHeader("Content-Type");
            if (contentType != null) {
                System.out.println("[B站播放] B站返回 Content-Type: " + contentType.getValue());
                response.setContentType(contentType.getValue());
            } else {
                System.out.println("[B站播放] B站未返回 Content-Type，使用默认 video/mp4");
                response.setContentType("video/mp4");
            }
            response.setHeader("Accept-Ranges", "bytes");

            // 透传 Content-Length 和 Content-Range
            org.apache.http.Header contentLength = httpResponse.getFirstHeader("Content-Length");
            if (contentLength != null) {
                response.setHeader("Content-Length", contentLength.getValue());
            }
            org.apache.http.Header contentRange = httpResponse.getFirstHeader("Content-Range");
            if (contentRange != null) {
                response.setStatus(206);
                response.setHeader("Content-Range", contentRange.getValue());
            }
            
            // 输出调试日志
            System.out.println("[B站播放] HTTP 状态码: " + statusCode);
            System.out.println("[B站播放] Content-Length: " + (contentLength != null ? contentLength.getValue() : "null"));

            // 流式输出
            try (InputStream in = httpResponse.getEntity().getContent();
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[16384];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                    out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("[B站播放] 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @GetMapping("/download")
    public void downloadVideo(@RequestParam String bvid,
                              @RequestParam long cid,
                              @RequestParam(defaultValue = "64") int qn,
                              @RequestParam String filename,
                              HttpServletResponse response) {
        try {
            String downloadUrl = bilibiliService.getDownloadUrl(bvid, cid, qn);
            if (downloadUrl == null) {
                response.sendError(500, "无法获取下载链接");
                return;
            }

            URL url = new URL(downloadUrl);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");

            String contentLength = conn.getHeaderField("Content-Length");
            if (contentLength != null) {
                response.setHeader("Content-Length", contentLength);
            }

            if (!filename.toLowerCase().endsWith(".mp4")) {
                filename = filename + ".mp4";
            }

            response.setContentType("video/mp4");
            ContentDisposition disposition = ContentDisposition.builder("attachment")
                    .filename(filename, StandardCharsets.UTF_8)
                    .build();
            response.setHeader("Content-Disposition", disposition.toString());

            try (InputStream in = conn.getInputStream();
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping("/download-speed")
    public void downloadWithSpeed(@RequestParam String bvid,
                                   @RequestParam long cid,
                                   @RequestParam(defaultValue = "64") int qn,
                                   @RequestParam double speed,
                                   @RequestParam String filename,
                                   HttpServletResponse response) {
        File tempFile = null;
        try {
            // 调用 Service 下载并变速
            tempFile = bilibiliService.downloadAndAdjustSpeed(bvid, cid, qn, speed);
            if (tempFile == null || !tempFile.exists()) {
                response.sendError(500, "变速处理失败");
                return;
            }

            // 设置响应头
            response.setContentType("video/mp4");
            String safeName = filename.replaceFirst("\\.mp4$", "") + "_" + speed + "x.mp4";
            ContentDisposition disposition = ContentDisposition.builder("attachment")
                    .filename(safeName, StandardCharsets.UTF_8)
                    .build();
            response.setHeader("Content-Disposition", disposition.toString());
            response.setHeader("Content-Length", String.valueOf(tempFile.length()));

            // 输出文件
            try (InputStream in = new FileInputStream(tempFile);
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                deleteDirectory(tempFile.getParentFile());
            }
        }
    }

    private void deleteDirectory(File dir) {
        if (dir != null && dir.exists()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory()) {
                        deleteDirectory(child);
                    } else {
                        child.delete();
                    }
                }
            }
            dir.delete();
        }
    }
}
