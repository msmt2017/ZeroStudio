package android.zero.studio.uidesigner.utils

import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * FileCreator Class is used to create a file with given name and MIME Type.
 */
abstract class FileCreator(actvty: AppCompatActivity) {
    /** To create a file  */
    private val createFile: ActivityResultLauncher<String>

    /** MIME Type of file  */
    private var mimeType = "*/*"

    /**
     * Constructor of class
     *
     * @param actvty Instance of AppCompatActivity
     */
    init {
        createFile = actvty.registerForActivityResult(
            ActivityResultContracts.CreateDocument(mimeType)
        ) { uri ->
            if (uri != null) {
                onCreateFile(uri)
            }
        }
    }

    /**
     * Abstract method onCreateFile to call on result
     */
    abstract fun onCreateFile(uri: Uri)

    /**
     * Method to create file
     *
     * @param fileName The name of the file
     * @param mimeType The MIME type of the file
     */
    fun create(fileName: String, mimeType: String) {
        this.mimeType = mimeType // Set MIME type
        createFile.launch(fileName) // Launch file
    }
}
