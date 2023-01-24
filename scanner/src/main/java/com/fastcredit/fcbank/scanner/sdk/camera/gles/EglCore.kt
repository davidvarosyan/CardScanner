/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:Suppress("unused", "UNUSED_PARAMETER", "MemberVisibilityCanBePrivate")

package com.fastcredit.fcbank.scanner.sdk.camera.gles

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.util.Log
import android.view.SurfaceHolder
import androidx.annotation.RestrictTo
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLSurface

@RestrictTo(RestrictTo.Scope.LIBRARY)
class EglCore @JvmOverloads constructor(sharedContext: EGLContext? = null, flags: Int = 0) {
    private val mEgl: EGL10
    private var mEGLDisplay = EGL10.EGL_NO_DISPLAY
    private var mEGLContext = EGL10.EGL_NO_CONTEXT
    private var mEGLConfig: EGLConfig? = null

    /**
     * Returns the GLES version this context is configured for (currently 2 or 3).
     */
    var glVersion = -1
    /**
     * Prepares EGL display and context.
     *
     *
     * @param sharedContext The context to share, or null if sharing is not desired.
     * @param flags Configuration bit flags, e.g. FLAG_RECORDABLE.
     */
    /**
     * Prepares EGL display and context.
     *
     *
     * Equivalent to EglCore(null, 0).
     */
    init {
        if (mEGLDisplay !== EGL10.EGL_NO_DISPLAY) {
            throw RuntimeException("EGL already set up")
        }
        mEgl = EGLContext.getEGL() as EGL10
        mEGLDisplay = mEgl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        if (mEGLDisplay === EGL10.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }
        val version = IntArray(2)
        if (!mEgl.eglInitialize(mEGLDisplay, version)) {
            mEGLDisplay = null
            throw RuntimeException("unable to initialize EGL14")
        }
        val config = getConfig(flags)
            ?: throw RuntimeException("Unable to find a suitable EGLConfig")
        val attrList = intArrayOf(
            EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL10.EGL_NONE
        )
        val context = mEgl.eglCreateContext(mEGLDisplay, config, EGL10.EGL_NO_CONTEXT, attrList)
        checkEglError("eglCreateContext")
        mEGLConfig = config
        mEGLContext = context
        glVersion = 2

        // Confirm with query.
        val values = IntArray(1)
        mEgl.eglQueryContext(
            mEGLDisplay, mEGLContext, EGL_CONTEXT_CLIENT_VERSION,
            values
        )
        if (DBG) Log.d(TAG, "EGLContext created, client version " + values[0])
    }

