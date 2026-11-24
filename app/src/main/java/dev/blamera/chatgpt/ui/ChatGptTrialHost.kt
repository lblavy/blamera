package dev.blamera.chatgpt.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dev.blamera.chatgpt.AndroidTrialRegistration
import dev.blamera.chatgpt.HttpsTrialHttp
import dev.blamera.chatgpt.OpenAiTrialGateway
import dev.blamera.chatgpt.TrialActivityState
import dev.blamera.chatgpt.TrialAppForeground
import dev.blamera.chatgpt.TrialGatewayEnvironment
import dev.blamera.chatgpt.TrialLoopback
import dev.blamera.chatgpt.TrialSessionCoordination
import dev.blamera.chatgpt.TrialSessionPersistence
import dev.blamera.chatgpt.createChatGptTrialGateway
import dev.blamera.chatgpt.storage.AndroidTrialSessionStore
import dev.blamera.chatgpt.writeTrialActivityState
import dev.blamera.chatgpt.writeTrialDiagnostic
import dev.blamera.chatgpt.writeTrialHttpDiagnostic

/** Each Activity owns an observer, while only the gate and session survive its recreation. */
internal fun retainChatGptTrialModel(
    activity: ComponentActivity,
    persistent: Boolean = false
): ChatGptTrialViewModel {
    val model = ViewModelProvider(
        activity,
        TrialModelFactory(activity.applicationContext, persistent)
    )[
        ChatGptTrialViewModel::class.java
    ]
    activity.lifecycle.addObserver(TrialForegroundObserver(model))
    return model
}

private class TrialModelFactory(context: Context, private val persistent: Boolean) :
    ViewModelProvider.Factory {
    private val application = context.applicationContext
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val foreground = TrialAppForeground()
        return modelClass.cast(
            ChatGptTrialViewModel(
                if (persistent) {
                    persistentGateway(
                        application,
                        foreground
                    )
                } else {
                    createChatGptTrialGateway(
                        application,
                        foreground::await,
                        foreground::awaitRefresh
                    )
                },
                diagnostics = ::writeTrialDiagnostic,
                appForeground = foreground
            )
        )!!
    }
}

private class TrialForegroundObserver(private val model: ChatGptTrialViewModel) :
    DefaultLifecycleObserver {
    override fun onResume(owner: LifecycleOwner) {
        writeTrialActivityState(TrialActivityState.RESUMED)
        model.appForeground.update(true)
        if (model.sessionRetention) model.restoreOnce()
    }
    override fun onPause(owner: LifecycleOwner) {
        model.appForeground.update(false)
        writeTrialActivityState(TrialActivityState.PAUSED)
    }
}

/** All principal clients share ordering, never a global credential value. */
private val principalSessionCoordination = TrialSessionCoordination()

private fun persistentGateway(
    context: Context,
    foreground: TrialAppForeground
): OpenAiTrialGateway {
    val registration = AndroidTrialRegistration(context)
    return OpenAiTrialGateway(
        registration,
        HttpsTrialHttp(diagnostics = ::writeTrialHttpDiagnostic),
        ::TrialLoopback,
        environment = TrialGatewayEnvironment(
            awaitAppForeground = foreground::await,
            awaitRefreshForeground = foreground::awaitRefresh
        ),
        persistence = TrialSessionPersistence(
            AndroidTrialSessionStore(context),
            registration,
            principalSessionCoordination
        )
    )
}
