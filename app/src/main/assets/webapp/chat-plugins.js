/**
 * ChatAI - Plugin Functions Module
 * Extracted from index.html for better maintainability
 * 
 * Contains:
 * - speakText() - TTS functionality
 * - openPlugin() / closePlugin() - Plugin modal management
 * - Calculator functions
 * - Web search functionality
 * - Weather functionality
 * - Jokes functionality
 * - Tips functionality
 * - Server status functions
 */

// ============================================================
// TEXT-TO-SPEECH
// ============================================================

function speakText(button) {
    if ('speechSynthesis' in window && window.secureChatApp) {
        const messageText = button.closest('.message-bubble').textContent.replace(/[]/g, '').trim();
        window.secureChatApp.speakText(messageText);
    }
}

// ============================================================
// PLUGIN MODAL MANAGEMENT
// ============================================================

const PLUGIN_DEFINITIONS = {
    websearch: {
        title: 'Recherche Web',
        content: `<div style="text-align: center;">
            <input type="text" id="searchQuery" placeholder="Bitcoin, meteo, actualites..." style="width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 8px; margin-bottom: 15px; font-size: 14px;">
            <button onclick="performWebSearch()" style="width: 100%; padding: 12px; background: linear-gradient(135deg, #667eea, #764ba2); color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer;">Rechercher sur le web</button>
            <div id="searchResults" style="background: #f8f9fa; padding: 15px; border-radius: 8px; display: none; margin-top: 10px; max-height: 300px; overflow-y: auto; text-align: left; font-size: 13px; line-height: 1.6;"></div>
        </div>`
    },
    calculator: {
        title: 'Calculette',
        content: `<div style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px;">
            <input type="text" id="calcDisplay" readonly style="grid-column: span 4; padding: 15px; font-size: 18px; text-align: right; border: 1px solid #ddd; border-radius: 8px; background: #f8f9fa;">
            <button onclick="clearCalc()" style="grid-column: span 2; padding: 15px; background: #ff4757; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer;">C</button>
            <button onclick="deleteLast()" style="grid-column: span 2; padding: 15px; background: #ffa502; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer;">DEL</button>
            <button onclick="addToCalc('7')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">7</button>
            <button onclick="addToCalc('8')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">8</button>
            <button onclick="addToCalc('9')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">9</button>
            <button onclick="addToCalc('/')" style="padding: 15px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">/</button>
            <button onclick="addToCalc('4')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">4</button>
            <button onclick="addToCalc('5')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">5</button>
            <button onclick="addToCalc('6')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">6</button>
            <button onclick="addToCalc('*')" style="padding: 15px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">*</button>
            <button onclick="addToCalc('1')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">1</button>
            <button onclick="addToCalc('2')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">2</button>
            <button onclick="addToCalc('3')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">3</button>
            <button onclick="addToCalc('-')" style="padding: 15px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">-</button>
            <button onclick="addToCalc('0')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">0</button>
            <button onclick="addToCalc('.')" style="padding: 15px; background: #f1f2f6; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">.</button>
            <button onclick="calculate()" style="padding: 15px; background: #4ECDC4; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 16px; font-weight: bold;">=</button>
            <button onclick="addToCalc('+')" style="padding: 15px; background: #667eea; color: white; border: none; border-radius: 8px; cursor: pointer; font-size: 16px;">+</button>
        </div>`
    },
    weather: {
        title: 'Meteo',
        content: `<div style="text-align: center;">
            <input type="text" id="cityInput" placeholder="Entrez une ville..." style="width: 100%; padding: 12px; border: 1px solid #ddd; border-radius: 8px; margin-bottom: 15px; font-size: 14px;">
            <button onclick="getWeather()" style="width: 100%; padding: 12px; background: linear-gradient(135deg, #4ECDC4, #44A08D); color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer;">Obtenir la meteo</button>
            <div id="weatherResult" style="background: #f8f9fa; padding: 15px; border-radius: 8px; display: none; margin-top: 10px;"></div>
        </div>`
    },
    jokes: {
        title: 'Generateur de Blagues',
        content: `<div style="text-align: center;">
            <button onclick="getRandomJoke()" style="width: 100%; padding: 15px; background: linear-gradient(135deg, #ff6b6b, #ee5a6f); color: white; border: none; border-radius: 8px; margin-bottom: 15px; font-weight: bold; cursor: pointer;">Blague aleatoire</button>
            <div id="jokeResult" style="background: #f8f9fa; padding: 20px; border-radius: 8px; min-height: 80px; display: flex; align-items: center; justify-content: center; font-style: italic; color: #666; line-height: 1.6;">Cliquez pour une blague !</div>
        </div>`
    },
    tips: {
        title: 'Conseils du Jour',
        content: `<div style="text-align: center;">
            <div style="display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 15px;">
                <button onclick="getTip('productivity')" style="padding: 12px; background: #4ECDC4; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer;">Productivite</button>
                <button onclick="getTip('health')" style="padding: 12px; background: #ff6b6b; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer;">Sante</button>
                <button onclick="getTip('tech')" style="padding: 12px; background: #667eea; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer;">Tech</button>
                <button onclick="getTip('lifestyle')" style="padding: 12px; background: #ffa502; color: white; border: none; border-radius: 8px; font-weight: bold; cursor: pointer;">Lifestyle</button>
            </div>
            <div id="tipResult" style="background: #f8f9fa; padding: 20px; border-radius: 8px; min-height: 100px; display: flex; align-items: center; justify-content: center; font-style: italic; color: #666; line-height: 1.6;">Choisissez une categorie !</div>
        </div>`
    }
};

