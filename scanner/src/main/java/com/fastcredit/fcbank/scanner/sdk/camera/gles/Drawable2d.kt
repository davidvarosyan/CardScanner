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
package com.fastcredit.fcbank.scanner.sdk.camera.gles

import androidx.annotation.RestrictTo
import java.nio.FloatBuffer

@RestrictTo(RestrictTo.Scope.LIBRARY)
class Drawable2d {
    /**
     * Returns the array of vertices.
     *
     *
     * To avoid allocations, this returns internal state.  The caller must not modify it.
     */
    val vertexArray: FloatBuffer = RECTANGLE_BUF

    /**
     * Returns the array of texture coordinates.
     *
     *
     * To avoid allocations, this returns internal state.  The caller must not modify it.
     */
    val texCoordArray: FloatBuffer

    /**
     * Returns the number of vertices stored in the vertex array.
     */
    val vertexCount: Int

    /**
     * Returns the number of position coordinates per vertex.  This will be 2 or 3.
     */
    val coordsPerVertex: Int

    /**
     * Returns the width, in bytes, of the data for each vertex.
     */
    val vertexStride: Int

    /**
     * Returns the width, in bytes, of the data for each texture coordinate.
     */
    val texCoordStride: Int

    /**
     * Prepares a drawable from a "pre-fabricated" shape definition.
     *
     *
     * Does no EGL/GL operations, so this can be done at any time.
     */
    init {
        texCoordArray = RECTANGLE_TEX_BUF
        coordsPerVertex = 2
        vertexStride = coordsPerVertex * SIZEOF_FLOAT
        vertexCount = RECTANGLE_COORDS.size / coordsPerVertex
        texCoordStride = 2 * SIZEOF_FLOAT
    }

    override fun toString(): String {
        return "[Drawable2d: Rectangle]"
    }

    companion object {
        private const val SIZEOF_FLOAT = 4

        /**
         * Simple square, specified as a triangle strip.  The square is centered on (0,0) and has
         * a size of 1x1.
         *
         *
         * Triangles are 0-1-2 and 2-1-3 (counter-clockwise winding).
         */
        private val RECTANGLE_COORDS = floatArrayOf(
            -0.5f, -0.5f,  // 0 bottom left
            0.5f, -0.5f,  // 1 bottom right
            -0.5f, 0.5f,  // 2 top left
            0.5f, 0.5f
        )
        private val RECTANGLE_TEX_COORDS = floatArrayOf(
            0.0f, 1.0f,  // 0 bottom left
            1.0f, 1.0f,  // 1 bottom right
            0.0f, 0.0f,  // 2 top left
            1.0f, 0.0f // 3 top right
        )
        private val RECTANGLE_BUF = GlUtil.createFloatBuffer(RECTANGLE_COORDS)
        private val RECTANGLE_TEX_BUF = GlUtil.createFloatBuffer(RECTANGLE_TEX_COORDS)
    }
}