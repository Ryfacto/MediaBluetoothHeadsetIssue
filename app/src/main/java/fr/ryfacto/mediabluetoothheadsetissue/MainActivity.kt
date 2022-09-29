package fr.ryfacto.mediabluetoothheadsetissue

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val audioMenuController by lazy { AudioMenuController(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        audioMenuController.attachTo(this)
    }

    override fun onResume() {
        super.onResume()

        audioMenuController.activate()
    }

    override fun onDestroy() {
        super.onDestroy()

        audioMenuController.deactivate()
    }
}
