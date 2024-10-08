package pl.iterative.call.buster

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import pl.iterative.call.buster.ui.theme.IncomingCallHandlerTheme
import pl.iterative.call.buster.ui.theme.Typography
import pl.iterative.call.buster.workers.UpdateDatabaseWorker
import merail.tools.permissions.SettingsSnackbar
import merail.tools.permissions.role.RoleRequester
import merail.tools.permissions.role.RoleState
import merail.tools.permissions.runtime.RuntimePermissionRequester
import merail.tools.permissions.runtime.RuntimePermissionState
import merail.tools.permissions.special.SpecialPermissionRequester
import merail.tools.permissions.special.SpecialPermissionState
import java.io.IOException
import java.util.Date
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {

    private lateinit var specialPermissionRequester: SpecialPermissionRequester
    private lateinit var runtimePermissionRequester: RuntimePermissionRequester
    private lateinit var roleRequester: RoleRequester
    private lateinit var workManager: WorkManager;
    private lateinit var database: BlockedNumbersDatabase;
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
    private var lastUpdateDateFormatted = mutableStateOf("");
    private var dialogOpen = mutableStateOf(false);
    private var fileFormatDescOpen = mutableStateOf(false);
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
        database = BlockedNumbersDatabase.getInstance(applicationContext);
        workManager = WorkManager.getInstance(applicationContext);
        loadPersistentData();
        workManager.getWorkInfosByTagLiveData(getString(R.string.auto_update_job_tag)).observeForever { jobs ->
            // Update added numbers count
            addedNumbersCount.intValue = database.blockedNumberDao().getSavedNumbersCount();
            lastUpdateDateFormatted.value =
                preferenceHelper.getPreference(applicationContext, "db_update_timestamp", "0")
                    .toLong()?.let {
                        Date(it).toLocaleString();
                    }.toString();
        }
    }

    private fun getWelcomeText(): String {
        var welcomeText = "Welcome to the Call Buster app!\n";
        if (addedNumbersCount.intValue > 0) {
            if (addedNumbersCount.value === 1) {
                welcomeText += "Currently there is " + addedNumbersCount.value + " imported number.\n";
            } else {
                welcomeText += "Currently there are " + addedNumbersCount.value + " imported numbers.\n";
            }
            welcomeText += "Last database update: " + lastUpdateDateFormatted.value;
        } else {
            welcomeText += "Start by granting permissions and importing a blocked numbers database."
        }

        return welcomeText;
    }

    @Composable
    private fun Content() {
        IncomingCallHandlerTheme {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = getWelcomeText(),
                        Modifier
                            .fillMaxWidth(0.85f)
                            .defaultMinSize(
                                minWidth = 72.dp,
                            )
                            .padding(8.dp)
                    )
                    Button(onClick = { showFileFormatDescription() }) {
                        Text("?")
                    }
                }
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
                    isVisible = isSpecialPermissionButtonVisible.value && showInfoWindow.value,
                )
                when {
                    isSpecialPermissionButtonVisible.value && showInfoWindow.value -> {
                        Text(

                            text = "Special permission is required to show the info window.",
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
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
                FileFormatDescription()
                Text(
                    text = "How to handle a call from a blocked number?",
                    modifier = Modifier.padding(start = 5.dp, bottom = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 0.dp, start = 4.dp)
                        .clickable(role = Role.Checkbox, onClick = {
                            saveCallHandlingPreference(
                                blockTheCall,
                                !blockTheCall.value,
                                "call_blocking_block"
                            )
                        })
                ) {
                    Checkbox(
                        checked = blockTheCall.value,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 5.dp)
                    )
                    Text(
                        "Block the call"
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 0.dp, start = 4.dp, top = 4.dp)
                        .clickable(role = Role.Checkbox, onClick = {
                            saveCallHandlingPreference(
                                silenceTheCall,
                                !silenceTheCall.value,
                                "call_blocking_silence"
                            )
                        })
                ) {
                    Checkbox(
                        checked = silenceTheCall.value,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 5.dp)
                    )
                    Text(
                        "Silence the call"
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(bottom = 0.dp, start = 4.dp, top = 4.dp)
                        .clickable(role = Role.Checkbox, onClick = {
                            saveCallHandlingPreference(
                                showInfoWindow,
                                !showInfoWindow.value,
                                "call_blocking_show_window"
                            )
                        })
                ) {
                    Checkbox(
                        checked = showInfoWindow.value,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 5.dp)
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
                    fontSize = 20.sp,
                    color = Color.White
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = getWelcomeText(),
                    Modifier
                        .defaultMinSize(
                            minWidth = 72.dp,
                        )
                        .fillMaxWidth(0.85f)
                        .padding(8.dp)
                )
                Button(onClick = {  }) {
                    Text("?")
                }
            }
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

    private fun showFileFormatDescription() {
        fileFormatDescOpen.value = true;
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
                        databaseManager.parseStream(inputStream, applicationContext, addedNumbersCount, lastUpdateDateFormatted)
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
                                                    workManager.cancelAllWorkByTag(getString(R.string.auto_update_job_tag));

                                                    val jobConstraints = Constraints.Builder()
                                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                                        .build();

                                                    val saveRequest =
                                                        PeriodicWorkRequestBuilder<UpdateDatabaseWorker>(
                                                            updateFrequency.value.toLong(),
                                                            TimeUnit.DAYS
                                                        )
                                                            .addTag(getString(R.string.auto_update_job_tag))
                                                            .setConstraints(jobConstraints)
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
                                                                addedNumbersCount,
                                                                lastUpdateDateFormatted
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
            modifier = Modifier
                .padding(bottom = 0.dp, start = 16.dp)
                .clickable(role = Role.Checkbox, onClick = {
                    updateAutomatically.value = !updateAutomatically.value
                })
        ) {
            Checkbox(
                checked = updateAutomatically.value,
                onCheckedChange = null,
                modifier = Modifier.padding(end = 5.dp)
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
                            .padding(start = 0.dp, end = 0.dp)
                            .align(alignment = Alignment.Top)
                            .height(50.dp)
                    )
                    Text(
                        "days"
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

    @Composable
    fun FileFormatDescription() {
        when {
            fileFormatDescOpen.value -> {
                Dialog(onDismissRequest = { fileFormatDescOpen.value = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f)
                            .padding(0.dp),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Column(
                            Modifier.fillMaxHeight().verticalScroll(
                                rememberScrollState()
                            ),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "The input files needs to be UTF-8 encoded JSON file, with an array of objects. Each object should have a 'phone' property and optionally, a 'name' property.\nFor example:\n[\n" +
                                        "    {\n" +
                                        "        \"phone\": \"+48123456789\",\n" +
                                        "        \"name\": \"abcde\"\n" +
                                        "    },\n" +
                                        "    {\n" +
                                        "        \"phone\": \"+23480312345678\",\n" +
                                        "        \"name\": \"spam number\"\n" +
                                        "    }\n" +
                                        "]",
                                modifier = Modifier
                                    .wrapContentSize(Alignment.TopCenter)
                                    .padding(8.dp),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Left,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                            ) {

                                TextButton(
                                    onClick = { fileFormatDescOpen.value = false },
                                    modifier = Modifier.padding(8.dp),
                                ) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }
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
        addedNumbersCount.intValue = database.blockedNumberDao().getSavedNumbersCount();

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