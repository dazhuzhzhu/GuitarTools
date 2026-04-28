# 🎸 大猪吉他练习助手

专业的吉他练习工具集合，包含视频解析、和弦表、节拍器、调音器四大功能。

## 功能特性

- 📺 **视频解析**：支持 B 站视频解析下载 + 本地视频实时变速播放
- 🎸 **和弦表**：7 大调性级数和弦，49 张真实指法图片
- 🎵 **节拍器**：BPM 20-300，支持多种节奏型（四分/八分/三连/十六分/六连）
- 🎤 **调音器**：基于 YIN 算法，支持 5 种调弦预设，自动/手动选弦

## 一键启动

### Windows
双击 `start.bat` 即可启动，启动后访问：
- 主页：http://localhost:8080
- 独立调音器：http://localhost:8080/tuner

### macOS / Linux
```bash
chmod +x start.sh
./start.sh
```

## 环境要求

- Java 8 或更高版本
- Maven 3.x（或使用 Maven Wrapper）

## 独立调音器

`tuner.html` + `tuner.js` + `tuner.css` 可单独部署到任意静态托管服务（GitHub Pages、Vercel、Netlify），无需后端。

## 部署到云平台

支持一键部署到 Zeabur/Railway/Render 等平台，项目已配置 `$PORT` 环境变量适配。
