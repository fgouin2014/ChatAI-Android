#!/bin/bash
# Script pour démarrer le serveur Coqui TTS

set -e

# Couleurs
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${GREEN}=========================================="
echo "DÉMARRAGE SERVEUR COQUI TTS"
echo -e "==========================================${NC}"

# Configuration
MODEL_NAME="tts_models/multilingual/multi-dataset/xtts_v2"
PORT=11401

echo -e "${YELLOW}Modèle: $MODEL_NAME${NC}"
echo -e "${YELLOW}Port: $PORT${NC}"
echo ""

# Vérifier Python
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Python3 non trouvé${NC}"
    exit 1
fi

# Vérifier installation TTS
if ! python3 -c "import TTS" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  Coqui TTS non installé${NC}"
    echo -e "${YELLOW}Installation de Coqui TTS...${NC}"
    pip3 install TTS
fi

# Vérifier version Python (3.9-3.11 requis)
PYTHON_VERSION=$(python3 --version 2>&1 | grep -oP '\d+\.\d+' | head -1)
MAJOR=$(echo $PYTHON_VERSION | cut -d. -f1)
MINOR=$(echo $PYTHON_VERSION | cut -d. -f2)

if [ "$MAJOR" -lt 3 ] || [ "$MAJOR" -eq 3 -a "$MINOR" -lt 9 ] || [ "$MAJOR" -eq 3 -a "$MINOR" -gt 11 ] || [ "$MAJOR" -gt 3 ]; then
    echo -e "${RED}❌ Python $PYTHON_VERSION non compatible${NC}"
    echo -e "${YELLOW}Coqui TTS nécessite Python 3.9-3.11${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Python $PYTHON_VERSION compatible${NC}"
echo ""

# Démarrer serveur
echo -e "${YELLOW}Démarrage du serveur Coqui TTS...${NC}"
echo -e "${YELLOW}Le modèle sera téléchargé au premier démarrage (peut prendre plusieurs minutes)${NC}"
echo ""
echo -e "${GREEN}Serveur accessible sur: http://127.0.0.1:$PORT${NC}"
echo -e "${YELLOW}Appuyez sur Ctrl+C pour arrêter${NC}"
echo ""

# Commande pour démarrer le serveur
if command -v tts-server &> /dev/null; then
    tts-server --model_name "$MODEL_NAME" --port "$PORT"
else
    # Fallback: utiliser Python module
    python3 -m TTS.server.server --model_name "$MODEL_NAME" --port "$PORT"
fi