function openPlugin(pluginType) {
    const modal = document.getElementById('pluginModal');
    const title = document.getElementById('modalTitle');
    const content = document.getElementById('modalContent');
    
    const plugin = PLUGIN_DEFINITIONS[pluginType];
    if (plugin) {
        title.textContent = plugin.title;
        content.innerHTML = plugin.content;
        modal.style.display = 'block';
    }
}

function closePlugin() {
    document.getElementById('pluginModal').style.display = 'none';
}

// ============================================================
// CALCULATOR FUNCTIONS
// ============================================================

function addToCalc(value) { 
    const display = document.getElementById('calcDisplay');
    if (display) display.value += value; 
}

function clearCalc() { 
    const display = document.getElementById('calcDisplay');
    if (display) display.value = ''; 
}

function deleteLast() { 
    const display = document.getElementById('calcDisplay');
    if (display) display.value = display.value.slice(0, -1); 
}

function calculate() {
    const display = document.getElementById('calcDisplay');
    if (!display) return;
    
    try {
        const expression = display.value;
        // Security: only allow numbers and basic operators
        if (!/^[0-9+\-*/.() ]+$/.test(expression)) {
            display.value = 'Error';
            return;
        }
        
        const result = Function('"use strict"; return (' + expression + ')')();
        display.value = result;
    } catch (error) {
        display.value = 'Error';
    }
}

// ============================================================
// WEB SEARCH
// ============================================================

function performWebSearch() {
    const searchQuery = document.getElementById('searchQuery');
    const searchResults = document.getElementById('searchResults');
    if (!searchQuery || !searchResults) return;
    
    const query = searchQuery.value.trim();
    if (!query) return;
    
    const safeQuery = query.replace(/[<>]/g, '');
    
    searchResults.innerHTML = '<div style="text-align: center; padding: 20px;">Recherche en cours...</div>';
    searchResults.style.display = 'block';
    
    fetch(`/api/search?q=${encodeURIComponent(safeQuery)}`)
        .then(response => response.json())
        .then(data => {
            if (data.status === 'success') {
                searchResults.innerHTML = `
                    <h4 style="margin-bottom: 10px;">Resultats pour "${data.query}":</h4>
                    <div style="white-space: pre-wrap;">${data.results}</div>
                `;
            } else {
                searchResults.innerHTML = `<p style="color: #999;">Aucun resultat trouve pour "${data.query}"</p>`;
            }
        })
        .catch(error => {
            console.error('Web search error:', error);
            searchResults.innerHTML = `<p style="color: #ff4757;">Error: ${error.message}</p>`;
        });
}

