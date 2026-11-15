package dev.ryuya.raincompat.payload

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.IBinder
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.InvocationTargetException

/** Runs inside Rain's existing LSPatch; never ask Vector to inject another ART engine. */
class RainResourceHook : IXposedHookLoadPackage {
    private val creatingActivity = ThreadLocal<Boolean>()
    private var firstActivity = true

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (
            param.packageName != TARGET ||
                param.processName != TARGET ||
                Build.VERSION.SDK_INT != 37
        )
            return
        val handles = mutableListOf<XC_MethodHook.Unhook>()
        try {
            val contextImpl = Class.forName("android.app.ContextImpl")
            val activity =
                contextImpl.getDeclaredMethod(
                    "createActivityContext",
                    Class.forName("android.app.ActivityThread"),
                    Class.forName("android.app.LoadedApk"),
                    ActivityInfo::class.java,
                    IBinder::class.java,
                    Int::class.javaPrimitiveType,
                    Configuration::class.java,
                )
            val resources =
                Class.forName("android.app.ResourcesManager")
                    .getDeclaredMethod(
                        "createBaseTokenResources",
                        IBinder::class.java,
                        String::class.java,
                        Array<String>::class.java,
                        Array<String>::class.java,
                        Array<String>::class.java,
                        Array<String>::class.java,
                        Int::class.javaPrimitiveType,
                        Configuration::class.java,
                        Class.forName("android.content.res.CompatibilityInfo"),
                        ClassLoader::class.java,
                        List::class.java,
                    )
            check(
                resources.returnType == Resources::class.java && activity.returnType == contextImpl,
            )
            handles +=
                XposedBridge.hookMethod(
                    resources,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(call: MethodHookParam) {
                            if (
                                call.hasThrowable() ||
                                    creatingActivity.get() != true ||
                                    call.result != null
                            )
                                return
                            val result =
                                ResourceRetry.recover<Resources>(
                                    null,
                                    {
                                        try {
                                            XposedBridge.invokeOriginalMethod(
                                                resources,
                                                call.thisObject,
                                                call.args,
                                            ) as Resources?
                                        } catch (error: InvocationTargetException) {
                                            throw (error.cause ?: error)
                                        }
                                    },
                                    { recovered ->
                                        val state = if (recovered) "repaired" else "retry_failed"
                                        Log.w(TAG, "RESOURCE_RETRY state=$state")
                                        Diagnostics.event(state)
                                    },
                                )
                            if (result != null) call.result = result
                        }
                    },
                )
            handles +=
                XposedBridge.hookMethod(
                    activity,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(call: MethodHookParam) {
                            call.setObjectExtra("raincompat.previous", creatingActivity.get())
                            creatingActivity.set(
                                (call.args[2] as? ActivityInfo)?.packageName == TARGET,
                            )
                            if (firstActivity) Diagnostics.event("activity_creation_started")
                        }

                        override fun afterHookedMethod(call: MethodHookParam) {
                            if (firstActivity) {
                                Diagnostics.event(
                                    if (call.hasThrowable()) "activity_creation_threw"
                                    else "activity_context_created",
                                )
                                firstActivity = false
                            }
                            val previous = call.getObjectExtra("raincompat.previous") as? Boolean
                            if (previous == null) creatingActivity.remove()
                            else creatingActivity.set(previous)
                        }
                    },
                )
            handles +=
                XposedBridge.hookMethod(
                    Application::class.java.getDeclaredMethod("attach", Context::class.java),
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(call: MethodHookParam) {
                            if (call.hasThrowable()) return
                            (call.args[0] as? Context)?.let { Diagnostics.attach(it) }
                            Log.i(TAG, "READY existing LSPatch; resource retry installed")
                        }
                    },
                )
        } catch (error: Throwable) {
            handles.asReversed().forEach { it.unhook() }
            Log.e(TAG, "Unsupported framework; partial hooks removed", error)
        }
    }

    companion object {
        private const val TARGET = "dev.raincord.app"
        private const val TAG = "RainCompat"
    }
}
