package com.example.musicsearch.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地视频纯前端变速：上传 → 返回 URL → 浏览器 <video> playbackRate 实时变速。
 */
@RestController
@RequestMapping("/api/local")
public class LocalVideoController {

    /** 临时文件注册表：tempId → { file, originalName } */
    private final ConcurrentHashMap<String, Object[]> tempRegistry = new ConcurrentHashMap<>();

    @PostMapping("/upload")
    @ResponseBody
    public String uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) return "{\"error\":\"未上传文件\"}";

        String tempId = UUID.randomUUID().toString().replace("-", "");
        String originalName = file.getOriginalFilename() == null ? "input.mp4" : new File(file.getOriginalFilename()).getName();
        if (!originalName.contains(".")) originalName += ".mp4";

        // 保存文件
        Path uploadDir = Files.createTempDirectory("local-video-");
        File saved = uploadDir.resolve(originalName).toFile();
        file.transferTo(saved);

        // 注册
        tempRegistry.put(tempId, new Object[]{saved, originalName});

        return "{\"tempId\":\"" + tempId + "\"," +
               "\"url\":\"/api/local/file/" + tempId + "\"," +
               "\"filename\":\"" + originalName.replace("\"", "\\\"") + "\"}";
    }

    /**
     * 流式输出文件，支持 Range 请求（浏览器拖拽进度条）。
     */
    @GetMapping("/file/{tempId}")
    public void serveFile(@PathVariable String tempId,
                          HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        Object[] data = tempRegistry.get(tempId);
        if (data == null) { response.sendError(404, "文件已过期"); return; }
        File file = (File) data[0];
        if (!file.exists()) { response.sendError(404, "文件不存在"); return; }

        long fileSize = file.length();
        response.setContentType("video/mp4");
        response.setHeader("Accept-Ranges", "bytes");

        String rangeHeader = request.getHeader("Range");
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String rangeVal = rangeHeader.substring(6).trim();
            String[] parts = rangeVal.split("-", 2);
            long start = 0, end = fileSize - 1;
            if (parts[0] != null && !parts[0].isEmpty()) start = Long.parseLong(parts[0]);
            if (parts.length > 1 && parts[1] != null && !parts[1].isEmpty()) end = Long.parseLong(parts[1]);
            if (start > end || end >= fileSize) { response.sendError(416); return; }

            long contentLength = end - start + 1;
            response.setStatus(206);
            response.setHeader("Content-Length", String.valueOf(contentLength));
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileSize);

            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 OutputStream out = response.getOutputStream()) {
                raf.seek(start);
                byte[] buffer = new byte[8192];
                long remaining = contentLength;
                while (remaining > 0) {
                    int len = raf.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (len < 0) break;
                    out.write(buffer, 0, len);
                    remaining -= len;
                }
            }
        } else {
            response.setHeader("Content-Length", String.valueOf(fileSize));
            try (InputStream in = new FileInputStream(file);
                 OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            }
        }
    }

    /**
     * 释放临时文件。
     */
    @PostMapping("/release/{tempId}")
    @ResponseBody
    public String releaseFile(@PathVariable String tempId) {
        Object[] data = tempRegistry.remove(tempId);
        if (data != null) {
            File file = (File) data[0];
            File dir = file.getParentFile();
            if (file.exists()) file.delete();
            deleteDirectory(dir);
            return "{\"ok\":true}";
        }
        return "{\"ok\":false}";
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteDirectory(child);
                else child.delete();
            }
        }
        dir.delete();
    }
}