// ============================================================
// WEATHER
// ============================================================

function getWeather() {
    const cityInput = document.getElementById('cityInput');
    const weatherResult = document.getElementById('weatherResult');
    if (!cityInput || !weatherResult) return;
    
    const city = cityInput.value.trim();
    if (!city) return;
    
    const safeCity = city.replace(/[<>]/g, '');
    
    if (window.secureChatApp?.androidInterface?.getHttpServerUrl) {
        const serverUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
        fetch(`${serverUrl}/api/weather/${encodeURIComponent(safeCity)}`)
            .then(response => response.json())
            .then(data => {
                weatherResult.innerHTML = `
                    <h3 style="margin-bottom: 10px;">${data.city}</h3>
                    <div style="font-size: 32px; margin: 10px 0;">${data.temperature}C</div>
                    <div style="font-size: 18px; margin: 5px 0;">${data.condition}</div>
                    <div style="font-size: 13px; color: #666; margin-top: 10px;">Humidite: ${data.humidity}% | Vent: ${data.wind}</div>
                `;
                weatherResult.style.display = 'block';
            })
            .catch(error => {
                console.error('Weather API error:', error);
                getWeatherFallback(safeCity, weatherResult);
            });
    } else {
        getWeatherFallback(safeCity, weatherResult);
    }
}

function getWeatherFallback(city, resultElement) {
    const temp = Math.floor(Math.random() * 25) + 5;
    const conditions = ['Ensoleille', 'Nuageux', 'Pluvieux'];
    const condition = conditions[Math.floor(Math.random() * conditions.length)];
    
    resultElement.innerHTML = `
        <h3 style="margin-bottom: 10px;">${city}</h3>
        <div style="font-size: 32px; margin: 10px 0;">${temp}C</div>
        <div style="font-size: 18px;">${condition}</div>
    `;
    resultElement.style.display = 'block';
}

// ============================================================
// JOKES
// ============================================================

const FALLBACK_JOKES = [
    "Pourquoi les plongeurs plongent-ils toujours en arriere ? Parce que sinon, ils tombent dans le bateau !",
    "Comment appelle-t-on un chat tombe dans un pot de peinture ? Un chat-mallow !",
    "Que dit un escargot quand il croise une limace ? 'Regarde le nudiste !'",
    "Qu'est-ce qu'un crocodile qui surveille une ecole ? Un Lacoste Gardien !",
    "Pourquoi les poissons n'aiment pas jouer au tennis ? Parce qu'ils ont peur du filet !"
];

function getRandomJoke() {
    const jokeResult = document.getElementById('jokeResult');
    if (!jokeResult) return;
    
    if (window.secureChatApp?.androidInterface?.getHttpServerUrl) {
        const serverUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
        fetch(`${serverUrl}/api/jokes/random`)
            .then(response => response.json())
            .then(data => {
                jokeResult.innerHTML = data.joke;
            })
            .catch(error => {
                console.error('Jokes API error:', error);
                getRandomJokeFallback(jokeResult);
            });
    } else {
        getRandomJokeFallback(jokeResult);
    }
}

function getRandomJokeFallback(resultElement) {
    resultElement.innerHTML = FALLBACK_JOKES[Math.floor(Math.random() * FALLBACK_JOKES.length)];
}

// ============================================================
// TIPS
// ============================================================

const FALLBACK_TIPS = {
    productivity: "Technique Pomodoro : Travaillez 25 minutes, puis faites une pause de 5 minutes. Repetez 4 fois, puis prenez une pause de 15-30 minutes.",
    health: "Buvez un grand verre d'eau des le reveil pour rehydrater votre corps apres la nuit. Votre cerveau vous remerciera !",
    tech: "Utilisez un gestionnaire de mots de passe pour creer et stocker des mots de passe uniques et forts pour chaque compte.",
    lifestyle: "Lisez 10 pages par jour. En un an, vous aurez lu environ 3650 pages, soit environ 12 livres !"
};

