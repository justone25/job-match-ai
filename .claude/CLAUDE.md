# JobMatch AI 项目开发规范

> 本文件记录项目开发准则和最佳实践，开发过程中持续更新

---

## 1. 技术文档获取规范

**强制要求**：所有技术组件的使用方式必须通过 Context7 MCP 工具获取最新文档，不依赖模型自有语料库。

```
使用流程：
1. 先调用 mcp__context7__resolve-library-id 获取库 ID
2. 再调用 mcp__context7__get-library-docs 获取文档
3. 根据文档编写代码
```

**适用场景**：
- 引入新依赖时
- 使用不熟悉的 API 时
- 遇到技术问题需要查阅文档时

---

## 2. 需求变更管理

**规则**：开发过程中如果对需求进行了改动，必须同步更新 PRD 文档。

- PRD 文件路径：`../PRD-JobMatch-AI-v3.2.md`
- 更新内容：变更的具体章节 + 变更摘要
- 更新时机：代码实现与 PRD 不一致时立即更新

---

## 3. Phase 完成检查流程

每完成一个 Phase 的开发，必须执行以下流程：

```
1. 单元测试
   - 运行所有单元测试
   - 核心服务覆盖率 ≥ 70%

2. 代码审查
   - 调用 /code-review 对代码进行检测
   - 修复发现的问题

3. 代码提交
   - 调用 /commit-command 提交代码
   - 如果仓库未建立，先通过 gh 命令创建 GitHub 仓库

4. 更新任务文档
   - 更新 DEV-TaskList-JobMatch-AI.md 中的任务状态
   - 记录完成时间和备注
```

---

## 4. 代码规范

### 4.1 Java 代码规范

- 使用 Java 17+ 语法特性
- 类名：PascalCase
- 方法名/变量名：camelCase
- 常量：UPPER_SNAKE_CASE
- 包名：全小写，点分隔

### 4.2 注释规范

- 所有注释使用英文
- 类和公共方法必须有 Javadoc
- 复杂逻辑添加行内注释

### 4.3 异常处理

- 自定义异常继承 `JobMatchException`
- 使用错误码体系（1xxx-5xxx）
- 异常信息包含上下文

---

## 5. 项目结构

```
job-match/
├── .claude/
│   └── CLAUDE.md           # 本文件
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/com/jobmatch/
│   │   │   ├── JobMatchApplication.java
│   │   │   ├── cli/        # CLI 命令层
│   │   │   ├── service/    # 业务逻辑层
│   │   │   ├── llm/        # LLM 集成层
│   │   │   ├── parser/     # 解析器
│   │   │   ├── matcher/    # 匹配引擎
│   │   │   ├── storage/    # 存储层
│   │   │   ├── config/     # 配置管理
│   │   │   ├── model/      # 数据模型
│   │   │   ├── exception/  # 异常定义
│   │   │   └── util/       # 工具类
│   │   └── resources/
│   │       ├── application.yaml
│   │       ├── skills_dictionary.yaml
│   │       └── prompts/
│   └── test/
└── README.md
```

---

## 6. 依赖管理

### 核心依赖

| 依赖 | 用途 | 版本策略 |
|------|------|----------|
| picocli | CLI 框架 | 最新稳定版 |
| jackson | JSON 处理 | 最新稳定版 |
| okhttp | HTTP 客户端 | 最新稳定版 |
| snakeyaml | YAML 配置 | 最新稳定版 |
| slf4j + logback | 日志 | 最新稳定版 |
| lombok | 代码简化 | 最新稳定版 |
| junit5 | 单元测试 | 最新稳定版 |

### 版本查询

引入依赖前，通过 Context7 查询最新版本和使用方式。

---

## 7. 开发经验总结

> 此部分在开发过程中持续补充

### 7.1 Picocli 使用要点

（待补充）

### 7.2 Ollama API 调用要点

（待补充）

### 7.3 JSON Schema 校验要点

（待补充）

---

## 8. 常见问题解决

> 此部分记录开发过程中遇到的问题和解决方案

（待补充）

---

*最后更新：2025-12-12*
