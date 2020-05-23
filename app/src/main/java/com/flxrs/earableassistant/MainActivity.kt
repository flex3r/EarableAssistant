package com.flxrs.earableassistant

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.flxrs.earableassistant.main.MainFragment

class MainActivity : AppCompatActivity(R.layout.main_activity) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, MainFragment.newInstance())
                .commitNow()
        }
    }
}
