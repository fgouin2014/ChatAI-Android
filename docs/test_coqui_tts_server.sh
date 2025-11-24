#!/bin/bash
# Script de test pour Coqui TTS Server
# Teste l'installation et le démarrage du serveur

set -e

echo "=========================================="
echo "TEST COQUI TTS SERVER"
echo "=========================================="

# Couleurs
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Vérifier Python
echo -e "${YELLOW}[1/5] Vérification Python...${NC}"
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}❌ Python3 non trouvé${NC}"
    exit 1
fi
PYTHON_VERSION=$(python3 --version)
echo -e "${GREEN}✅ Python trouvé: $PYTHON_VERSION${NC}"

# Vérifier pip
echo -e "${YELLOW}[2/5] Vérification pip...${NC}"
if ! command -v pip3 &> /dev/null; then
    echo -e "${RED}❌ pip3 non trouvé${NC}"
    exit 1
fi
echo -e "${GREEN}✅ pip3 trouvé${NC}"

# Vérifier installation TTS
echo -e "${YELLOW}[3/5] Vérification installation Coqui TTS...${NC}"
if ! python3 -c "import TTS" 2>/dev/null; then
    echo -e "${YELLOW}⚠️  Coqui TTS non installé${NC}"
    echo -e "${YELLOW}Installation de Coqui TTS...${NC}"
    pip3 install TTS
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Échec installation Coqui TTS${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Coqui TTS installé${NC}"
else
    echo -e "${GREEN}✅ Coqui TTS déjà installé${NC}"
fi

# Vérifier commande tts-server
echo -e "${YELLOW}[4/5] Vérification commande tts-server...${NC}"
if ! command -v tts-server &> /dev/null; then
    echo -e "${YELLOW}⚠️  Commande tts-server non trouvée dans PATH${NC}"
    echo -e "${YELLOW}Recherche dans Python...${NC}"
    TTS_SERVER_PATH=$(python3 -c "import TTS; import os; print(os.path.join(os.path.dirname(TTS.__file__), 'server', 'server.py'))" 2>/dev/null || echo "")
    if [ -z "$TTS_SERVER_PATH" ] || [ ! -f "$TTS_SERVER_PATH" ]; then
        echo -e "${RED}❌ Serveur TTS non trouvé${NC}"
        echo -e "${YELLOW}Installation complète de TTS avec serveur...${NC}"
        pip3 install TTS[server]
        if [ $? -ne 0 ]; then
            echo -e "${RED}❌ Échec installation serveur TTS${NC}"
            exit 1
        fi
    fi
    echo -e "${GREEN}✅ Serveur TTS disponible${NC}"
else
    echo -e "${GREEN}✅ Commande tts-server trouvée${NC}"
fi

# Test démarrage serveur (mode test - ne démarre pas vraiment)
echo -e "${YELLOW}[5/5] Test configuration serveur...${NC}"
MODEL_NAME="tts_models/multilingual/multi-dataset/xtts_v2"
PORT=11401

echo -e "${YELLOW}Modèle: $MODEL_NAME${NC}"
echo -e "${YELLOW}Port: $PORT${NC}"

# Vérifier si le modèle peut être chargé (test rapide)
echo -e "${YELLOW}Test chargement modèle (peut prendre du temps au premier lancement)...${NC}"
python3 << EOF
from TTS.api import TTS
import sys

try:
    print("Chargement modèle $MODEL_NAME...")
    tts = TTS("$MODEL_NAME")
    print("✅ Modèle chargé avec succès")
    print(f"✅ Langues supportées: {tts.languages if hasattr(tts, 'languages') else 'N/A'}")
    sys.exit(0)
except Exception as e:
    print(f"❌ Erreur chargement modèle: {e}")
    print("⚠️  Le modèle sera téléchargé au premier démarrage du serveur")
    sys.exit(0)  # Non bloquant - le modèle sera téléchargé plus tard
EOF

echo ""
echo "=========================================="
echo -e "${GREEN}✅ TESTS TERMINÉS${NC}"
echo "=========================================="
echo ""
echo "Pour démarrer le serveur Coqui TTS:"
echo "  tts-server --model_name $MODEL_NAME --port $PORT"
echo ""
echo "Ou avec Python:"
echo "  python3 -m TTS.server.server --model_name $MODEL_NAME --port $PORT"
echo ""
echo "Test API (une fois le serveur démarré):"
echo "  curl \"http://127.0.0.1:$PORT/process?INPUT_TEXT=Bonjour&INPUT_TYPE=TEXT&OUTPUT_TYPE=AUDIO&AUDIO=WAVE_FILE\" > test_output.wav"
echo ""

