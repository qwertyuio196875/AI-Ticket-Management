# AI 智能工单管理系统 · 阿里云 ECS 部署手册

> 本文档是「AI 智能工单管理系统」（Spring Boot 3 单体 + MySQL 8 + Redis 7 + Vue 3 前端）的阿里云 ECS 部署 runbook，覆盖从开通 ECS 到 HTTPS 上线、日志轮转的完整流程。
>
> **目标读者**：有 Linux 基础、正在做本项目部署收尾的学习者。
> **部署形态**：单体容器（镜像 `ai-ticket-system:1.0.0`）+ 本机 nginx 反向代理 + 本机 Docker 版 MySQL/Redis（或阿里云托管版）。

## 部署架构总览

```
客户端浏览器
   │  HTTPS(443)
   ▼
nginx（ECS 本机）
   │  ├─ SSL 终止（证书）
   │  ├─ 静态托管前端 dist/（Vue 3 + Vite 构建产物）
   │  └─ /api/ 反向代理
   ▼  HTTP(127.0.0.1:8080)
ai-ticket-system:1.0.0 容器（Spring Boot，非 root 用户 app 运行）
   ├─ MySQL 8（本机 Docker 容器 或 阿里云 RDS）
   └─ Redis 7（本机 Docker 容器 或 阿里云 Redis）
```

完整流程：开通 ECS → 配置安全组 → 安装 Docker → 部署 MySQL/Redis → 启动后端容器 → 解析域名并申请 SSL → 配置 nginx → 验证上线 → 配置日志轮转。

---

## 一、ECS 实例规格推荐

**预计耗时**：5 分钟 ｜ **难度**：★☆☆☆☆

### 最低规格：2 vCPU / 4GB 内存 / 40GB SSD

推荐 **2 vCPU、4GB 内存、40GB SSD（通用型 g7/g8i 或经济型均可）**，系统盘容量至少 40GB（系统 + 容器镜像 + 日志留余量）。操作系统推荐 **Ubuntu 22.04 LTS**（本文命令以 Ubuntu 为主、CentOS 为附）。

### 为什么 4GB 够用：内存估算

本系统是「后端 + MySQL + Redis + nginx」同机部署的单体架构，各组件内存估算如下：

| 组件 | 内存占用估算 | 说明 |
|---|---|---|
| JVM（Spring Boot 后端） | 约 1.5 ~ 2GB | 4GB 机器 JVM 默认堆为物理内存的 1/4（约 1GB），加上 Metaspace、线程栈、JIT 与 Docker 层开销，进程整体约 1.5~2GB |
| MySQL 8 | 约 1GB | `innodb_buffer_pool_size` 默认 128MB，但含线程缓存、临时表、连接缓冲等整体约 1GB |
| Redis 7 | 约 0.3GB | 建议容器启动时限制 `--maxmemory 256mb`（缓存工单详情，256MB 足够） |
| nginx | 约 20MB | 可忽略 |
| 操作系统 + 日志 + 余量 | 约 0.7~1GB | 系统进程、nginx 访问日志、应用日志、`docker logs` 落盘 |

合计约 **3.5 ~ 4.3GB**，2 vCPU / 4GB 是**刚好够用**的入门配置，适合学习与演示。

> **升级建议**：如果工单量增长、或 AI 分类/回复并发增加，优先升级内存（8GB）——JVM 堆与 MySQL 缓冲池都受益；CPU 核数对 JVM GC 和查询并发帮助大。带宽选 3~5Mbps 起步即可（静态资源已由 nginx 缓存，后端只走 `/api/`）。

---

## 二、安全组规则

**预计耗时**：5 分钟 ｜ **难度**：★☆☆☆☆

在 ECS 控制台「安全组 → 入方向」放行以下端口，**遵守最小暴露原则**：只放行部署必需的端口，且源地址尽量收窄（推荐先设为 `0.0.0.0/0` 便于调试，上线后收紧）。

| 端口 | 协议 | 源地址 | 用途 |
|---|---|---|---|
| 80 | TCP | 0.0.0.0/0 | HTTP：nginx 监听 80，收到请求后 301 跳转到 HTTPS |
| 443 | TCP | 0.0.0.0/0 | HTTPS：nginx 做 SSL 终止，对外提供加密访问 |
| 8080 | TCP | **默认不建议对公网开放** | 后端 Spring Boot 服务端口。nginx 通过本机 `127.0.0.1:8080` 转发，**不需要公网放行**；仅在调试后端接口时可临时把源地址设为「我的 IP」放行，调试完删除 |

