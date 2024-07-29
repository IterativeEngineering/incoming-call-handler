package merail.calls.handler

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import merail.calls.handler.ui.theme.IncomingCallHandlerTheme
import merail.calls.handler.ui.theme.Typography
import merail.calls.handler.workers.UpdateDatabaseWorker
import merail.tools.permissions.SettingsSnackbar
import merail.tools.permissions.role.RoleRequester
import merail.tools.permissions.role.RoleState
import merail.tools.permissions.runtime.RuntimePermissionRequester
import merail.tools.permissions.runtime.RuntimePermissionState
import merail.tools.permissions.special.SpecialPermissionRequester
import merail.tools.permissions.special.SpecialPermissionState
import java.io.IOException
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {

    private lateinit var specialPermissionRequester: SpecialPermissionRequester
    private lateinit var runtimePermissionRequester: RuntimePermissionRequester
    private lateinit var roleRequester: RoleRequester
    private val logger = OperationLogger();
    private val preferenceHelper = PreferenceHelper();
    private val databaseUpdater = UpdateDatabaseFromUrl();
    private val databaseManager = DatabaseManager()

    private val specialPermission = Manifest.permission.SYSTEM_ALERT_WINDOW

    private val runtimePermissions =
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
        )

    private val rolePermission = RoleManager.ROLE_CALL_SCREENING

    private lateinit var isSpecialPermissionButtonVisible: MutableState<Boolean>
    private lateinit var isRuntimePermissionsButtonVisible: MutableState<Boolean>
    private lateinit var rolePermissionButtonVisible: MutableState<Boolean>
    private lateinit var logContents: MutableState<String>;

    private var addedNumbersCount = mutableIntStateOf(0);
    private var dialogOpen = mutableStateOf(false);
    private var fileUrl = mutableStateOf("")
    private var updateAutomatically = mutableStateOf(false)
    private var updateFrequency = mutableStateOf("1")
    private var fileDownloadInProgress = mutableStateOf(false)
    private var blockTheCall = mutableStateOf(false)
    private var silenceTheCall = mutableStateOf(false)
    private var showInfoWindow = mutableStateOf(false)

    //    Call handling

    private val onSpecialPermissionClick = {
        specialPermissionRequester.requestPermission {
            isSpecialPermissionButtonVisible.value = it.second == SpecialPermissionState.DENIED
        }
    }
    private val onRuntimePermissionsClick = {
        runtimePermissionRequester.requestPermissions {
            isRuntimePermissionsButtonVisible.value =
                runtimePermissionRequester.areAllPermissionsGranted().not()
            if (it.containsValue(RuntimePermissionState.PERMANENTLY_DENIED)) {
                val settingsOpeningSnackbar = SettingsSnackbar(
                    activity = this,
                    view = window.decorView,
                )
                settingsOpeningSnackbar.showSnackbar(
                    text = "You must grant permissions in Settings!",
                    actionName = "Settings",
                )
            }
        }
    }

    private val rolePermissionClick = {
        roleRequester.requestRole {
            rolePermissionButtonVisible.value = it.second == RoleState.DENIED
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Content()
        }

        specialPermissionRequester = SpecialPermissionRequester(
            activity = this,
            requestedPermission = specialPermission,
        )
        runtimePermissionRequester = RuntimePermissionRequester(
            activity = this,
            requestedPermissions = runtimePermissions,
        )
        roleRequester = RoleRequester(
            activity = this,
            requestedRole = rolePermission,
        )
        loadPersistentData()
    }

    @Composable
    private fun Content() {
        IncomingCallHandlerTheme {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                Text(
                    "Welcome to the call blocker app.\nCurrently there are " + addedNumbersCount.value + " imported numbers.",
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(
                            minWidth = 72.dp,
                        )
                        .padding(8.dp)
                )
                logContents = remember {
                    mutableStateOf("")
                }
                dialogOpen = remember { mutableStateOf(false) }
                fileUrl = remember {
                    mutableStateOf("")
                }

                rolePermissionButtonVisible = remember {
                    mutableStateOf(
                        roleRequester.isRoleGranted().not()
                    )
                }
                Button(
                    onClick = {
                        rolePermissionClick.invoke()
                    },
                    text = "Get role permissions",
                    isVisible = rolePermissionButtonVisible.value,
                )
                isRuntimePermissionsButtonVisible = remember {
                    mutableStateOf(
                        runtimePermissionRequester.areAllPermissionsGranted().not()
                    )
                }
                Button(
                    onClick = {
                        onRuntimePermissionsClick.invoke()
                    },
                    text = "Get runtime permissions",
                    isVisible = isRuntimePermissionsButtonVisible.value,
                )
                isSpecialPermissionButtonVisible = remember {
                    mutableStateOf(
                        specialPermissionRequester.isPermissionGranted().not()
                    )
                }
                Button(
                    onClick = {
                        onSpecialPermissionClick.invoke()
                    },
                    text = "Get special permission",
                    isVisible = isSpecialPermissionButtonVisible.value,
                )
                Button(
                    onClick = {
                        showFileChooser()
                    },
                    text = "Import numbers from a file",
                    isVisible = true
                )
                Button(
                    onClick = {
                        importFromUrl()
                    },
                    text = "Import numbers from URL",
                    isVisible = true
                )
//                Button(
//                    onClick = { toggleLog() },
//                    text = "Show/hide log",
//                    isVisible = true
//                )
//                Button(
//                    onClick = { logger.clearLog(applicationContext); logContents.value = ""; },
//                    text = "Clear log permanently",
//                    isVisible = true
//                )
                Text(
                    text = logContents.value,
                    style = Typography.titleSmall,
                    modifier = Modifier
                        .padding(top = 2.dp, start = 4.dp)
                        .verticalScroll(
                            rememberScrollState()
                        )
                )
                FileUrlDialog()
                Text(
                    text = "How to handle a call from a blocked number?",
                    modifier = Modifier.padding(start = 5.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 0.dp, start = 0.dp)
                ) {
                    Checkbox(
                        checked = blockTheCall.value,
                        onCheckedChange = {
                            saveCallHandlingPreference(
                                blockTheCall,
                                it,
                                "call_blocking_block"
                            )
                        },
                    )
                    Text(
                        "Block the call"
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 0.dp)
                ) {
                    Checkbox(
                        checked = silenceTheCall.value,
                        onCheckedChange = {
                            saveCallHandlingPreference(
                                silenceTheCall,
                                it,
                                "call_blocking_silence"
                            )
                        }
                    )
                    Text(
                        "Silence the call"
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 0.dp)
                ) {
                    Checkbox(
                        checked = showInfoWindow.value,
                        onCheckedChange = {
                            saveCallHandlingPreference(
                                showInfoWindow,
                                it,
                                "call_blocking_show_window"
                            )
                        }
                    )
                    Text(
                        "Show info window"
                    )
                }
            }
        }
    }

    @Composable
    private fun Button(
        onClick: () -> Unit,
        text: String,
        isVisible: Boolean,
    ) {
        if (isVisible) {
            Button(
                onClick = {
                    onClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(
                        minWidth = 72.dp,
                    )
                    .padding(4.dp),
                contentPadding = PaddingValues(
                    vertical = 8.dp,
                ),
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.DarkGray,
                ),
            ) {
                Text(
                    text = text,
                    style = Typography.titleLarge,
                    fontSize = 20.sp
                )
            }
        }
    }

    @Preview(
        showBackground = true,
    )
    @Composable
    private fun ContentPreview() {
        Column {
            Button(
                onClick = { },
                text = "Get special permissions",
                isVisible = true,
            )

            Button(
                onClick = { },
                text = "Get runtime permissions",
                isVisible = true,
            )

            Button(
                onClick = { },
                text = "Get role permissions",
                isVisible = true,
            )
            Button(
                onClick = { },
                text = "Show/hide log",
                isVisible = true
            )
            Button(
                onClick = { logger.clearLog(applicationContext); logContents.value = ""; },
                text = "Clear log permanently",
                isVisible = true
            )
            Text(
                text = "Log appears here",
                style = Typography.titleSmall,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp)
            )
            FileUrlDialog()
        }
    }

    private fun showFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setType("application/json")
        intent.addCategory(Intent.CATEGORY_OPENABLE)

        try {
            startActivityForResult(Intent.createChooser(intent, "Select a file"), 100)
        } catch (exception: Exception) {
            Toast.makeText(this, "please install a file manager", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importFromUrl() {
        fileUrl.value = "";
        dialogOpen.value = true
    }

    private fun toggleLog() {
        if (logContents.value.isNotEmpty()) {
            logContents.value = "";
        } else {
            logContents.value = logger.getLog(applicationContext)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 100 && resultCode === RESULT_OK && data != null) {
            val uri: Uri = data.data!!;

            try {
                Thread {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        databaseManager.parseStream(inputStream, applicationContext, addedNumbersCount)
                    }
                }.start()

            } catch (e: Exception) {
                // Exception may occur when trying to load a file from Google Drive which no longer exists
                val errorMsg = "Error occurred while loading a file $e"
                showToast(errorMsg);
                logger.saveToLog(applicationContext, errorMsg);
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @Composable
    fun FileUrlDialog() {
        when {
            dialogOpen.value -> {
                Dialog(onDismissRequest = { dialogOpen.value = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(350.dp)
                            .padding(8.dp),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Column(
                            Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "Enter blocked numbers database URL:",
                                    modifier = Modifier
                                        .wrapContentSize(Alignment.TopCenter)
                                        .padding(top = 16.dp),
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                SimpleFilledTextFieldSample()
                            }

                            AutoUpdateCheckbox()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                TextButton(
                                    onClick = { dialogOpen.value = false },
                                    modifier = Modifier.padding(8.dp),
                                ) {
                                    Text("Dismiss")
                                }
                                when {
                                    !fileDownloadInProgress.value -> {
                                        TextButton(
                                            onClick = {
                                                if (updateAutomatically.value) {
                                                    // Schedule auto update job
                                                    preferenceHelper.setPreference(
                                                        applicationContext,
                                                        getString(R.string.shared_preference_file_url),
                                                        fileUrl.value
                                                    );
                                                    // Remove previous jobs first
                                                    val workManager =
                                                        WorkManager.getInstance(applicationContext)

                                                    workManager.cancelAllWorkByTag(getString(R.string.auto_update_job_tag))
                                                    val saveRequest =
                                                        PeriodicWorkRequestBuilder<UpdateDatabaseWorker>(
                                                            updateFrequency.value.toLong(),
                                                            TimeUnit.MINUTES
                                                        )
                                                            .addTag(getString(R.string.auto_update_job_tag))
                                                            .build()
                                                    workManager.enqueue(saveRequest);
                                                    dialogOpen.value = false

                                                } else {
                                                    fileDownloadInProgress.value = true
                                                    Thread {
                                                        try {
                                                            databaseUpdater.updateDatabaseFromUrl(
                                                                applicationContext,
                                                                fileUrl.value,
                                                                addedNumbersCount
                                                            );
                                                        } catch (e: IOException) {
                                                            Looper.prepare()
                                                            showToast("Error occurred while fetching a file from " + fileUrl.value);
                                                        } finally {
                                                            dialogOpen.value = false
                                                            fileDownloadInProgress.value = false
                                                        }
                                                    }.start()
                                                }
                                            },
                                            modifier = Modifier.padding(8.dp),
                                        ) {
                                            Text("Confirm")
                                        }
                                    }

                                    fileDownloadInProgress.value -> {
                                        TextButton(modifier = Modifier
                                            .alpha(0.5F)
                                            .padding(8.dp), onClick = {}) {
                                            Text("Confirm")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


    }

    @Composable
    fun AutoUpdateCheckbox() {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 0.dp)
        ) {
            Checkbox(
                checked = updateAutomatically.value,
                onCheckedChange = { updateAutomatically.value = it }
            )
            Text(
                "Update automatically"
            )
        }
        when {
            updateAutomatically.value -> {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = 0.dp, start = 12.dp)
                ) {
                    Text(
                        "Update frequency:"
                    )
                    TextField(
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        value = updateFrequency.value,
                        onValueChange = { updateFrequency.value = it },
                        label = { Text("") },
                        modifier = Modifier
                            .width(75.dp)
                            .padding(start = 8.dp, end = 8.dp)
                            .align(alignment = Alignment.Top)
                            .height(50.dp)
                    )
//                    Text(
//                        "day(s)"
//                    )
                    Text(
                        "minutes (min. value: 15)"
                    )
                }
            }
        }
    }


    @Composable
    fun SimpleFilledTextFieldSample() {
        TextField(
            value = fileUrl.value,
            onValueChange = { fileUrl.value = it },
            label = { Text("") }
        )
    }

    private fun saveCallHandlingPreference(
        variable: MutableState<Boolean>,
        newVal: Boolean,
        persistentPreferenceKey: String
    ) {
        variable.value = newVal;
        preferenceHelper.setPreference(
            applicationContext,
            persistentPreferenceKey,
            newVal.toString()
        )
    }

    private fun loadPersistentData() {
        // Load added numbers count
        val database = BlockedNumbersDatabase.getInstance(applicationContext);
        addedNumbersCount.value = database.blockedNumberDao().getSavedNumbersCount();

        blockTheCall.value =
            preferenceHelper.getPreference(applicationContext, "call_blocking_block").toBoolean()
        silenceTheCall.value =
            preferenceHelper.getPreference(applicationContext, "call_blocking_silence").toBoolean()
        showInfoWindow.value =
            preferenceHelper.getPreference(applicationContext, "call_blocking_show_window")
                .toBoolean()
    }

    /**
     * Shows [message] in a Toast.
     */
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}