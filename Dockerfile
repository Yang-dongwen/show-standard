# Show 云版镜像（产品线 B：SaaS + C 端 API + 静态资源）
# 构建前建议先编译前端进 resources，或使用 compose 多阶段。

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY src ./src
# 前端若已 build 进 static / static-saas 则一并打包
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/target/ddmo-1.0.0.jar /app/app.jar
ENV JAVA_OPTS="-Xms256m -Xmx512m"
EXPOSE 8080
# 默认 cloud profile；库连接由 compose 注入
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar --spring.profiles.active=cloud"]