### 最小暴露原则要点

1. **80/443 面向公网**，这是用户访问入口；
2. **8080 不对公网开放**——nginx 与后端同机，走 loopback 即可，端口不暴露给外部就少一层攻击面；
3. 如果你选择了「阿里云 RDS / 云 Redis」托管方案（见第四节），**不要**在 ECS 安全组放行 3306/6379，而是在 RDS / Redis 的**白名单**里只添加 ECS 的内网 IP（同地域内网互通）；
4. 22 端口（SSH）：建议仅限「我的 IP」，并改用密钥登录，避免密码爆破。

---

## 三、Docker 与 Docker Compose 安装

**预计耗时**：10 分钟 ｜ **难度**：★☆☆☆☆

> 容器运行时需要 **Docker Engine + Compose 插件**。以下命令使用**阿里云镜像源**安装（国内直连 Docker 官方源常超时），镜像拉取加速见「配置镜像加速器」。

### 3.1 Ubuntu 22.04 / 20.04（apt）

```bash
# 1. 安装依赖
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# 2. 添加 Docker 官方 GPG 密钥（走阿里云镜像）
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# 3. 添加 docker-ce 软件源（阿里云镜像）
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.aliyun.com/docker-ce/linux/ubuntu $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 4. 安装 Docker Engine + Compose 插件
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# 5. 开机自启并验证
sudo systemctl enable --now docker
sudo docker version          # 验证 Docker 客户端与服务端
sudo docker compose version  # 验证 Compose 插件
```

### 3.2 CentOS 7（yum） / CentOS 8（dnf）

> 说明：CentOS 8 已于 2024 年 EOL（end-of-life），阿里云镜像源可能不再提供更新；生产建议用 Ubuntu 22.04，或改用 Rocky Linux / AlmaLinux。以下命令在两套体系上均可执行，CentOS 7 用 `yum`，CentOS 8 用 `dnf`。

```bash
# CentOS 7：yum
sudo yum install -y yum-utils
sudo yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo sed -i 's+download.docker.com+mirrors.aliyun.com/docker-ce+g' /etc/yum.repos.d/docker-ce.repo
sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker

# CentOS 8 / Rocky 9：dnf
sudo dnf install -y dnf-utils
sudo dnf config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo systemctl enable --now docker
```

### 3.3 配置 Docker 镜像加速器

Docker Hub 在国内访问不稳定，配置 registry-mirror 加速镜像拉取：

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://<你的阿里云加速器地址>.mirror.aliyuncs.com"]
}
EOF
sudo systemctl daemon-reload
sudo systemctl restart docker
```

> **获取专属加速器地址**：登录阿里云控制台 →「容器镜像服务 ACR」→「镜像加速器」，页面会给出形如 `https://xxxx.mirror.aliyuncs.com` 的专属地址，每个账号唯一。配置后可用 `sudo docker pull mysql:8.0` 验证速度。

---

## 四、MySQL 8 与 Redis 7（本机容器 / 阿里云托管 二选一）

**预计耗时**：30 分钟 ｜ **难度**：★★★☆☆

两条路径任选其一，效果等价：

- **路径 A**：ECS 本机用 Docker 部署 MySQL 8 + Redis 7 —— 免费、完整自管，适合学习；
- **路径 B**：阿里云 RDS MySQL + 云数据库 Redis（托管）—— 免运维、有备份/监控，适合正式环境。

> 后端通过环境变量读取连接信息：`MYSQL_URL / MYSQL_USERNAME / MYSQL_PASSWORD / REDIS_HOST / REDIS_PORT / REDIS_PASSWORD`（完整清单见第五节）。

### 路径 A：ECS 本机 Docker 部署

#### 4.1 MySQL 8

```bash
# 数据目录（建议单独挂载，容器重建数据不丢）
sudo mkdir -p /data/mysql

docker run -d --name mysql8 \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD='<请换成强密码>' \
  -e MYSQL_DATABASE=ai_ticket_system \
  -e TZ=Asia/Shanghai \
  -v /data/mysql:/var/lib/mysql \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

要点：

- `MYSQL_DATABASE=ai_ticket_system` 会自动创建业务库，**建表由应用启动时的 `spring.sql.init` 自动完成**（含种子数据 admin/admin123），无需手工执行 SQL；
- 字符集强制 `utf8mb4`，与后端连接串参数 `characterEncoding=utf8` 对齐，避免中文乱码；
- 容器重建后数据仍在 `/data/mysql`。

#### 4.2 Redis 7

```bash
# 数据目录（AOF 持久化）
sudo mkdir -p /data/redis

