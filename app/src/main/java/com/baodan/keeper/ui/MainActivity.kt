package com.baodan.keeper.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.baodan.keeper.R
import com.baodan.keeper.databinding.ActivityMainBinding
import com.baodan.keeper.ui.home.HomeFragment
import com.baodan.keeper.ui.policy.PolicyListFragment
import com.baodan.keeper.ui.settings.SettingsFragment
import com.baodan.keeper.ui.stats.StatsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNav.setOnItemSelectedListener { item ->
            val fragment: Fragment = when (item.itemId) {
                R.id.tab_home -> HomeFragment()
                R.id.tab_policy -> PolicyListFragment()
                R.id.tab_stats -> StatsFragment()
                else -> SettingsFragment()
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, fragment)
                .commit()
            true
        }

        if (savedInstanceState == null) {
            binding.bottomNav.selectedItemId = R.id.tab_home
        }
    }
}
