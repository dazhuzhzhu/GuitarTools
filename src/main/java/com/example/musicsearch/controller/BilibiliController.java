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

            // 先用GET+Range=bytes=0-0获取总大小（比HEAD更可靠）
            long totalSize = -1;
            try {
                HttpURLConnection probeConn = (HttpURLConnection) url.openConnection();
                probeConn.setRequestMethod("GET");
                probeConn.setRequestProperty("User-Agent", ua);
                probeConn.setRequestProperty("Referer", referer);
                probeConn.setRequestProperty("Origin", "https://www.bilibili.com");
                probeConn.setRequestProperty("Cookie", "CURRENT_FNVAL=4048; b_nut=1704067000; buvid3=XXXXX");
                probeConn.setRequestProperty("Range", "bytes=0-0");
                int probeStatus = probeConn.getResponseCode();
                if (probeStatus == 206) {
                    // Content-Range: bytes 0-0/TOTAL
                    String cr = probeConn.getHeaderField("Content-Range");
                    if (cr != null && cr.contains("/")) {
                        String sizeStr = cr.substring(cr.lastIndexOf('/') + 1).trim();
                        if (!"*".equals(sizeStr)) {
                            totalSize = Long.parseLong(sizeStr);
                        }
                    }
                } else {
                    totalSize = probeConn.getContentLengthLong();
                }
                probeConn.disconnect();
            } catch (Exception e) {
                System.err.println("[B站播放] 探测大小失败: " + e.getMessage());
            }

            // 如果探测失败，再试HEAD
            if (totalSize <= 0) {
                try {
                    HttpURLConnection headConn = (HttpURLConnection) url.openConnection();
                    headConn.setRequestMethod("HEAD");
                    headConn.setRequestProperty("User-Agent", ua);
                    headConn.setRequestProperty("Referer", referer);
                    headConn.setRequestProperty("Origin", "https://www.bilibili.com");
                    totalSize = headConn.getContentLengthLong();
                    headConn.disconnect();
                } catch (Exception e) {
                    System.err.println("[B站播放] HEAD请求失败: " + e.getMessage());
                }
            }

            String rangeHeader = request.getHeader("Range");
            response.setContentType("video/mp4");
            response.setHeader("Accept-Ranges", "bytes");

            if (totalSize <= 0) {
                // 无法得知大小，直接透传全部数据
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", ua);
                conn.setRequestProperty("Referer", referer);
                conn.setRequestProperty("Origin", "https://www.bilibili.com");
                if (rangeHeader != null) {
                    conn.setRequestProperty("Range", rangeHeader);
                }
                // 透传B站的响应状态和头
                int biliStatus = conn.getResponseCode();
                if (biliStatus == 206) {
                    response.setStatus(206);
                    String cr = conn.getHeaderField("Content-Range");
                    if (cr != null) response.setHeader("Content-Range", cr);
                } else if (biliStatus == 403) {
                    System.err.println("[B站播放] 403 Forbidden，防盗链拦截");
                    response.sendError(403, "视频加载失败：B站防盗链拦截，请尝试下载后本地播放");
                    return;
                }
                String cl = conn.getHeaderField("Content-Length");
                if (cl != null) response.setHeader("Content-Length", cl);

                try (InputStream in = conn.getInputStream();
                     OutputStream out = response.getOutputStream()) {
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }
                return;
            }

            // 已知总大小，精确处理Range
            long start = 0, end = totalSize - 1;
            if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
                String rangeVal = rangeHeader.substring(6).trim();
                String[] parts = rangeVal.split("-", 2);
                if (parts[0] != null && !parts[0].isEmpty()) {
                    start = Long.parseLong(parts[0].trim());
                }
                if (parts.length > 1 && parts[1] != null && !parts[1].isEmpty()) {
                    end = Long.parseLong(parts[1].trim());
                }
            }
            if (end >= totalSize) end = totalSize - 1;
            long contentLength = end - start + 1;

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", ua);
            conn.setRequestProperty("Referer", referer);
            conn.setRequestProperty("Origin", "https://www.bilibili.com");
            conn.setRequestProperty("Range", "bytes=" + start + "-" + end);

            response.setHeader("Content-Length", String.valueOf(contentLength));
            if (rangeHeader != null) {
                response.setStatus(206);
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + totalSize);
            }

            try (InputStream in = conn.getInputStream();
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