docker run -d --name redis7 \
  -p 6379:6379 \
  -e TZ=Asia/Shanghai \
  -v /data/redis:/data \
  redis:7-alpine \
  redis-server --requirepass '<请换成强密码>' --appendonly yes --maxmemory 256mb
```

要点：

- `--requirepass` 设置密码（对应后端 `REDIS_PASSWORD`）；`--appendonly yes` 开启 AOF 持久化；
- `--maxmemory 256mb` 限制内存上限（Redis 用于工单详情缓存，TTL 30 分钟 ± 5 分钟随机，256MB 足够）；
- 无密码场景可省略该参数，后端 `REDIS_PASSWORD` 留空即可。

#### 4.3 后端容器如何连到本机的 MySQL / Redis

后端容器与 MySQL/Redis 容器同宿主机：容器内访问宿主机的默认网关地址 `172.17.0.1`。因此第五节启动命令中 `MYSQL_URL` 的主机名、`REDIS_HOST` 都填 **`172.17.0.1`**（若 nginx 与后端容器同宿主，nginx 代理 `127.0.0.1:8080` 即可，因为后端容器端口已 `-p 8080:8080` 映射到宿主机）。

### 路径 B：阿里云托管（RDS MySQL + 云数据库 Redis）

**适合**：希望免运维、自带备份与监控，或数据量大时的正式部署。

#### 4.4 云数据库 RDS MySQL 版

1. 控制台搜索「云数据库 RDS MySQL」→ 创建实例：**基础版（单节点）2 核 4GB** 足够，地域选与 ECS **同一地域**（走内网，免公网流量费）；
2. 实例创建后：设置**白名单**，添加 ECS 的**内网 IP**（ECS 详情页「本实例内网 IP」）；
3. 创建**高权限账号**与密码（对应后端 `MYSQL_USERNAME / MYSQL_PASSWORD`）；
4. 创建**数据库** `ai_ticket_system`（字符集选 `utf8mb4`，排序规则 `utf8mb4_unicode_ci`）；
5. 从实例「连接信息」拿到**内网地址**，填到后端 `MYSQL_URL`（形如 `jdbc:mysql://rm-xxx.mysql.rds.aliyuncs.com:3306/ai_ticket_system?...`）。

> RDS 默认开启强制 SSL 时，连接串中的 `useSSL=false` 会被拒绝，需在 RDS 参数组关闭「强制 SSL」或改用 SSL 连接串——连接失败时优先排查此项。

#### 4.5 云数据库 Redis 版（Tair）

1. 控制台搜索「云数据库 Redis」→ 创建社区版实例（容量 1GB 即可），地域同样选与 ECS 同地域；
2. 设置**白名单**，添加 ECS 内网 IP；
3. 从实例「连接信息」拿到**内网地址与端口**（默认 6379），设置访问密码；
4. 对应后端环境变量：`REDIS_HOST=<内网地址>`、`REDIS_PORT=6379`、`REDIS_PASSWORD=<密码>`。

---

## 五、后端容器启动与环境变量

**预计耗时**：10 分钟 ｜ **难度**：★★☆☆☆

### 5.1 环境变量清单

在 ECS 上执行以下命令构建镜像（仓库根目录存在 Dockerfile，容器内以非 root 用户 `app` 运行，暴露端口 8080）：

```bash
# 在仓库根目录执行
sudo docker build -t ai-ticket-system:1.0.0 .
```

