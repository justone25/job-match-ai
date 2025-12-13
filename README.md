# JobMatch AI

> 智能求职匹配助手 - 让每一次投递决策"可解释、可复盘、可优化"

JobMatch AI 是一款面向中高级技术人才的求职决策助手。通过 LLM 结构化分析简历与职位描述的匹配度，提供可解释的评估结果和可执行的行动建议。

## 核心特性

- **本地优先** - 使用本地 Ollama LLM，数据不出本机
- **可解释分析** - 每项评分都有证据支撑，不输出"黑盒分数"
- **结构化报告** - 硬性门槛检查 → 软性评分 → 差距分析 → 行动建议
- **智能降级** - LLM 不可用时自动切换规则引擎

## 快速安装

```bash
# 1. 确保已安装 Java 17+ 和 Ollama
java -version
ollama --version

# 2. 启动 Ollama 并下载模型
ollama serve &
ollama pull qwen2.5:7b

# 3. 安装 JobMatch
git clone <repo-url> && cd job-match
./install.sh
```

---

## analyze 命令详解

`analyze` 是核心命令，用于分析简历与 JD 的匹配度。支持三种输入模式：

### 用法一：文件模式（推荐）

直接指定简历和 JD 文件路径，支持 **PDF、TXT、MD** 格式：

```bash
# 基本用法（支持 PDF）
jobmatch analyze -r resume.pdf -j job.txt

# 或使用 TXT/MD 文件
jobmatch analyze -r resume.txt -j job.txt

# 完整参数
jobmatch analyze \
  --resume resume.txt \
  --jd job.txt \
  --format markdown \
  --output report.md
```

**示例：**
```bash
# 使用项目自带的示例文件
jobmatch analyze -r examples/sample_resume.txt -j examples/sample_jd.txt

# 保存报告到文件
jobmatch analyze -r my_resume.txt -j target_job.txt -o match_report.md
```

### 用法二：文本模式

直接传入文本内容（适合脚本调用或短文本）：

```bash
# 使用文本参数
jobmatch analyze \
  --resume-text "张三，8年Java开发经验，精通Spring Boot、MySQL..." \
  --jd-text "招聘高级Java工程师，要求5年以上经验..."
```

**脚本调用示例：**
```bash
# 从变量读取
RESUME=$(cat resume.txt)
JD=$(cat job.txt)
jobmatch analyze --resume-text "$RESUME" --jd-text "$JD" -f json
```

### 用法三：交互模式

不带参数启动，进入交互式输入：

```bash
jobmatch analyze
```

交互流程：
```
╔══════════════════════════════════════════════════╗
║     JobMatch AI v0.1.0 - 交互模式              ║
╚══════════════════════════════════════════════════╝

请输入简历内容 (输入空行结束):
──────────────────────────────────────────────────
张三
8年Java开发经验
精通 Spring Boot、MySQL、Redis
...
<空行结束>

请输入职位描述 (输入空行结束):
──────────────────────────────────────────────────
高级Java开发工程师
要求5年以上经验...
<空行结束>

[1/3] 解析简历中...
  ✓ 提取到 12 项技能, 3 段工作经历
[2/3] 解析职位描述中...
  ✓ 提取到 8 项硬性要求, 4 项软性要求
[3/3] 匹配分析中...
  ✓ 分析完成
  ✓ 已保存 (ID: 20231213_143052)
```

### 输出格式选项

使用 `-f` 或 `--format` 指定输出格式：

```bash
# Markdown 格式（默认，适合阅读）
jobmatch analyze -r resume.txt -j job.txt -f markdown

# JSON 格式（适合程序处理）
jobmatch analyze -r resume.txt -j job.txt -f json

# 简洁格式（只显示关键信息）
jobmatch analyze -r resume.txt -j job.txt -f simple
```

### 高级选项

```bash
# 禁用缓存，强制重新分析
jobmatch analyze -r resume.txt -j job.txt --no-cache

# 仅解析，不进行匹配（调试用）
jobmatch analyze -r resume.txt -j job.txt --dry-run

# 组合使用
jobmatch analyze \
  -r resume.txt \
  -j job.txt \
  --format json \
  --output result.json \
  --no-cache
```

