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
@file:Suppress("MemberVisibilityCanBePrivate", "SameParameterValue")

package com.fastcredit.fcbank.scanner.sdk.camera.gles

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import com.fastcredit.fcbank.scanner.sdk.camera.gles.GlUtil.checkGlError
import com.fastcredit.fcbank.scanner.sdk.camera.gles.GlUtil.checkLocation
import com.fastcredit.fcbank.scanner.sdk.camera.gles.GlUtil.createProgram
import com.fastcredit.fcbank.scanner.sdk.utils.Constants
import java.nio.FloatBuffer

class Texture2dProgram {
    // Handles to the GL program and various components of it.
    private var mProgramHandle: Int
    private val muMVPMatrixLoc: Int
    private val muTexMatrixLoc: Int
    private var muKernelLoc: Int
    private var muTexOffsetLoc = 0
    private var muColorAdjustLoc = 0
    private val maPositionLoc: Int
    private val maTextureCoordLoc: Int
    private val mTextureTarget: Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    private val mKernel = FloatArray(KERNEL_SIZE)
    private var mTexOffset: FloatArray = floatArrayOf()
    private var mColorAdjust = 0f

    /**
     * Prepares the program in the current EGL context.
     */
    init {
        mProgramHandle = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_EXT)
        if (DBG) Log.d(TAG, "Created program $mProgramHandle (TEXTURE_EXT)")

