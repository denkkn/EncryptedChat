package com.example.encryptedchat

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.encryptedchat.databinding.ActivityMainBinding
import com.example.encryptedchat.ui.ContactsFragment
import com.example.encryptedchat.ui.HomeFragment
import com.example.encryptedchat.ui.SettingsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var homeFragment: HomeFragment? = null
    private var contactsFragment: ContactsFragment? = null
    private var settingsFragment: SettingsFragment? = null
    private var currentTag: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 恢复或创建Fragment
        val fm = supportFragmentManager
        homeFragment = fm.findFragmentByTag("home") as? HomeFragment ?: HomeFragment()
        contactsFragment = fm.findFragmentByTag("contacts") as? ContactsFragment ?: ContactsFragment()
        settingsFragment = fm.findFragmentByTag("settings") as? SettingsFragment ?: SettingsFragment()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchTo(homeFragment!!, "home")
                R.id.nav_contacts -> switchTo(contactsFragment!!, "contacts")
                R.id.nav_settings -> switchTo(settingsFragment!!, "settings")
                else -> false
            }
        }

        // 首次启动显示首页
        if (savedInstanceState == null) {
            fm.beginTransaction()
                .add(R.id.fragment_container, homeFragment!!, "home")
                .add(R.id.fragment_container, contactsFragment!!, "contacts").hide(contactsFragment!!)
                .add(R.id.fragment_container, settingsFragment!!, "settings").hide(settingsFragment!!)
                .commit()
            currentTag = "home"
            binding.bottomNav.selectedItemId = R.id.nav_home
        } else {
            // 恢复active fragment状态
            currentTag = savedInstanceState.getString("currentTag", "home")
            binding.bottomNav.selectedItemId = when (currentTag) {
                "contacts" -> R.id.nav_contacts
                "settings" -> R.id.nav_settings
                else -> R.id.nav_home
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("currentTag", currentTag)
    }

    private fun switchTo(target: Fragment, tag: String): Boolean {
        if (tag == currentTag) return true
        val fm = supportFragmentManager
        val current = currentTag?.let { fm.findFragmentByTag(it) }
        fm.beginTransaction().apply {
            current?.let { hide(it) }
            show(target)
        }.commit()
        currentTag = tag
        return true
    }

    @Deprecated("Use registerForActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == HomeFragment.REQUEST_FILE_PICK && resultCode == RESULT_OK) {
            data?.data?.let { homeFragment?.handleFileResult(it) }
        }
    }
}
