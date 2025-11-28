#!/usr/bin/env python3
"""
Serveur TTS SpeechT5 (similaire à whisper-server)
Port: 11401
Format: HTTP POST JSON { "text": "..." } → Audio WAV
"""

from flask import Flask, request, jsonify, send_file
from transformers import SpeechT5Processor, SpeechT5ForTextToSpeech
import torch
import io
import soundfile as sf
import sys
import os

app = Flask(__name__)
processor = None
model = None

def load_model():
    global processor, model
    print("[TTS] Chargement modèle SpeechT5...")
    processor = SpeechT5Processor.from_pretrained("microsoft/speecht5_tts")
    model = SpeechT5ForTextToSpeech.from_pretrained("microsoft/speecht5_tts")
    model.eval()
    print("[TTS] Modèle chargé")

@app.route('/synthesize', methods=['POST'])
def synthesize():
    try:
        data = request.json
        text = data.get('text', '')
        
        if not text:
            return jsonify({"error": "Text is required"}), 400
        
        print(f"[TTS] Synthèse: {text[:50]}...")
        
        # Tokeniser
        inputs = processor(text=text, return_tensors="pt")
        
        # Générer audio
        with torch.no_grad():
            speech = model.generate_speech(
                inputs["input_ids"],
                model.speaker_embeddings(torch.tensor([0]))
            )
        
        # Convertir en WAV
        audio_bytes = io.BytesIO()
        sf.write(audio_bytes, speech.numpy(), samplerate=16000, format='WAV')
        audio_bytes.seek(0)
        
        print(f"[TTS] Audio généré: {len(audio_bytes.getvalue())} bytes")
        return send_file(audio_bytes, mimetype='audio/wav')
        
    except Exception as e:
        print(f"[TTS] Erreur: {e}")
        return jsonify({"error": str(e)}), 500

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "ok", "model_loaded": model is not None})

if __name__ == '__main__':
    load_model()
    port = int(os.environ.get('PORT', 11401))
    print(f"[TTS] Serveur démarré sur http://127.0.0.1:{port}")
    app.run(host='127.0.0.1', port=port, debug=False)


