/**
 * ChatAI - Error Handling and Debug Logging Module
 * 
 * Provides:
 * - ChatError class for structured errors
 * - Global error handler
 * - Conditional debug logging (debugLog)
 * - Error codes and messages
 */

// ============================================================
// DEBUG MODE CONFIGURATION
// ============================================================

// Set to false in production to disable debug logs
const DEBUG_MODE = true;

// Log levels
const LOG_LEVELS = {
    DEBUG: 0,
    INFO: 1,
    WARN: 2,
    ERROR: 3
};

// Current log level (DEBUG shows all, ERROR shows only errors)
let currentLogLevel = DEBUG_MODE ? LOG_LEVELS.DEBUG : LOG_LEVELS.WARN;

// ============================================================
// DEBUG LOGGING
// ============================================================

/**
 * Conditional debug logging function
 * Only logs if DEBUG_MODE is true and level >= currentLogLevel
 * @param {string} message - Message to log
 * @param {string} level - Log level ('debug', 'info', 'warn', 'error')
 * @param {...any} args - Additional arguments to log
 */
function debugLog(message, level = 'debug', ...args) {
    const levelNum = LOG_LEVELS[level.toUpperCase()] || LOG_LEVELS.DEBUG;
    
    if (levelNum < currentLogLevel) return;
    
    const timestamp = new Date().toISOString().substr(11, 12);
    const prefix = `[${timestamp}]`;
    
    switch (level.toLowerCase()) {
        case 'error':
            console.error(prefix, message, ...args);
            break;
        case 'warn':
            console.warn(prefix, message, ...args);
            break;
        case 'info':
            console.info(prefix, message, ...args);
            break;
        default:
            console.log(prefix, message, ...args);
    }
}

/**
 * Set the current log level
 * @param {string} level - 'debug', 'info', 'warn', or 'error'
 */
function setLogLevel(level) {
    const levelNum = LOG_LEVELS[level.toUpperCase()];
    if (levelNum !== undefined) {
        currentLogLevel = levelNum;
        console.log(`[chat-errors] Log level set to: ${level.toUpperCase()}`);
    }
}

// Make functions globally accessible
window.debugLog = debugLog;
window.setLogLevel = setLogLevel;

// ============================================================
// ERROR CODES
// ============================================================

const ERROR_CODES = {
    // General errors (1xxx)
    UNKNOWN: { code: 1000, message: 'Unknown error' },
    NETWORK: { code: 1001, message: 'Network error' },
    TIMEOUT: { code: 1002, message: 'Request timeout' },
    
    // API errors (2xxx)
    API_UNAVAILABLE: { code: 2001, message: 'API not available' },
    API_ERROR: { code: 2002, message: 'API returned error' },
    API_INVALID_RESPONSE: { code: 2003, message: 'Invalid API response' },
    
    // Model errors (3xxx)
    MODEL_NOT_FOUND: { code: 3001, message: 'Model not found' },
    MODEL_LOAD_FAILED: { code: 3002, message: 'Failed to load model' },
    MODEL_INFERENCE_FAILED: { code: 3003, message: 'Model inference failed' },
    
    // Configuration errors (4xxx)
    CONFIG_INVALID: { code: 4001, message: 'Invalid configuration' },
    CONFIG_MISSING: { code: 4002, message: 'Missing configuration' },
    
    // UI errors (5xxx)
    ELEMENT_NOT_FOUND: { code: 5001, message: 'UI element not found' },
    RENDER_FAILED: { code: 5002, message: 'Failed to render UI' }
};

// ============================================================
// CHAT ERROR CLASS
// ============================================================

/**
 * Structured error class for ChatAI
 */