### 参数速查表

| 参数 | 短写 | 说明 | 示例 |
|------|------|------|------|
| `--resume` | `-r` | 简历文件路径 (PDF/TXT/MD) | `-r resume.pdf` |
| `--jd` | `-j` | JD 文件路径 (PDF/TXT/MD) | `-j job.txt` |
| `--resume-text` | | 简历文本内容 | `--resume-text "..."` |
| `--jd-text` | | JD 文本内容 | `--jd-text "..."` |
| `--format` | `-f` | 输出格式 | `-f json` |
| `--output` | `-o` | 输出文件路径 | `-o report.md` |
| `--no-cache` | | 禁用缓存 | `--no-cache` |
| `--dry-run` | | 仅解析不匹配 | `--dry-run` |

---

## 输出报告示例

### Markdown 格式

```
╔══════════════════════════════════════════════════════════════╗
║                    匹配分析报告                               ║
╚══════════════════════════════════════════════════════════════╝

📊 总体评分: 78/100 - 良好匹配

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
一句话总结
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
候选人具备扎实的 Java 后端开发能力，技能匹配度高，
但缺乏微服务架构经验，建议针对性补强。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
硬性门槛检查
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  ✓ 学历要求 (本科及以上) - 满足
  ✓ 工作年限 (5年+) - 满足 (8年)
  ✓ Java 技能 - 满足
  ✗ Spring Cloud 经验 - 不满足

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
行动建议
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  1. [技能补强] 学习 Spring Cloud 微服务组件
     - 复习服务注册发现、配置中心、熔断降级

  2. [简历优化] 突出分布式系统相关经验
     - 强调项目中的高并发处理经验
```

### JSON 格式

```json
{
  "summary": {
    "overallScore": 78,
    "matchLevel": "良好匹配",
    "oneLine": "候选人具备扎实的 Java 后端开发能力...",
    "hardGateStatus": "partial_pass"
  },
  "hardGateResults": [
    {"requirement": "本科及以上", "status": "pass"},
    {"requirement": "5年+经验", "status": "pass"},
    {"requirement": "Spring Cloud", "status": "fail"}
  ],
  "actions": [
    {"type": "技能补强", "description": "学习 Spring Cloud..."}
  ]
}
```

---

## 其他命令

### 历史记录

```bash
jobmatch history              # 列出所有记录
jobmatch history view <ID>    # 查看指定报告
jobmatch history delete <ID>  # 删除记录
jobmatch history clear        # 清空历史
```

### 缓存管理

```bash
jobmatch cache        # 查看缓存状态
jobmatch cache clean  # 清理过期缓存
jobmatch cache clear  # 清空所有缓存
```

### 用户反馈

```bash
jobmatch feedback              # 交互式提交反馈
jobmatch feedback -r 1         # 快速评分 (1=有帮助)
jobmatch feedback --stats      # 查看反馈统计
```

### 其他

```bash
jobmatch config       # 查看当前配置
jobmatch dict         # 查看技能词典统计
jobmatch --help       # 帮助信息
jobmatch --version    # 版本信息
```

---

## 配置文件

配置文件位置：`~/.jobmatch/config.yaml`

```yaml
llm:
  provider: local
  local:
    baseUrl: http://localhost:11434
    model: qwen2.5:7b
    timeout: 120

storage:
  dataDir: ~/.jobmatch/data
  cacheDir: ~/.jobmatch/cache
  cacheEnabled: true
  cacheTtlDays: 7
```

---

## 错误处理

| 错误码 | 类型 | 常见原因 | 解决方案 |
|--------|------|----------|----------|
| 1001 | 简历为空 | 未提供简历内容 | 检查文件路径或输入 |
| 1002 | JD为空 | 未提供JD内容 | 检查文件路径或输入 |
| 3001 | LLM连接失败 | Ollama 未启动 | 运行 `ollama serve` |
| 3002 | LLM超时 | 模型响应慢 | 增加 timeout 或换小模型 |

---

## 开发

```bash
mvn compile           # 编译
mvn test              # 运行测试 (111 个测试)
mvn package -DskipTests  # 打包
./install.sh          # 安装到本地
```

## License

MIT