        // get locations of attributes and uniforms
        maPositionLoc = GLES20.glGetAttribLocation(mProgramHandle, "aPosition")
        checkLocation(maPositionLoc, "aPosition")
        maTextureCoordLoc = GLES20.glGetAttribLocation(mProgramHandle, "aTextureCoord")
        checkLocation(maTextureCoordLoc, "aTextureCoord")
        muMVPMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uMVPMatrix")
        checkLocation(muMVPMatrixLoc, "uMVPMatrix")
        muTexMatrixLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix")
        checkLocation(muTexMatrixLoc, "uTexMatrix")
        muKernelLoc = GLES20.glGetUniformLocation(mProgramHandle, "uKernel")
        if (muKernelLoc < 0) {
            // no kernel in this one
            muKernelLoc = -1
            muTexOffsetLoc = -1
            muColorAdjustLoc = -1
        } else {
            // has kernel, must also have tex offset and color adj
            muTexOffsetLoc = GLES20.glGetUniformLocation(mProgramHandle, "uTexOffset")
            checkLocation(muTexOffsetLoc, "uTexOffset")
            muColorAdjustLoc = GLES20.glGetUniformLocation(mProgramHandle, "uColorAdjust")
            checkLocation(muColorAdjustLoc, "uColorAdjust")

            // initialize default values
            setKernel(floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f), 0f)
            setTexSize(256, 256)
        }
    }

    /**
     * Releases the program.
     *
     *
     * The appropriate EGL context must be current (i.e. the one that was used to create
     * the program).
     */
    fun release() {
        if (DBG) Log.d(TAG, "deleting program $mProgramHandle")
        GLES20.glDeleteProgram(mProgramHandle)
        mProgramHandle = -1
    }

    /**
     * Creates a texture object suitable for use with this program.
     *
     *
     * On exit, the texture will be bound.
     */
    fun createTextureObject(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        checkGlError("glGenTextures")
        val texId = textures[0]
        GLES20.glBindTexture(mTextureTarget, texId)
        checkGlError("glBindTexture $texId")
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        checkGlError("glTexParameter")
        return texId
    }

    /**
     * Configures the convolution filter values.
     *
     * @param values Normalized filter values; must be KERNEL_SIZE elements.
     */
    private fun setKernel(values: FloatArray, colorAdjustment: Float) {
        require(values.size == KERNEL_SIZE) {
            "Kernel size is " + values.size +
                    " vs. " + KERNEL_SIZE
        }
        System.arraycopy(values, 0, mKernel, 0, KERNEL_SIZE)
        mColorAdjust = colorAdjustment
    }

    /**
     * Sets the size of the texture.  This is used to find adjacent texels when filtering.
     */
    fun setTexSize(width: Int, height: Int) {
        val rw = 1.0f / width
        val rh = 1.0f / height

        // Don't need to create a new array here, but it's syntactically convenient.
        mTexOffset = floatArrayOf(
            -rw, -rh, 0f, -rh, rw, -rh,
            -rw, 0f, 0f, 0f, rw, 0f,
            -rw, rh, 0f, rh, rw, rh
        )
        //if (DBG) Log.d(TAG, "filt size: " + width + "x" + height + ": " + Arrays.toString(mTexOffset));
    }

    /**
     * Issues the draw call.  Does the full setup on every call.
     *
     * @param mvpMatrix The 4x4 projection matrix.
     * @param vertexBuffer Buffer with vertex position data.
     * @param firstVertex Index of first vertex to use in vertexBuffer.
     * @param vertexCount Number of vertices in vertexBuffer.
     * @param coordsPerVertex The number of coordinates per vertex (e.g. x,y is 2).
     * @param vertexStride Width, in bytes, of the position data for each vertex (often
     * vertexCount * sizeof(float)).
     * @param texMatrix A 4x4 transformation matrix for texture coords.  (Primarily intended
     * for use with SurfaceTexture.)
     * @param texBuffer Buffer with vertex texture data.
     * @param texStride Width, in bytes, of the texture data for each vertex.
     */
    fun draw(
        mvpMatrix: FloatArray?, vertexBuffer: FloatBuffer?, firstVertex: Int,
        vertexCount: Int, coordsPerVertex: Int, vertexStride: Int,
        texMatrix: FloatArray?, texBuffer: FloatBuffer?, textureId: Int, texStride: Int
    ) {
        checkGlError("draw start")

        // Select the program.
        GLES20.glUseProgram(mProgramHandle)
        checkGlError("glUseProgram")

        // Set the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(mTextureTarget, textureId)

        // Copy the model / view / projection matrix over.
        GLES20.glUniformMatrix4fv(muMVPMatrixLoc, 1, false, mvpMatrix, 0)
        checkGlError("glUniformMatrix4fv")

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(muTexMatrixLoc, 1, false, texMatrix, 0)
        checkGlError("glUniformMatrix4fv")

        // Enable the "aPosition" vertex attribute.
        GLES20.glEnableVertexAttribArray(maPositionLoc)
        checkGlError("glEnableVertexAttribArray")

        // Connect vertexBuffer to "aPosition".
        GLES20.glVertexAttribPointer(
            maPositionLoc, coordsPerVertex,
            GLES20.GL_FLOAT, false, vertexStride, vertexBuffer
        )
        checkGlError("glVertexAttribPointer")

        // Enable the "aTextureCoord" vertex attribute.
        GLES20.glEnableVertexAttribArray(maTextureCoordLoc)
        checkGlError("glEnableVertexAttribArray")

        // Connect texBuffer to "aTextureCoord".
        GLES20.glVertexAttribPointer(
            maTextureCoordLoc, 2,
            GLES20.GL_FLOAT, false, texStride, texBuffer
        )
        checkGlError("glVertexAttribPointer")

        // Populate the convolution kernel, if present.
        if (muKernelLoc >= 0) {
            GLES20.glUniform1fv(muKernelLoc, KERNEL_SIZE, mKernel, 0)
            GLES20.glUniform2fv(muTexOffsetLoc, KERNEL_SIZE, mTexOffset, 0)
            GLES20.glUniform1f(muColorAdjustLoc, mColorAdjust)
        }

        // Draw the rect.
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, firstVertex, vertexCount)
        checkGlError("glDrawArrays")

        // Done -- disable vertex array, texture, and program.
        GLES20.glDisableVertexAttribArray(maPositionLoc)
        GLES20.glDisableVertexAttribArray(maTextureCoordLoc)
        GLES20.glBindTexture(mTextureTarget, 0)
        GLES20.glUseProgram(0)
    }

    companion object {
        private val DBG = Constants.DEBUG
        private const val TAG = GlUtil.TAG

        // Simple vertex shader, used for all programs.
        private const val VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uTexMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "    gl_Position = uMVPMatrix * aPosition;\n" +
                "    vTextureCoord = (uTexMatrix * aTextureCoord).xy;\n" +
                "}\n"

        // Simple fragment shader for use with external 2D textures (e.g. what we get from
        // SurfaceTexture).
        private const val FRAGMENT_SHADER_EXT = "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                "}\n"
        const val KERNEL_SIZE = 9
    }
}