    /**
     * Finds a suitable EGLConfig.
     *
     * @param flags Bit flags from constructor.
     */
    private fun getConfig(flags: Int): EGLConfig? {
        val renderableType =  /* EGL14.EGL_OPENGL_ES2_BIT */4

        // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
        // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
        // when reading into a GL_RGBA buffer.
        val attribList = intArrayOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,  //EGL14.EGL_DEPTH_SIZE, 16,
            //EGL14.EGL_STENCIL_SIZE, 8,
            EGL10.EGL_RENDERABLE_TYPE, renderableType,
            EGL10.EGL_NONE, 0,  // placeholder for recordable [@-3]
            EGL10.EGL_NONE
        )
        if (flags and FLAG_RECORDABLE != 0) {
            attribList[attribList.size - 3] = EGL_RECORDABLE_ANDROID
            attribList[attribList.size - 2] = 1
        }
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!mEgl.eglChooseConfig(mEGLDisplay, attribList, configs, configs.size, numConfigs)) {
            if (DBG) Log.w(TAG, "unable to find RGB8888 / " + 2 + " EGLConfig")
            return null
        }
        return configs[0]
    }

    /**
     * Discards all resources held by this class, notably the EGL context.  This must be
     * called from the thread where the context was created.
     *
     *
     * On completion, no context will be current.
     */
    fun release() {
        if (mEGLDisplay !== EGL10.EGL_NO_DISPLAY) {
            // Android is unusual in that it uses a reference-counted EGLDisplay.  So for
            // every eglInitialize() we need an eglTerminate().
            mEgl.eglMakeCurrent(
                mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT
            )
            mEgl.eglDestroyContext(mEGLDisplay, mEGLContext)
            // TODO mEgl.eglReleaseThread();
            mEgl.eglTerminate(mEGLDisplay)
        }
        mEGLDisplay = EGL10.EGL_NO_DISPLAY
        mEGLContext = EGL10.EGL_NO_CONTEXT
        mEGLConfig = null
    }

    @Throws(Throwable::class)
    protected fun finalize() {
        if (mEGLDisplay !== EGL10.EGL_NO_DISPLAY) {
            // We're limited here -- finalizers don't run on the thread that holds
            // the EGL state, so if a surface or context is still current on another
            // thread we can't fully release it here.  Exceptions thrown from here
            // are quietly discarded.  Complain in the log file.
            if (DBG) Log.w(
                TAG,
                "WARNING: EglCore was not explicitly released -- state may be leaked"
            )
            release()
        }
    }

    /**
     * Destroys the specified surface.  Note the EGLSurface won't actually be destroyed if it's
     * still current in a context.
     */
    fun releaseSurface(eglSurface: EGLSurface?) {
        mEgl.eglDestroySurface(mEGLDisplay, eglSurface)
    }

    /**
     * Creates an EGL surface associated with a Surface.
     *
     *
     * If this is destined for MediaCodec, the EGLConfig should have the "recordable" attribute.
     */
    fun createWindowSurface(surface: Any): EGLSurface {
        if (surface !is SurfaceHolder && surface !is SurfaceTexture) {
            throw RuntimeException("invalid surface: $surface")
        }

        // Create a window surface, and attach it to the Surface we received.
        val surfaceAttribs = intArrayOf(
            EGL10.EGL_NONE
        )
        val eglSurface = mEgl.eglCreateWindowSurface(
            mEGLDisplay, mEGLConfig, surface,
            surfaceAttribs
        )
        checkEglError("eglCreateWindowSurface")
        if (eglSurface == null) {
            throw RuntimeException("surface was null")
        }
        return eglSurface
    }

    /**
     * Creates an EGL surface associated with an offscreen buffer.
     */
    fun createOffscreenSurface(width: Int, height: Int): EGLSurface {
        val surfaceAttribs = intArrayOf(
            EGL10.EGL_WIDTH, width,
            EGL10.EGL_HEIGHT, height,
            EGL10.EGL_NONE
        )
        val eglSurface = mEgl.eglCreatePbufferSurface(
            mEGLDisplay, mEGLConfig,
            surfaceAttribs
        )
        checkEglError("eglCreatePbufferSurface")
        if (eglSurface == null) {
            throw RuntimeException("surface was null")
        }
        return eglSurface
    }

    /**
     * Makes our EGL context current, using the supplied surface for both "draw" and "read".
     */
    fun makeCurrent(eglSurface: EGLSurface?) {
        if (mEGLDisplay === EGL10.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            if (DBG) Log.d(TAG, "NOTE: makeCurrent w/o display")
        }
        if (!mEgl.eglMakeCurrent(mEGLDisplay, eglSurface, eglSurface, mEGLContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    /**
     * Makes our EGL context current, using the supplied "draw" and "read" surfaces.
     */
    fun makeCurrent(drawSurface: EGLSurface?, readSurface: EGLSurface?) {
        if (mEGLDisplay === EGL10.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            if (DBG) Log.d(TAG, "NOTE: makeCurrent w/o display")
        }
        if (!mEgl.eglMakeCurrent(mEGLDisplay, drawSurface, readSurface, mEGLContext)) {
            throw RuntimeException("eglMakeCurrent(draw,read) failed")
        }
    }

    /**
     * Makes no context current.
     */
    fun makeNothingCurrent() {
        if (!mEgl.eglMakeCurrent(
                mEGLDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE,
                EGL10.EGL_NO_CONTEXT
            )
        ) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    /**
     * Calls eglSwapBuffers.  Use this to "publish" the current frame.
     *
     * @return false on failure
     */
    fun swapBuffers(eglSurface: EGLSurface?): Boolean {
        return mEgl.eglSwapBuffers(mEGLDisplay, eglSurface)
    }

    /**
     * Returns true if our context and the specified surface are current.
     */
    fun isCurrent(eglSurface: EGLSurface): Boolean {
        return mEGLContext == mEgl.eglGetCurrentContext() && eglSurface == mEgl.eglGetCurrentSurface(
            EGL10.EGL_DRAW
        )
    }

    /**
     * Performs a simple surface query.
     */
    fun querySurface(eglSurface: EGLSurface?, what: Int): Int {
        val value = IntArray(1)
        mEgl.eglQuerySurface(mEGLDisplay, eglSurface, what, value)
        return value[0]
    }

    /**
     * Queries a string value.
     */
    fun queryString(what: Int): String {
        return mEgl.eglQueryString(mEGLDisplay, what)
    }

    /**
     * Checks for EGL errors.  Throws an exception if an error has been raised.
     */
    private fun checkEglError(msg: String) {
        var error: Int
        if (mEgl.eglGetError().also { error = it } != EGL14.EGL_SUCCESS) {
            throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
        }
    }

    companion object {
        private val DBG = GlUtil.DBG
        private const val TAG = GlUtil.TAG

        /**
         * Constructor flag: surface must be recordable.  This discourages EGL from using a
         * pixel format that cannot be converted efficiently to something usable by the video
         * encoder.
         */
        const val FLAG_RECORDABLE = 0x01

        // Android-specific extension.
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private const val EGL_CONTEXT_CLIENT_VERSION = 0x3098
    }
}