| 环境变量 | 是否必填 | 说明 |
|---|---|---|
| `MYSQL_URL` | 必填 | JDBC 连接串。本机容器用 `jdbc:mysql://172.17.0.1:3306/ai_ticket_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false`；RDS 则把主机名换成内网地址 |
| `MYSQL_USERNAME` | 必填 | MySQL 账号（本机部署为 root；RDS 为高权限账号） |
| `MYSQL_PASSWORD` | 必填 | MySQL 密码 |
| `REDIS_HOST` | 必填 | Redis 地址（本机部署为 `172.17.0.1`；托管版为内网地址） |
| `REDIS_PORT` | 选填 | Redis 端口，默认 `6379` |
| `REDIS_PASSWORD` | 选填 | Redis 密码，无密码留空 |
| `JWT_SECRET` | **必填** | JWT 签名密钥，HMAC-SHA256 要求 **≥ 32 字节**，生产环境**必须**覆盖默认值（默认值仅用于本地开发）。可用 `openssl rand -base64 48` 生成 |
| `DEEPSEEK_API_KEY` | **必填** | DeepSeek API Key。Spring AI 配置为 fail-fast，**不设置则应用启动失败**；配到环境变量，不要写进配置文件 |
| `ALIYUN_OSS_ENABLED` | 选填 | 默认 `false`：附件走**本地文件存储降级**（无需任何 OSS 配置）；设为 `true` 需再配置 OSS 相关环境变量（本 runbook 不展开，见 `ticket-web` 的 `application.yml` 注释） |

> **口令生成**：`openssl rand -base64 48` 生成 64 字符的 JWT_SECRET（远超 32 字节要求）；DeepSeek API Key 在 `https://platform.deepseek.com` 注册获取。

### 5.2 启动容器

```bash
sudo docker run -d --name ai-ticket \
  -p 8080:8080 \
  -e MYSQL_URL='jdbc:mysql://172.17.0.1:3306/ai_ticket_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
  -e MYSQL_USERNAME=root \
  -e MYSQL_PASSWORD='<MySQL密码>' \
  -e REDIS_HOST=172.17.0.1 \
  -e REDIS_PORT=6379 \
  -e REDIS_PASSWORD='<Redis密码>' \
  -e JWT_SECRET='<openssl rand -base64 48 生成>' \
  -e DEEPSEEK_API_KEY='sk-<你的DeepSeek Key>' \
  -e ALIYUN_OSS_ENABLED=false \
  ai-ticket-system:1.0.0
```

> 若采用路径 B（RDS/托管 Redis），把 `MYSQL_URL` 主机名、`REDIS_HOST` 换成内网地址即可，命令其余部分不变。

### 5.3 健康检查

```bash
# 查看启动日志（首次启动自动建表 + 灌种子数据，稍等数秒）
sudo docker logs -f ai-ticket

# 容器内直接探测
sudo docker exec ai-ticket curl -s http://127.0.0.1:8080/api/v1/ping
# 期望返回：{"code":"200","message":"success","data":"pong"}
```

`GET /api/v1/ping` 是匿名健康检查端点，返回 `{"code":"200","message":"success","data":"pong"}` 即代表后端就绪。

> **安全提醒**：应用启动时 `spring.sql.init` 自动建表并写入种子数据，初始管理员为 `admin / admin123`，**首次登录后必须立即改密**。

---

## 六、域名与 SSL 证书

**预计耗时**：30~60 分钟（含域名解析生效等待）｜ **难度**：★★☆☆☆

### 6.1 解析域名到 ECS

1. 在阿里云「域名控制台」找到你的域名（未购买可在阿里云万网购买，如 `ticket.example.com`）；
2. 进入「解析设置」→ 添加记录：
   - 记录类型：`A`；主机记录：`ticket`（得到 `ticket.example.com`）；记录值：**ECS 公网 IP**；TTL 默认 10 分钟；
3. 等待解析生效，可用 `ping ticket.example.com` 或 `nslookup ticket.example.com` 确认解析到 ECS IP。

### 6.2 申请阿里云免费证书

1. 控制台搜索「数字证书管理服务」（原 SSL 证书）→「证书管理」→「申请免费证书」；
2. 证书类型选 **DigiCert 免费版 DV（单域名）**，有效期 **3 个月**，每个自然年可免费申请 20 张（20 张额度内每年可重新申请实现近乎全年免费）；
3. 填写证书绑定域名：`ticket.example.com`，验证方式选 **DNS 验证**（阿里云 DNS 可一键自动添加验证记录，通常几分钟内签发）；
4. 签发成功后，在「证书列表 → 下载」选择 **Nginx** 类型，得到两个文件：`ticket.example.com.pem`（证书链）和 `ticket.example.com.key`（私钥）。

### 6.3 上传证书到 ECS

