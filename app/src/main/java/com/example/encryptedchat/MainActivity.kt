package com.example.encryptedchat

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.encryptedchat.databinding.ActivityMainBinding
import com.example.encryptedchat.ui.*

class MainActivity : AppCompatActivity() {
    private lateinit var b: ActivityMainBinding
    private var home: HomeFragment? = null
    private var contacts: ContactsFragment? = null
    private var settings: SettingsFragment? = null
    private var currentTag = "home"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查密钥，没有则跳转账户设置
        val app = application as ChatApp
        if (!app.keyManager.hasKeyPair()) {
            startActivity(Intent(this, AccountSetupActivity::class.java))
            finish()
            return
        }

        ThemeManager.apply(this)

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        val fm = supportFragmentManager
        home = fm.findFragmentByTag("home") as? HomeFragment ?: HomeFragment()
        contacts = fm.findFragmentByTag("contacts") as? ContactsFragment ?: ContactsFragment()
        settings = fm.findFragmentByTag("settings") as? SettingsFragment ?: SettingsFragment()

        b.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchTo(home!!, "home")
                R.id.nav_contacts -> switchTo(contacts!!, "contacts")
                R.id.nav_settings -> switchTo(settings!!, "settings")
                else -> false
            }
        }

        if (savedInstanceState == null) {
            fm.beginTransaction().apply {
                add(R.id.fragment_container, home!!, "home")
                add(R.id.fragment_container, contacts!!, "contacts").hide(contacts!!)
                add(R.id.fragment_container, settings!!, "settings").hide(settings!!)
            }.commit()
        } else {
            currentTag = savedInstanceState.getString("currentTag", "home")
            b.bottomNav.selectedItemId = when (currentTag) {
                "contacts" -> R.id.nav_contacts; "settings" -> R.id.nav_settings; else -> R.id.nav_home
            }
        }
    }

    override fun onSaveInstanceState(s: Bundle) { super.onSaveInstanceState(s); s.putString("currentTag", currentTag) }

    private fun switchTo(target: Fragment, tag: String): Boolean {
        if (tag == currentTag) return true
        val fm = supportFragmentManager
        val cur = currentTag.let { fm.findFragmentByTag(it) }
        fm.beginTransaction().apply { cur?.let { hide(it) }; show(target) }.commit()
        currentTag = tag; return true
    }
}
