# ==============================================================================
# 运行时镜像 (Runtime Stage)
# 作用：使用 JRE 镜像，安装 Python 和 uv 以支持 MCP (uvx 命令)
# 注意：jar 包已在本地上下文目录中提供，直接复制即可
# ==============================================================================
FROM eclipse-temurin:21-jre

# 安装 Python 和 uv（用于 MCP uvx 命令）
RUN sed -i 's|http://archive.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list \
    && sed -i 's|http://security.ubuntu.com|http://mirrors.aliyun.com|g' /etc/apt/sources.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        python3 \
        python3-pip \
        curl \
    && pip3 install uv --break-system-packages -i https://pypi.tuna.tsinghua.edu.cn/simple \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# 声明构建参数（与宿主机环境变量同名）
ARG MINIMAX_API_KEY
ARG MINIMAX_API_HOST

# 将构建参数的值传递给 ENV，使其在容器运行时也生效
ENV MINIMAX_API_KEY=${MINIMAX_API_KEY}
ENV MINIMAX_API_HOST=${MINIMAX_API_HOST}

# 安装 MCP 工具（使用清华镜像源加速）
RUN --mount=type=cache,target=/root/.cache/uv \
    UV_INDEX_URL=https://pypi.tuna.tsinghua.edu.cn/simple \
    uv tool install minimax-coding-plan-mcp
ENV PATH="/root/.local/bin:${PATH}"

WORKDIR /app

# 复制 jar 包（由 deploy.sh 从 app/build/libs/*.jar 复制过来）
COPY app.jar app.jar

# 创建日志目录（运行时挂载）
RUN mkdir -p /app/logs

# 声明容器将暴露的端口
EXPOSE 8080 8081

# 容器启动命令
# 日志输出到 ${APP_LOG_DIR:-/app/logs} 目录
ENTRYPOINT ["sh", "-c", "java -Xmx1g -Xms512m -jar app.jar --logging.file.path=${APP_LOG_DIR:-/app/logs} --logging.file.name=${APP_LOG_DIR:-/app/logs}/app.log"]
