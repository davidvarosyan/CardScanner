/*
 * Copyright 2011 JogAmp Community. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of
 *       conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list
 *       of conditions and the following disclaimer in the documentation and/or other materials
 *       provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY JogAmp Community ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JogAmp Community OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are those of the
 * authors and should not be interpreted as representing official policies, either expressed
 * or implied, of JogAmp Community.
 */
package com.fastcredit.fcbank.scanner.sdk.camera

import java.lang.StringBuilder
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class FpsCounter
/** Creates a disabled instance  */
{
    private var fpsUpdateFramesInterval = 0

    @get:Synchronized
    var fPSStartTime: Long = 0
        private set

    @get:Synchronized
    var lastFPSUpdateTime: Long = 0
        private set

    @get:Synchronized
    var lastFPSPeriod: Long = 0
        private set

    @get:Synchronized
    var totalFPSDuration: Long = 0
        private set

    @get:Synchronized
    var totalFPSFrames = 0
        private set

    @get:Synchronized
    var lastFPS = 0f
        private set

    @get:Synchronized
    var totalFPS = 0f
        private set
    private var fpsLastTickTime = 0f

    /**
     * Increases total frame count and updates values if feature is enabled and
     * update interval is reached.<br></br>
     *
     *
     */
    @Synchronized
    fun tickFPS() {
        val now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
        if (fpsUpdateFramesInterval == 0) return
        totalFPSFrames++
        if (totalFPSFrames % fpsUpdateFramesInterval == 0) {
            lastFPSPeriod = now - lastFPSUpdateTime
            lastFPSPeriod = max(lastFPSPeriod, 1) // div 0
            lastFPS = fpsUpdateFramesInterval * 1000f / lastFPSPeriod
            totalFPSDuration = now - fPSStartTime
            totalFPSDuration = max(totalFPSDuration, 1) // div 0
            totalFPS = totalFPSFrames * 1000f / totalFPSDuration
            lastFPSUpdateTime = now
        }
        fpsLastTickTime = now.toFloat()
    }

    @Synchronized
    fun update() {
        val now = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
        if (now - fpsLastTickTime > 1000) {
            lastFPS = 0f
            lastFPSPeriod = 0
            totalFPSDuration = now - fPSStartTime
            totalFPSDuration = max(totalFPSDuration, 1) // div 0
            lastFPSUpdateTime = now
        }
    }

    fun toString(stringBuilder: StringBuilder?): StringBuilder {
        var sb = stringBuilder
        if (null == sb) {
            sb = StringBuilder()
        }
        var fpsLastS = lastFPS.toString()
        fpsLastS = fpsLastS.substring(0, fpsLastS.indexOf('.') + 2)
        val fpsTotalS = totalFPS.toString()
        fpsTotalS.substring(0, fpsTotalS.indexOf('.') + 2)
        sb.append(
            (totalFPSDuration / 1000).toString() + " s: " + fpsUpdateFramesInterval + " f / " + lastFPSPeriod + " ms, " + fpsLastS + " fps, " + lastFPSPeriod / fpsUpdateFramesInterval + " ms/f; " /* + "total: "+ fpsTotalFrames+" f, "+ fpsTotalS+ " fps, "+ fpsTotalDuration/fpsTotalFrames+" ms/f" */
        )
        return sb
    }

    override fun toString(): String {
        return toString(null).toString()
    }

    @Synchronized
    fun resetFPSCounter() {
        fPSStartTime =
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) // overwrite startTime to real init one
        lastFPSUpdateTime = fPSStartTime
        lastFPSPeriod = 0
        totalFPSFrames = 0
        lastFPS = 0f
        totalFPS = 0f
        lastFPSPeriod = 0
        totalFPSDuration = 0
    }

    @get:Synchronized
    @set:Synchronized
    var updateFPSFrames
        get() = fpsUpdateFramesInterval
        set(frames) {
            fpsUpdateFramesInterval = frames
            resetFPSCounter()
        }
}