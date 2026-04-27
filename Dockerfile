FROM maven:3.8-openjdk-8 AS build

# 国内加速：阿里云 Maven 镜像 + Alpine 源
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories 2>/dev/null; true

WORKDIR /build

# 先复制 pom.xml 缓存依赖，避免每次重新下载
COPY pom.xml .
RUN mvn dependency:go-offline -q 2>/dev/null; true

# 复制源码并构建
COPY src/ src/
RUN mvn clean package -DskipTests -q

FROM eclipse-temurin:8-jre-alpine

RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories
RUN apk add --no-cache tzdata && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime

WORKDIR /app

COPY --from=build /build/target/music-search-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