```bash
# 在本地电脑执行（或用阿里云控制台「远程文件管理」直接上传）
scp ticket.example.com.pem ticket.example.com.key root@<ECS公网IP>:/tmp/

# 在 ECS 上执行
sudo mkdir -p /etc/nginx/ssl
sudo mv /tmp/ticket.example.com.pem /etc/nginx/ssl/
sudo mv /tmp/ticket.example.com.key /etc/nginx/ssl/
sudo chmod 600 /etc/nginx/ssl/ticket.example.com.key
```

### 6.4 到期续期提醒

- 免费证书有效期 **3 个月**。阿里云会在证书**到期前 30 天**通过控制台消息、短信、邮件提醒；
- 续期方式：到期前在「数字证书管理服务」重新申请一张免费证书（消耗当年 20 张额度之一），验证签发后**重新下载并替换** ECS 上的 `.pem` / `.key`，再 `sudo nginx -s reload`；
- 可在证书列表为该证书开启「自动续期」功能（前提：当年仍有免费额度，且域名在阿里云 DNS 托管），签发后 nginx 侧替换文件仍需手动或脚本完成——**建议把续期日写入自己的日历，用提醒兜底**。

---

## 七、nginx 反向代理配置

**预计耗时**：15 分钟 ｜ **难度**：★★☆☆☆

### 7.1 安装 nginx

```bash
# Ubuntu
sudo apt-get update && sudo apt-get install -y nginx

# CentOS 7/8
sudo yum install -y nginx   # 或 dnf
sudo systemctl enable --now nginx
```

### 7.2 前端构建产物（dist/）

在开发机 `ticket-ui` 目录执行 `npm install` 与 `npm run build`，将生成的 `dist/` 目录上传到 ECS 的 `/opt/ai-ticket/dist`：

```bash
scp -r dist/ root@<ECS公网IP>:/tmp/
# ECS 上执行
sudo mkdir -p /opt/ai-ticket
sudo mv /tmp/dist /opt/ai-ticket/dist
```

> **注意**：前端 axios 的 API 基址需配置为 `/api`（与下方 nginx 代理前缀一致），在构建前于前端环境配置文件里确认，避免上线后请求 404。

### 7.3 完整 nginx 配置

创建 `/etc/nginx/conf.d/ai-ticket.conf`（若 Ubuntu 默认站点 `/etc/nginx/sites-enabled/default` 存在，先删除或注释以免 80 端口冲突）：

```nginx
# ============================================================
# AI 智能工单管理系统 —— nginx 配置
# 域名：ticket.example.com（请替换为你的真实域名）
# 后端：Spring Boot，本机 127.0.0.1:8080（容器已 -p 8080:8080 映射）
# 前端：Vue 3 构建产物，位于 /opt/ai-ticket/dist
# SSL 证书：阿里云免费证书，有效期 3 个月，到期需替换并 reload
# ============================================================

# ---------- HTTP 80：仅做跳转 HTTPS ----------
server {
    listen 80;
    server_name ticket.example.com;

    location / {
        return 301 https://$host$request_uri;
    }
}

# ---------- HTTPS 443：SSL 终止 + 前端静态托管 + 后端反向代理 ----------
server {
    listen 443 ssl http2;          # nginx 1.25 及以上也可写 http2 on;
    server_name ticket.example.com;

    # ---- SSL 证书（第六节上传，路径保持一致）----
    ssl_certificate     /etc/nginx/ssl/ticket.example.com.pem;
    ssl_certificate_key /etc/nginx/ssl/ticket.example.com.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;
    ssl_session_timeout 10m;

    # 上传工单附件的大小上限（按需调整）
    client_max_body_size 20m;

    # ---- 后端反向代理：/api/ 全部转发到本机 Spring Boot 8080 ----
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;

        # 传递真实 IP / 协议头，后端可据此记录调用者与 scheme
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # 超时兜底：AI 智能回复最长等待 30s，此处放宽到 60s
        proxy_connect_timeout 10s;
        proxy_read_timeout    60s;
        proxy_send_timeout    60s;
    }

    # ---- 前端静态资源：nginx 直接托管 dist/ ----
    root  /opt/ai-ticket/dist;
    index index.html;

    # SPA 路由回退：非真实文件的路径都交给 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 带 hash 的构建产物可以长缓存
    location /assets/ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }
}
```

### 7.4 校验并生效

