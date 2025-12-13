#!/bin/bash
# JobMatch AI Installation Script
# This script installs JobMatch AI to your local machine

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Installation directories
INSTALL_DIR="${HOME}/.jobmatch"
BIN_DIR="${HOME}/.local/bin"
JAR_NAME="job-match-0.1.0-SNAPSHOT.jar"

echo -e "${GREEN}JobMatch AI Installer${NC}"
echo "======================="
echo

# Check Java version
echo -e "${YELLOW}Checking Java version...${NC}"
if ! command -v java &> /dev/null; then
    echo -e "${RED}Error: Java is not installed.${NC}"
    echo "Please install Java 17 or later and try again."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo -e "${RED}Error: Java 17 or later is required.${NC}"
    echo "Current version: $JAVA_VERSION"
    exit 1
fi
echo -e "${GREEN}Java version OK: $JAVA_VERSION${NC}"
echo

# Create directories
echo -e "${YELLOW}Creating directories...${NC}"
mkdir -p "$INSTALL_DIR"
mkdir -p "$BIN_DIR"
mkdir -p "$INSTALL_DIR/data"
mkdir -p "$INSTALL_DIR/cache"
mkdir -p "$INSTALL_DIR/logs"
echo -e "${GREEN}Directories created.${NC}"
echo

# Copy JAR file
echo -e "${YELLOW}Installing JAR file...${NC}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/target/$JAR_NAME" ]; then
    cp "$SCRIPT_DIR/target/$JAR_NAME" "$INSTALL_DIR/"
    echo -e "${GREEN}JAR file installed.${NC}"
else
    echo -e "${RED}Error: JAR file not found.${NC}"
    echo "Please run 'mvn package -DskipTests' first."
    exit 1
fi
echo

# Create wrapper script
echo -e "${YELLOW}Creating wrapper script...${NC}"
cat > "$BIN_DIR/jobmatch" << 'EOF'
#!/bin/bash
# JobMatch AI wrapper script
INSTALL_DIR="${HOME}/.jobmatch"
JAR_FILE="${INSTALL_DIR}/job-match-0.1.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "Error: JobMatch JAR not found at $JAR_FILE"
    echo "Please run the install script again."
    exit 1
fi

java -jar "$JAR_FILE" "$@"
EOF

chmod +x "$BIN_DIR/jobmatch"
echo -e "${GREEN}Wrapper script created.${NC}"
echo

# Create default config if not exists
if [ ! -f "$INSTALL_DIR/config.yaml" ]; then
    echo -e "${YELLOW}Creating default configuration...${NC}"
    cat > "$INSTALL_DIR/config.yaml" << 'EOF'
# JobMatch AI Configuration
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
EOF
    echo -e "${GREEN}Default configuration created.${NC}"
else
    echo -e "${YELLOW}Configuration file already exists, skipping.${NC}"
fi
echo

# Check if BIN_DIR is in PATH
if [[ ":$PATH:" != *":$BIN_DIR:"* ]]; then
    echo -e "${YELLOW}Note: Please add $BIN_DIR to your PATH:${NC}"
    echo
    echo "  For bash:"
    echo "    echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.bashrc"
    echo "    source ~/.bashrc"
    echo
    echo "  For zsh:"
    echo "    echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> ~/.zshrc"
    echo "    source ~/.zshrc"
    echo
fi

# Check Ollama
echo -e "${YELLOW}Checking Ollama...${NC}"
if command -v ollama &> /dev/null; then
    if curl -s http://localhost:11434/api/tags > /dev/null 2>&1; then
        echo -e "${GREEN}Ollama is running.${NC}"

        # Check if model is installed
        if ollama list | grep -q "qwen2.5:7b"; then
            echo -e "${GREEN}Recommended model (qwen2.5:7b) is installed.${NC}"
        else
            echo -e "${YELLOW}Note: Recommended model not found.${NC}"
            echo "  Run: ollama pull qwen2.5:7b"
        fi
    else
        echo -e "${YELLOW}Ollama is installed but not running.${NC}"
        echo "  Start with: ollama serve"
    fi
else
    echo -e "${YELLOW}Ollama is not installed.${NC}"
    echo "  Install with: brew install ollama"
    echo "  Then: ollama pull qwen2.5:7b"
fi
echo

echo -e "${GREEN}Installation complete!${NC}"
echo
echo "Usage:"
echo "  jobmatch --help              Show help"
echo "  jobmatch analyze             Start analysis (interactive mode)"
echo "  jobmatch analyze -r resume.txt -j jd.txt"
echo
echo "Configuration: $INSTALL_DIR/config.yaml"
echo "Logs: $INSTALL_DIR/logs/"
