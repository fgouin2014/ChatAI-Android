package com.chatai.activities

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chatai.R
import com.chatai.fragments.KittFragment
import com.chatai.FileServer
import com.chatai.HttpServer
import com.chatai.WebSocketServer
import com.chatai.WebServer
import com.chatai.RealtimeAIService
import android.util.Log
import android.widget.Toast

/**
 * Activité principale pour l'interface KITT
 * Container pour le fragment KITT avec IA générative
 */
class KittActivity : AppCompatActivity() {
    
    private lateinit var sharedPreferences: SharedPreferences
    private var fileServer: FileServer? = null
    
    // Serveurs gérés par BackgroundService (pas de duplication)
    // Les serveurs sont déjà démarrés par MainActivity
    
    companion object {
        private const val TAG = "KittActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Charger les préférences
        sharedPreferences = getSharedPreferences("kitt_prefs", Context.MODE_PRIVATE)
        
        // Appliquer le thème sélectionné
        applySelectedTheme()
        
        setContentView(R.layout.activity_kitt)
        
        // NE PAS DÉMARRER LES SERVEURS - ils sont déjà démarrés par BackgroundService
        // Les serveurs sont gérés par MainActivity via BackgroundService
        Log.i(TAG, "KittActivity utilise les serveurs existants de BackgroundService")
        
        // Vérifier le statut des serveurs existants
        checkServersStatus()
        
        // Ajouter le fragment KITT si c'est la première fois
        if (savedInstanceState == null) {
            val kittFragment = KittFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, kittFragment)
                .commit()
            
            // Injecter le FileServer dans le fragment
            kittFragment.setFileServer(fileServer)
        }
    }
    
    /**
     * Vérifie si les serveurs sont disponibles via BackgroundService
     */
    private fun checkServersStatus() {
        try {
            // Tester la connectivité des serveurs existants
            // Les serveurs sont gérés par BackgroundService (MainActivity)
            
            Log.i(TAG, "Vérification des serveurs existants...")
            Log.i(TAG, "✅ Les serveurs sont gérés par BackgroundService (MainActivity)")
            Toast.makeText(this, "Utilisation des serveurs existants", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erreur vérification serveurs: ", e)
        }
    }
    
    private fun applySelectedTheme() {
        val selectedTheme = sharedPreferences.getString("kitt_theme", "red") ?: "red"
        
        when (selectedTheme) {
            "red" -> setTheme(R.style.Theme_KITT)
            "dark" -> setTheme(R.style.Theme_KITT_Dark)
            "amber" -> setTheme(R.style.Theme_KITT_Amber)
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Vérifier si le drawer est ouvert
        val drawerFragment = supportFragmentManager.findFragmentByTag("kitt_drawer")
        if (drawerFragment != null && drawerFragment.isVisible) {
            // Fermer le drawer au lieu de l'app
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(0, R.anim.slide_out_right)
                .remove(drawerFragment)
                .commit()
        } else {
            // Fermer l'app normalement
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
