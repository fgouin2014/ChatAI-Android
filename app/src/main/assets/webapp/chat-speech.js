/**
 * chat-speech.js - Reconnaissance vocale (STT)
 * 
 * ✅ Phase 1 intégrée
 * 
 * Responsabilités :
 * - STT via Whisper Server (prioritaire)
 * - Fallback webkitSpeechRecognition
 * - Gestion VU-meter
 */

(function() {
    'use strict';

    class ChatSpeech {
        constructor(androidInterface, chatUI, chatMessaging) {
            this.androidInterface = androidInterface;
            this.chatUI = chatUI;
            this.chatMessaging = chatMessaging;
            this.isRecording = false;
            this.recognition = null; // webkitSpeechRecognition (fallback)
            this.useWhisper = false;
            this.useGoogleSpeech = false;
        }

        /**
         * Initialise la reconnaissance vocale (Whisper ou Google Speech selon configuration Audio STT)
         */
        initializeSpeech() {
            // ✅ Lire la configuration Audio STT depuis la webapp
            const audioEngine = this.getAudioEngineFromConfig();
            
            if (audioEngine === 'whisper_server') {
                // === WHISPER SERVER ===
                // Vérifier si Whisper est disponible via AndroidApp
                if (this.androidInterface?.isWhisperAvailable?.()) {
                    this.useWhisper = true;
                    this.setupWhisperListener();
                    console.log('✅ Whisper Server disponible - utilisation de Whisper (config Audio STT)');
                } else {
                    console.warn('⚠️ Whisper Server configuré mais non disponible - vérifiez le serveur Whisper');
                    this.useWhisper = false;
                    this.useGoogleSpeech = false;
                }
            } else if (audioEngine === 'legacy_google') {
                // === GOOGLE SPEECH ===
                if (this.androidInterface && android.speech?.SpeechRecognizer?.isRecognitionAvailable) {
                    this.useWhisper = false;
                    this.useGoogleSpeech = true;
                    this.setupWhisperListener(); // Réutilise le même listener pour Google Speech
                    console.log('✅ Google Speech disponible - utilisation de Google Speech (config Audio STT)');
                } else {
                    console.warn('⚠️ Google Speech configuré mais non disponible');
                    this.useWhisper = false;
                    this.useGoogleSpeech = false;
                }
            } else if ('webkitSpeechRecognition' in window) {
                // Fallback : webkitSpeechRecognition (si pas d'Android ou Whisper non configuré)
                this.useWhisper = false;
                this.recognition = new webkitSpeechRecognition();
                this.recognition.continuous = false;
                this.recognition.interimResults = false;
                const language = window.secureChatApp?.language || 'fr';
                this.recognition.lang = language === 'fr' ? 'fr-FR' : 'en-US';
                
                this.recognition.onresult = (event) => {
                    const transcript = event.results[0][0].transcript;
                    const safeTranscript = window.ChatUtils.sanitizeInput(transcript);
                    if (this.chatUI.messageInput) {
                        this.chatUI.messageInput.value = safeTranscript;
                        this.chatUI.adjustTextareaHeight();
                        // Envoyer automatiquement
                        if (window.secureChatApp?.sendMessage) {
                            window.secureChatApp.sendMessage();
                        }
                    }
                };
                
                this.recognition.onend = () => {
                    const voiceBtn = document.getElementById('voiceBtn');
                    if (voiceBtn) voiceBtn.classList.remove('recording');
                    this.isRecording = false;
                };

                this.recognition.onerror = (event) => {
                    console.error('Erreur reconnaissance vocale:', event.error);
                    this.chatUI.showSecureMessage('ai', 'Erreur lors de la reconnaissance vocale.');
                    const voiceBtn = document.getElementById('voiceBtn');
                    if (voiceBtn) voiceBtn.classList.remove('recording');
                    this.isRecording = false;
                };
                
                console.log('✅ webkitSpeechRecognition initialisé (fallback)');
            } else {
                console.warn("Aucun système de reconnaissance vocale disponible");
            }
        }

        /**
         * ✅ NOUVEAU Phase 1 : Configure l'écoute des événements Whisper
         */
        setupWhisperListener() {
            window.onWhisperEvent = (event, data) => {
                switch(event) {
                    case "whisper_ready":
                        console.log("Whisper ready");
                        this.chatUI.updateVUIndicator(true); // Afficher indicateur VU
                        break;
                    case "whisper_speech_start":
                        console.log("Whisper: speech start");
                        const voiceBtn = document.getElementById('voiceBtn');
                        if (voiceBtn) voiceBtn.classList.add('recording');
                        break;
                    case "whisper_rms":
                        // Mettre à jour indicateur VU dans Chat
                        const rmsDb = parseFloat(data);
                        this.chatUI.updateVUIndicatorLevel(rmsDb);
                        break;
                    case "whisper_transcription":
                        // ✅ Transcription reçue → remplir textInput et envoyer
                        const transcript = window.ChatUtils.sanitizeInput(data);
                        if (this.chatUI.messageInput) {
                            this.chatUI.messageInput.value = transcript;
                            this.chatUI.adjustTextareaHeight();
                            // Envoyer automatiquement
                            if (window.secureChatApp?.sendMessage) {
                                window.secureChatApp.sendMessage();
                            }
                        }
                        const voiceBtn2 = document.getElementById('voiceBtn');
                        if (voiceBtn2) voiceBtn2.classList.remove('recording');
                        this.chatUI.updateVUIndicator(false); // Cacher indicateur VU
                        break;
                    case "whisper_error":
                        console.error("Whisper error:", data);
                        this.chatUI.showSecureMessage('ai', 'Erreur reconnaissance vocale: ' + data);
                        const voiceBtn3 = document.getElementById('voiceBtn');
                        if (voiceBtn3) voiceBtn3.classList.remove('recording');
                        this.chatUI.updateVUIndicator(false);
                        break;
                }
            };
        }

        /**
         * Obtenir le moteur Audio STT depuis la configuration
         */
        getAudioEngineFromConfig() {
            // Lire depuis la config de la webapp (configAudioEngine)
            const configAudioEngine = document.getElementById('configAudioEngine');
            if (configAudioEngine && configAudioEngine.value) {
                return configAudioEngine.value;
            }
            // Fallback: vérifier si Whisper est disponible
            if (this.androidInterface?.isWhisperAvailable?.()) {
                return 'whisper_server';
            }
            // Fallback par défaut
            return 'whisper_server';
        }
        
        /**
         * Démarrer la reconnaissance vocale (Whisper ou Google Speech selon configuration Audio STT)
         */
        startVoiceRecognition() {
            const audioEngine = this.getAudioEngineFromConfig();
            
            if (audioEngine === 'whisper_server' && this.androidInterface?.sttStartWhisper) {
                // ✅ Utiliser Whisper Server (selon configuration Audio STT)
                this.androidInterface.sttStartWhisper();
                this.isRecording = true;
            } else if (audioEngine === 'legacy_google' && this.androidInterface?.sttStartWhisper) {
                // ✅ Utiliser Google Speech (selon configuration Audio STT)
                // sttStartWhisper() gère aussi Google Speech selon la configuration
                this.androidInterface.sttStartWhisper();
                this.isRecording = true;
            } else if (this.recognition) {
                // Fallback : webkitSpeechRecognition
                this.recognition.start();
                this.isRecording = true;
            } else {
                console.warn("Aucun système de reconnaissance vocale disponible");
                this.chatUI.showToast('Reconnaissance vocale non disponible');
            }
        }

        /**
         * Arrêter la reconnaissance vocale
         */
        stopVoiceRecognition() {
            const audioEngine = this.getAudioEngineFromConfig();
            
            if ((audioEngine === 'whisper_server' || audioEngine === 'legacy_google') && this.androidInterface?.sttStopWhisper) {
                // Arrêter Whisper ou Google Speech selon la configuration
                this.androidInterface.sttStopWhisper();
            } else if (this.recognition) {
                this.recognition.stop();
            }
            this.isRecording = false;
            const voiceBtn = document.getElementById('voiceBtn');
            if (voiceBtn) voiceBtn.classList.remove('recording');
            this.chatUI.updateVUIndicator(false);
        }

        /**
         * Toggle de l'enregistrement vocal
         */
        toggleVoiceRecording() {
            if (this.isRecording) {
                this.stopVoiceRecognition();
            } else {
                this.startVoiceRecognition();
            }
        }
    }

    // Export global
    window.ChatSpeech = ChatSpeech;
    console.log('✅ ChatSpeech chargé');
})();

