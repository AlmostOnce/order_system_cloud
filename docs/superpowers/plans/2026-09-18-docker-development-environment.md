# Docker Development Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为当前 Spring Cloud 项目建立不与旧项目冲突的 MySQL、Nacos、Gateway Docker 开发环境。

**Architecture:** Docker Compose 管理 MySQL 和 Nacos 基础设施，Gateway 使用 `app` profile 单独构建和启动。通过命名卷保存基础设施数据，通过环境变量让 Gateway 同时支持 IDEA 本地启动和 Compose 内启动。

**Tech Stack:** Docker Compose、MySQL 8.0、Nacos 3.0.3、Java 21、Spring Boot Maven Plugin。

**Spec:** `docs/superpowers/specs/2026-09-18-docker-development-environment-design.md`

## Global Constraints

- 不删除或修改旧项目的 `ordering-nacos`、`order-sys-mysql-dev`、`order-sys-redis-dev` 容器。
- 本项目宿主机端口使用 MySQL `3307`、Nacos 控制台 `8082`、Nacos API `8850`、Gateway `8083`。
- `.env` 只用于本机，不提交到 Git。
- `docker compose down` 不带 `-v`，保留命名卷数据。
- Nacos 使用单机模式；当前阶段使用内置 Derby，不接入 MySQL。

---

### Task 1: Add environment documentation

**Files:**
- Create: `docs/PROJECT_PROGRESS.md`
- Create: `docs/DOCKER_DEV_ENV.md`
- Modify: `.gitignore`

- [x] **Step 1: Record completed learning milestones and the next milestone in `docs/PROJECT_PROGRESS.md`.**
- [x] **Step 2: Document copy, start, inspect, stop, and data-preserving commands in `docs/DOCKER_DEV_ENV.md`.**
- [x] **Step 3: Ignore `.env`, Compose overrides, Docker logs, and generated build output.**

### Task 2: Add Compose infrastructure

**Files:**
- Create: `.env.example`
- Create: `docker-compose.yml`

- [x] **Step 1: Define MySQL 8.0 with host port `3307`, named data volume, UTF-8 settings, and a health check.**
- [x] **Step 2: Define Nacos 3.0.3 in standalone mode with auth token, server identity, named data/log volumes, and host ports `8082`, `8850`, `9850`, `9851`.**
- [x] **Step 3: Put Gateway in the `app` profile with host port `8083` and the Compose DNS address `nacos:8848`.**

### Task 3: Containerize Gateway

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Modify: `gateway-service/src/main/resources/application.yml`
- Modify: `gateway-service/pom.xml`

- [x] **Step 1: Add a Java 21 multi-stage Maven build and a Java 21 JRE runtime image.**
- [x] **Step 2: Replace the hard-coded Nacos address and password with environment-variable placeholders.**
- [x] **Step 3: Keep one explicit Spring Boot Maven Plugin declaration.**

### Task 4: Verify the environment

**Files:**
- Verify: `docker-compose.yml`
- Verify: Maven project and Docker build inputs

- [x] **Step 1: Run `docker compose config` with a generated local `.env` file.**
- [x] **Step 2: Run Maven package for `gateway-service` with tests skipped.**
- [x] **Step 3: Build the Gateway image and start only the infrastructure services.**
- [x] **Step 4: Confirm container status and record the actual result in `docs/PROJECT_PROGRESS.md`.**