function getTip(category) {
    const tipResult = document.getElementById('tipResult');
    if (!tipResult) return;
    
    if (window.secureChatApp?.androidInterface?.getHttpServerUrl) {
        const serverUrl = window.secureChatApp.androidInterface.getHttpServerUrl();
        fetch(`${serverUrl}/api/tips/${encodeURIComponent(category)}`)
            .then(response => response.json())
            .then(data => {
                tipResult.innerHTML = data.tip;
            })
            .catch(error => {
                console.error('Tips API error:', error);
                getTipFallback(category, tipResult);
            });
    } else {
        getTipFallback(category, tipResult);
    }
}

function getTipFallback(category, resultElement) {
    resultElement.innerHTML = FALLBACK_TIPS[category] || "Conseil non disponible";
}

// ============================================================
// INFO MODAL & SERVER STATUS
// ============================================================

function showInfo() {
    const modal = document.getElementById('infoModal');
    modal.style.display = 'block';
    checkServerStatus();
}

function closeInfo() {
    document.getElementById('infoModal').style.display = 'none';
}

function checkServerStatus() {
    const httpStatus = document.getElementById('httpStatus');
    const wsStatus = document.getElementById('wsStatus');
    const aiStatus = document.getElementById('aiStatus');
    
    if (window.secureChatApp?.androidInterface) {
        try {
            const httpUrl = window.secureChatApp.androidInterface.getHttpServerUrl?.();
            if (httpUrl) {
                httpStatus.innerHTML = 'Active (' + httpUrl + ')';
                httpStatus.style.color = 'green';
            } else {
                httpStatus.innerHTML = 'Inactive';
                httpStatus.style.color = 'red';
            }
        } catch (e) {
            httpStatus.innerHTML = 'Error';
            httpStatus.style.color = 'red';
        }
        
        try {
            const wsCount = window.secureChatApp.androidInterface.getWebSocketClientsCount?.() || 'N/A';
            wsStatus.innerHTML = 'Active (' + wsCount + ' clients)';
            wsStatus.style.color = 'green';
        } catch (e) {
            wsStatus.innerHTML = 'Error';
            wsStatus.style.color = 'red';
        }
        
        try {
            const aiStats = window.secureChatApp.androidInterface.getAIServiceStats?.();
            if (aiStats && aiStats.includes('healthy')) {
                aiStatus.innerHTML = 'Active';
                aiStatus.style.color = 'green';
            } else {
                aiStatus.innerHTML = 'Inactive';
                aiStatus.style.color = 'red';
            }
        } catch (e) {
            aiStatus.innerHTML = 'Error';
            aiStatus.style.color = 'red';
        }
    } else {
        httpStatus.innerHTML = 'Interface not available';
        wsStatus.innerHTML = 'Interface not available';
        aiStatus.innerHTML = 'Interface not available';
    }
}

function testServers() {
    const button = event.target;
    button.innerHTML = 'Testing...';
    button.disabled = true;
    
    setTimeout(() => {
        if (window.secureChatApp?.androidInterface) {
            const httpUrl = window.secureChatApp.androidInterface.getHttpServerUrl?.();
            if (httpUrl) {
                fetch(httpUrl + '/api/status')
                    .then(response => response.json())
                    .then(data => {
                        alert('HTTP Server: ' + data.server + ' (Version ' + data.version + ')');
                    })
                    .catch(error => {
                        alert('HTTP Server Error: ' + error.message);
                    });
            } else {
                alert('HTTP Server not available');
            }
        } else {
            alert('Android interface not available');
        }
        
        button.innerHTML = 'Test Servers';
        button.disabled = false;
    }, 500);
}

// Log module loaded
console.log('[chat-plugins.js] Module loaded');

