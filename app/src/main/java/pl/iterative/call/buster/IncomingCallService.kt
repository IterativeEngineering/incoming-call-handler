package pl.iterative.call.buster

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class IncomingCallService : CallScreeningService() {

    companion object {
        @SuppressLint("StaticFieldLeak")
        private val incomingCallAlert = IncomingCallAlert()

        private val countryCodesHelper = CountryCodes();
        private val preferenceHelper = PreferenceHelper();
        private val logger = OperationLogger();
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        incomingCallAlert.closeWindow()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        if (callDetails.callDirection == Call.Details.DIRECTION_INCOMING) {
            var phoneNumber = callDetails.handle.schemeSpecificPart
            logger.saveToLog(applicationContext, "Incoming call from $phoneNumber");

            if (!phoneNumber.startsWith("+")) {
//                Add missing country prefix
                val countryCodeValue =
                    (this.getSystemService(TELEPHONY_SERVICE) as TelephonyManager).networkCountryIso
                val countryPrefix = "+" + countryCodesHelper.getCode(countryCodeValue.uppercase());
                phoneNumber = countryPrefix + phoneNumber;
            }

            handleIncomingCall(phoneNumber, callDetails);
        }
    }

    private fun handleIncomingCall(phoneNumber: String, callDetails: Call.Details) {
        val db = BlockedNumbersDatabase.getInstance(applicationContext);
        val blockedNumberName = db.blockedNumberDao().getByNumber(phoneNumber);
        val numberIsInTheDb = blockedNumberName != null

        if (numberIsInTheDb) {
            // Load users preferences
            val blockTheCall =
                preferenceHelper.getPreference(
                    applicationContext,
                    "call_blocking_block"
                )
                    .toBoolean()
            val silenceTheCall =
                preferenceHelper.getPreference(
                    applicationContext,
                    "call_blocking_silence"
                )
                    .toBoolean()
            val showInfoWindow =
                preferenceHelper.getPreference(
                    applicationContext,
                    "call_blocking_show_window"
                )
                    .toBoolean()

            if (showInfoWindow) {
                phoneNumber?.let {
                    var infoWindowText = "Warning: $it";
                    if (blockedNumberName?.name?.isNotEmpty() == true) {
                        infoWindowText += " (" + blockedNumberName.name + ") "
                    }
                    infoWindowText += " is in the blocked numbers database.";
                    incomingCallAlert.showWindow(this, infoWindowText)
                }
            }
            if (blockTheCall || silenceTheCall) {
                var logMessage = "Handling incoming call from $phoneNumber:  ";
                if (blockTheCall) {
                    logMessage += "block "
                }
                if (silenceTheCall) {
                    logMessage += "silence"
                }
                logger.saveToLog(applicationContext, logMessage);
                val response = CallResponse.Builder()
                    // Sets whether the incoming call should be blocked.
                    .setDisallowCall(blockTheCall)
                    // Sets whether the incoming call should be rejected as if the user did so manually.
                    .setRejectCall(blockTheCall)
                    // Sets whether ringing should be silenced for the incoming call.
                    .setSilenceCall(silenceTheCall)
                    // Sets whether a missed call notification should not be shown for the incoming call.
                    .setSkipNotification(blockTheCall);

                respondToCall(callDetails, response.build())
            } else {
                // Handle in default dialer
                logger.saveToLog(applicationContext,
                    "Handling incoming call from $phoneNumber in the default dialer"
                );
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        } else {
            // Handle in default dialer
            logger.saveToLog(applicationContext,
                "Handling incoming call from $phoneNumber in the default dialer"
            );
            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }
}