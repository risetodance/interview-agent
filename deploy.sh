#!/bin/bash
# ==============================================================================
# AI Interview Platform - 一键部署脚本
# 使用方法: ./deploy.sh
# 前提条件:
#   1. 服务器已安装 docker 和 nginx
#   2. 配置 SSH Key 免密登录
#   3. 配置 .env 文件（复制自 .env.example）
# 注意: 后端构建在服务器上进行（解决 M 系列芯片镜像兼容性问题）
# ==============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查前置条件
check_prerequisites() {
    log_info "检查前置条件..."

    # 检查 .env 文件
    if [ ! -f ".env" ]; then
        log_error ".env 文件不存在，请先复制 .env.example 为 .env 并配置"
        exit 1
    fi

    # 加载环境变量
    set -a
    source .env
    set +a

    # 检查必要的环境变量
    if [ -z "$SERVER_HOST" ] || [ -z "$SERVER_USER" ]; then
        log_error "SERVER_HOST 和 SERVER_USER 必须配置"
        exit 1
    fi

    if [ -z "$MINIMAX_API_KEY" ]; then
        log_error "MINIMAX_API_KEY 必须配置"
        exit 1
    fi

    log_info "前置条件检查通过"
}

# 构建前端
build_frontend() {
    log_info "构建前端..."

    cd frontend

    # 安装依赖
    pnpm install

    # 构建（使用环境变量注入 API 地址）
    VITE_API_BASE_URL="$API_BASE_URL" pnpm build

    # 打包
    tar -czvf ../deploy/frontend.tar.gz -C dist .

    cd ..

    log_info "前端构建完成"
}

# 构建小程序
build_miniprogram() {
    log_info "构建小程序..."

    cd miniprogram

    # 安装依赖
    pnpm install

    # 微信小程序构建
    pnpm dev:mp-weixin

    # 打包
    tar -czvf ../deploy/miniprogram.tar.gz -C dist .

    cd ..

    log_info "小程序构建完成"
}

# 构建后端 jar 包
build_backend_jar() {
    log_info "构建后端 jar 包..."

    # 使用 Gradle 构建 jar
    ./gradlew :app:bootJar --no-daemon -x test

    log_info "后端 jar 包构建完成"
}



# 打包后端部署文件（jar + Dockerfile + docker-compose.yml）
package_backend_deploy() {
    log_info "打包后端部署文件..."

    mkdir -p deploy/tmp

    # 复制 jar 包
    cp app/build/libs/*.jar deploy/tmp/app.jar

    # 复制 Dockerfile
    cp app/Dockerfile deploy/tmp/

    # 复制 docker-compose.yml
    cp docker-compose.yml deploy/tmp/

    # 复制 .env（包含 API Key 等）
    cp .env deploy/tmp/

    # 打包
    tar -czvf deploy/backend-deploy.tar.gz -C deploy/tmp .
    rm -rf deploy/tmp

    log_info "后端部署文件打包完成"
}

# 创建远程目录
create_remote_dirs() {
    log_info "创建远程目录..."

    ssh "$SERVER_USER@$SERVER_HOST" "mkdir -p \
        $BACKEND_LOG_DIR \
        $FRONTEND_DIST_DIR \
        $MINIPROGRAM_DIST_DIR \
        $BACKEND_DEPLOY_DIR \
        $REDIS_DATA_DIR \
        $REDIS_LOGS_DIR \
        $POSTGRES_DATA_DIR \
        $POSTGRES_LOGS_DIR \
        $RUSTFS_DATA_DIR \
        $RUSTFS_LOGS_DIR"

    log_info "远程目录创建完成"
}

# 部署前端
deploy_frontend() {
    log_info "部署前端..."

    # 删除旧的前端静态资源
    ssh "$SERVER_USER@$SERVER_HOST" "rm -rf $FRONTEND_DIST_DIR/*"

    # 上传新的前端文件
    scp deploy/frontend.tar.gz "$SERVER_USER@$SERVER_HOST:$FRONTEND_DIST_DIR/"

    # 解压
    ssh "$SERVER_USER@$SERVER_HOST" "cd $FRONTEND_DIST_DIR && tar -xzvf frontend.tar.gz && rm frontend.tar.gz"

    log_info "前端部署完成"
}

# 部署小程序
deploy_miniprogram() {
    log_info "部署小程序..."

    # 删除旧的小程序静态资源
    ssh "$SERVER_USER@$SERVER_HOST" "rm -rf $MINIPROGRAM_DIST_DIR/*"

    # 上传新的小程序文件
    scp deploy/miniprogram.tar.gz "$SERVER_USER@$SERVER_HOST:$MINIPROGRAM_DIST_DIR/"

    # 解压
    ssh "$SERVER_USER@$SERVER_HOST" "cd $MINIPROGRAM_DIST_DIR && tar -xzvf miniprogram.tar.gz && rm miniprogram.tar.gz"

    log_info "小程序部署完成"
}

# 部署后端（使用 docker compose）
deploy_backend() {
    log_info "部署后端..."

    # 上传后端部署包
    log_info "上传后端部署包..."
    scp deploy/backend-deploy.tar.gz "$SERVER_USER@$SERVER_HOST:/tmp/"

    # 在服务器上执行构建和部署
    log_info "在服务器上构建并启动 Docker 容器（可能需要较长时间）..."
    ssh "$SERVER_USER@$SERVER_HOST" "
        # 解压部署文件
        mkdir -p $BACKEND_DEPLOY_DIR
        tar -xzvf /tmp/backend-deploy.tar.gz -C $BACKEND_DEPLOY_DIR
        rm /tmp/backend-deploy.tar.gz

        cd $BACKEND_DEPLOY_DIR
        # 使用 docker compose 构建并启动所有容器
        # 第一次部署会启动全部容器，后续部署只重新构建 app
        echo '构建并启动容器...'
        docker compose up -d --build

        # 清理部署目录中的临时文件
        echo '清理临时文件...'
        rm -f $BACKEND_DEPLOY_DIR/app.jar
        rm -f $BACKEND_DEPLOY_DIR/Dockerfile
        rm -f $BACKEND_DEPLOY_DIR/docker-compose.yml
        rm -f $BACKEND_DEPLOY_DIR/.env

        echo '后端部署完成'
    "

    log_info "后端部署完成"
}

# 清理本地临时文件
cleanup() {
    log_info "清理本地临时文件..."

    rm -f deploy/frontend.tar.gz
    rm -f deploy/miniprogram.tar.gz
    rm -f deploy/backend-deploy.tar.gz

    log_info "清理完成"
}

# 显示部署信息
show_summary() {
    echo ""
    echo "========================================"
    echo "          部署完成！"
    echo "========================================"
    echo ""
    echo "前端地址: http://$SERVER_HOST"
    echo "后端API: $API_BASE_URL"
    echo "后端日志: $BACKEND_LOG_DIR"
    echo "后端部署目录: $BACKEND_DEPLOY_DIR"
    echo ""
    echo "查看后端日志:"
    echo "  ssh $SERVER_USER@$SERVER_HOST"
    echo "  docker compose logs -f app"
    echo ""
}

# 主流程
main() {
    log_info "========================================"
    log_info "   AI Interview Platform 部署脚本"
    log_info "========================================"
    echo ""

    check_prerequisites
    create_remote_dirs
    build_frontend
#    build_miniprogram
    build_backend_jar
    package_backend_deploy
    deploy_frontend
#    deploy_miniprogram
    deploy_backend
    cleanup
    show_summary

    log_info "部署成功！"
}

main "$@"