class ChatError extends Error {
    /**
     * @param {string} code - Error code from ERROR_CODES
     * @param {string} details - Additional error details
     * @param {Error} cause - Original error that caused this error
     */
    constructor(code, details = '', cause = null) {
        const errorDef = ERROR_CODES[code] || ERROR_CODES.UNKNOWN;
        const message = details ? `${errorDef.message}: ${details}` : errorDef.message;
        
        super(message);
        this.name = 'ChatError';
        this.code = errorDef.code;
        this.errorCode = code;
        this.details = details;
        this.cause = cause;
        this.timestamp = new Date().toISOString();
    }
    
    /**
     * Convert to JSON for logging/storage
     */
    toJSON() {
        return {
            name: this.name,
            code: this.code,
            errorCode: this.errorCode,
            message: this.message,
            details: this.details,
            timestamp: this.timestamp,
            stack: this.stack
        };
    }
    
    /**
     * Log the error
     */
    log() {
        debugLog(`[ChatError ${this.code}] ${this.message}`, 'error');
        if (this.cause) {
            debugLog('Caused by:', 'error', this.cause);
        }
    }
}

// Make class globally accessible
window.ChatError = ChatError;
window.ERROR_CODES = ERROR_CODES;

// ============================================================
// GLOBAL ERROR HANDLER
// ============================================================

/**
 * Global error handler for unhandled errors
 */
function setupGlobalErrorHandler() {
    // Handle unhandled promise rejections
    window.addEventListener('unhandledrejection', (event) => {
        debugLog('Unhandled promise rejection:', 'error', event.reason);
        
        // Optionally show user-friendly error
        if (typeof showNotification === 'function') {
            showNotification('An error occurred. Please try again.', 'error');
        }
        
        // Prevent default browser behavior
        event.preventDefault();
    });
    
    // Handle uncaught errors
    window.onerror = function(message, source, lineno, colno, error) {
        debugLog(`Uncaught error: ${message}`, 'error', {
            source,
            lineno,
            colno,
            error
        });
        
        // Return true to prevent default browser error handling
        return true;
    };
    
    debugLog('[chat-errors] Global error handler installed', 'info');
}

// ============================================================
// ERROR HELPERS
// ============================================================

/**
 * Wrap an async function with error handling
 * @param {Function} fn - Async function to wrap
 * @param {string} context - Context for error messages
 * @returns {Function} Wrapped function
 */
function withErrorHandling(fn, context = '') {
    return async function(...args) {
        try {
            return await fn.apply(this, args);
        } catch (error) {
            if (error instanceof ChatError) {
                error.log();
                throw error;
            }
            
            const chatError = new ChatError('UNKNOWN', `${context}: ${error.message}`, error);
            chatError.log();
            throw chatError;
        }
    };
}

/**
 * Safe fetch with error handling
 * @param {string} url - URL to fetch
 * @param {object} options - Fetch options
 * @param {number} timeout - Timeout in ms (default 30s)
 * @returns {Promise<Response>}
 */
async function safeFetch(url, options = {}, timeout = 30000) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeout);
    
    try {
        const response = await fetch(url, {
            ...options,
            signal: controller.signal
        });
        
        clearTimeout(timeoutId);
        
        if (!response.ok) {
            throw new ChatError('API_ERROR', `HTTP ${response.status}: ${response.statusText}`);
        }
        
        return response;
    } catch (error) {
        clearTimeout(timeoutId);
        
        if (error.name === 'AbortError') {
            throw new ChatError('TIMEOUT', `Request to ${url} timed out after ${timeout}ms`);
        }
        
        if (error instanceof ChatError) {
            throw error;
        }
        
        throw new ChatError('NETWORK', error.message, error);
    }
}

// Make helpers globally accessible
window.withErrorHandling = withErrorHandling;
window.safeFetch = safeFetch;

// ============================================================
// INITIALIZATION
// ============================================================

// Setup global error handler on load
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', setupGlobalErrorHandler);
} else {
    setupGlobalErrorHandler();
}

console.log('[chat-errors.js] Module loaded - DEBUG_MODE:', DEBUG_MODE);

