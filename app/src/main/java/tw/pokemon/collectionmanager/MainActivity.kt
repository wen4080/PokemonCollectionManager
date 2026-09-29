package tw.pokemon.collectionmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import tw.pokemon.collectionmanager.ui.CollectionApp
import tw.pokemon.collectionmanager.ui.CollectionViewModel
import tw.pokemon.collectionmanager.ui.CollectionViewModelFactory

class MainActivity : ComponentActivity() {
    private val viewModel: CollectionViewModel by viewModels {
        val application = application as CollectionApplication
        CollectionViewModelFactory(
            repository = application.collectionRepository,
            masterDataRepository = application.masterDataRepository,
            preferencesRepository = application.preferencesRepository,
            backupManager = application.backupManager,
            imageRepository = application.imageRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CollectionApp(viewModel) }
    }
}