```bash
sudo nginx -t                              # 语法检查，输出 syntax is ok 即通过
sudo systemctl reload nginx

# 验证 HTTPS 与健康检查（期望返回 {"code":"200","message":"success","data":"pong"}）
curl -s https://ticket.example.com/api/v1/ping

# 浏览器打开 https://ticket.example.com 应能打开前端登录页，
# 用 admin / admin123 登录（首次登录后必须立即改密）。
```

---

## 八、日志轮转

**预计耗时**：10 分钟 ｜ **难度**：★☆☆☆☆

长期运行后 nginx 访问日志与后端日志都会持续增长，需配置 `logrotate` 自动轮转、压缩与清理，避免占满磁盘。

### 8.1 nginx 日志轮转（自带配置）

nginx 安装包已自带 `/etc/logrotate.d/nginx`，内容大致如下（已满足日常需求，无需修改）：

```text
/var/log/nginx/*.log {
    daily                    # 每天轮转一次
    missingok
    rotate 14                # 保留 14 份
    compress                 # 轮转后 gzip 压缩
    delaycompress
    notifempty               # 空文件不轮转
    create 0640 nginx adm
    sharedscripts
    postrotate
        [ -f /var/run/nginx.pid ] && kill -USR1 `cat /var/run/nginx.pid`
    endscript                # 通知 nginx 重新打开日志文件
}
```

确认生效：`sudo logrotate -d /etc/logrotate.d/nginx`（debug 模式，只打印计划不实际执行）。

### 8.2 后端（应用）日志轮转 —— 推荐方案：限制 Docker 日志大小

后端跑在容器里，应用日志输出到 stdout，由 Docker 的 `json-file` 日志驱动收集。**最省事的方案是在启动容器时直接限制日志文件大小与数量**，无需 logrotate：

```bash
sudo docker run -d --name ai-ticket \
  ...
  --log-driver json-file \
  --log-opt max-size=50m \   # 单个日志文件上限 50MB
  --log-opt max-file=3       # 最多保留 3 个（即最近 150MB）
  ai-ticket-system:1.0.0
```

- 查看实时日志：`sudo docker logs -f ai-ticket`；
- 旧日志滚动清理由 Docker 自动完成，磁盘占用可控。

### 8.3 方案 B：应用日志挂载到宿主机后用 logrotate

如果应用把日志写到了容器内文件（如 `/app/logs`）并挂载到宿主机，则用 logrotate 处理。应用进程会一直持有文件句柄，**必须用 `copytruncate`**（先复制再清空，应用无需重启）：

```text
# /etc/logrotate.d/ai-ticket
/data/ai-ticket/logs/*.log {
    daily
    rotate 14
    compress
    copytruncate            # 应用持续写句柄，必须 copytruncate
    missingok
    notifempty
    dateext
}
```

```bash
# 验证配置（debug 模式）
sudo logrotate -d /etc/logrotate.d/ai-ticket
# 强制轮转一次
sudo logrotate -f /etc/logrotate.d/ai-ticket
```

### 8.4 常用运维命令速查

```bash
sudo logrotate -d /etc/logrotate.d/nginx      # 调试（不实际执行）
sudo logrotate -f /etc/logrotate.d/nginx      # 强制轮转
sudo du -sh /var/log/nginx /var/lib/docker/containers   # 查看日志/容器磁盘占用
```

---

## 附录：上线验收 Checklist

- [ ] ECS：2 vCPU / 4GB，安全组仅放行 80/443（8080 不对公网开放）
- [ ] Docker + Compose 已安装，镜像加速器已配置
- [ ] MySQL 8 / Redis 7 已就绪（本机容器或阿里云托管），白名单正确
- [ ] 后端容器启动，`curl http://127.0.0.1:8080/api/v1/ping` 返回 `{"code":"200","message":"success","data":"pong"}`
- [ ] 域名 A 记录已解析到 ECS 公网 IP
- [ ] 免费 SSL 证书已上传并配置，`https://ticket.example.com/api/v1/ping` 返回 pong，浏览器锁图标正常
- [ ] HTTP 80 自动 301 跳转 HTTPS
- [ ] `admin / admin123` 可登录，**已立即改密**
- [ ] logrotate 配置校验通过，`docker logs` 大小已限制
- [ ] 服务器重启后 `systemctl`（docker/nginx）与容器 `restart` 策略自愈（`docker update --restart unless-stopped ai-ticket` 可补设）
