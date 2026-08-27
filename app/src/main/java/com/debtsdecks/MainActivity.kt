package com.debtsdecks

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration

class MainActivity : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            r = 8; g = 8; b = 8; a = 8
            depth = 16; stencil = 8
            useImmersiveMode = true
            numSamples = 2
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
        }

        initialize(GameApp(), config)
    }
}