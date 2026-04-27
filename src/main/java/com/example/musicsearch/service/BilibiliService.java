package com.example.musicsearch.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BilibiliService {

    private static final String VIEW_API = "https://api.bilibili.com/x/web-interface/view";
    private static final String PLAYURL_API = "https://api.bilibili.com/x/player/playurl";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public JSONObject getVideoInfo(String input, int qn) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            // 先处理短链接和手机端链接，解析出真实URL
            input = resolveShortUrl(input.trim());

            String bvid = extractBvid(input);
            String aid = extractAid(input);

            String url;
            if (bvid != null) {
                url = VIEW_API + "?bvid=" + bvid;
            } else if (aid != null) {
                url = VIEW_API + "?aid=" + aid;
            } else {
                JSONObject err = new JSONObject();
                err.put("code", -1);
                err.put("message", "无法解析输入，请输入 BV号、av号 或视频链接");
                return err;
            }

            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", USER_AGENT);
            request.setHeader("Referer", "https://www.bilibili.com/");

            try (CloseableHttpResponse response = client.execute(request)) {
                String result = EntityUtils.toString(response.getEntity(), "UTF-8");
                JSONObject json = JSON.parseObject(result);
                
                // 提取关键信息
                if (json.getIntValue("code") == 0) {
                    JSONObject data = json.getJSONObject("data");
                    JSONObject resultData = new JSONObject();
                    resultData.put("code", 0);
                    resultData.put("title", data.getString("title"));
                    resultData.put("bvid", data.getString("bvid"));
                    resultData.put("cid", data.getLongValue("cid"));
                    resultData.put("duration", data.getIntValue("duration"));
                    return resultData;
                }
                return json;
            }
        } catch (Exception e) {
            JSONObject err = new JSONObject();
            err.put("code", -1);
            err.put("message", e.getMessage());
            return err;
        }
    }

    public String getDownloadUrl(String bvid, long cid, int qn) {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            String url = PLAYURL_API + "?bvid=" + bvid + "&cid=" + cid + "&qn=" + qn + "&fnval=0";
            HttpGet request = new HttpGet(url);
            request.setHeader("User-Agent", USER_AGENT);
            request.setHeader("Referer", "https://www.bilibili.com/");

            try (CloseableHttpResponse response = client.execute(request)) {
                String result = EntityUtils.toString(response.getEntity(), "UTF-8");
                JSONObject json = JSON.parseObject(result);
                if (json.getIntValue("code") != 0) {
                    return null;
                }
                return json.getJSONObject("data").getJSONArray("durl").getJSONObject(0).getString("url");
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String extractBvid(String input) {
        Pattern p = Pattern.compile("[Bb][Vv]([a-zA-Z0-9]+)");
        Matcher m = p.matcher(input);
        if (m.find()) {
            return "BV" + m.group(1);
        }
        return null;
    }

    private String extractAid(String input) {
        Pattern p = Pattern.compile("av(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(input);
        if (m.find()) {
            return m.group(1);
        }
        if (input.trim().matches("\\d+")) {
            return input.trim();
        }
        return null;
    }

    /**
     * 解析B站短链接(b23.tv)和手机端分享链接
     * 手机分享格式：【标题-哔哩哔哩】 https://b23.tv/xxxxx
     * 跟踪重定向获取真实bilibili.com链接
     */
    private String resolveShortUrl(String input) {
        // 从分享文本中提取URL（手机分享常带标题文字）
        Pattern urlPattern = Pattern.compile("(https?://[\\w\\-./]+)");
        Matcher urlMatcher = urlPattern.matcher(input);
        String url = null;
        while (urlMatcher.find()) {
            String found = urlMatcher.group(1);
            // 优先取b23.tv或bilibili链接
            if (found.contains("b23.tv") || found.contains("bilibili.com")) {
                url = found;
                break;
            }
            if (url == null) url = found;
        }

        // 如果没提取到URL，可能直接就是BV号/av号，原样返回
        if (url == null) return input;

        // 如果不是短链接，直接返回提取到的URL
        if (!url.contains("b23.tv")) return url;

        // 跟踪b23.tv重定向
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();

            int code = conn.getResponseCode();
            if (code == 301 || code == 302) {
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    conn.disconnect();
                    return location;
                }
            }
            conn.disconnect();
        } catch (Exception e) {
            // 解析失败返回原始输入
        }
        return url;
    }

    public File downloadAndAdjustSpeed(String bvid, long cid, int qn, double speed) {
        try {
            // 获取原始下载链接
            String downloadUrl = getDownloadUrl(bvid, cid, qn);
            if (downloadUrl == null) {
                return null;
            }

            // 创建临时目录
            Path tempDir = Files.createTempDirectory("bili-speed-");
            File originalFile = tempDir.resolve("original.mp4").toFile();

            // 下载原始视频
            downloadFile(downloadUrl, originalFile);

            // 复用统一变速方法
            File result = applySpeedToFile(originalFile, speed);

            // 清理原始下载文件
            originalFile.delete();

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 对任意本地视频文件应用变速（视频画面 + 音频同步变速，保持音调）。
     * @param inputFile 输入 mp4/mkv/mov 等 FFmpeg 支持的容器
     * @param speed 速率，范围 0.25 ~ 4.0
     * @return 变速后的 mp4 文件（位于独立临时目录，调用方负责清理父目录）
     */
    public File applySpeedToFile(File inputFile, double speed) {
        try {
            Path tempDir = Files.createTempDirectory("local-speed-");
            File outputDir = tempDir.resolve("output").toFile();
            outputDir.mkdir();

            String outputMp4 = outputDir.getAbsolutePath() + "/speed_" + speed + "x.mp4";
            String videoFilter = "setpts=" + String.format("%.6f", 1.0 / speed) + "*PTS";
            String audioFilter = buildAtempoFilter(speed);

            // 兼容无音轨的视频：先探测
            boolean hasAudio = probeHasAudio(inputFile);

            String ffmpegCmd;
            if (hasAudio) {
                ffmpegCmd = String.format(
                        "ffmpeg -y -i \"%s\" -filter_complex \"[0:v]%s[v];[0:a]%s[a]\" -map \"[v]\" -map \"[a]\" \"%s\"",
                        inputFile.getAbsolutePath(), videoFilter, audioFilter, outputMp4);
            } else {
                ffmpegCmd = String.format(
                        "ffmpeg -y -i \"%s\" -filter:v \"%s\" -an \"%s\"",
                        inputFile.getAbsolutePath(), videoFilter, outputMp4);
            }

            Process process = Runtime.getRuntime().exec(ffmpegCmd);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                StringBuilder error = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) error.append(line).append("\n");
                throw new RuntimeException("FFmpeg 处理失败:\n" + error);
            }

            return new File(outputMp4);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean probeHasAudio(File file) {
        try {
            String cmd = "ffprobe -v error -select_streams a -show_entries stream=codec_type -of csv=p=0 \""
                    + file.getAbsolutePath() + "\"";
            Process p = Runtime.getRuntime().exec(cmd);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            return line != null && line.trim().contains("audio");
        } catch (Exception e) {
            return true; // 探测失败时默认有音轨
        }
    }

    private String buildAtempoFilter(double speed) {
        StringBuilder filter = new StringBuilder();
        double remaining = speed;
        boolean first = true;

        if (speed < 0.5) {
            while (remaining < 0.5) {
                if (!first) filter.append(",");
                filter.append("atempo=0.5");
                remaining *= 2;
                first = false;
            }
            filter.append(",atempo=").append(remaining);
        } else if (speed > 2.0) {
            while (remaining > 2.0) {
                if (!first) filter.append(",");
                filter.append("atempo=2.0");
                remaining /= 2;
                first = false;
            }
            filter.append(",atempo=").append(remaining);
        } else {
            filter.append("atempo=").append(speed);
        }
        return filter.toString();
    }

    private void downloadFile(String url, File dest) throws IOException {
        URL fileUrl = new URL(url);
        URLConnection conn = fileUrl.openConnection();
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Referer", "https://www.bilibili.com/");
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
    }
}
