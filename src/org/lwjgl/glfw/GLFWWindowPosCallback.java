/*
 * Copyright LWJGL. All rights reserved.
 * License terms: http://lwjgl.org/license.php
 * MACHINE GENERATED FILE, DO NOT EDIT
 */
package org.lwjgl.glfw;

import java.nio.*;

import org.lwjgl.*;
import org.lwjgl.system.libffi.*;

import static org.lwjgl.system.MemoryUtil.*;
import static org.lwjgl.system.libffi.LibFFI.*;

/** Instances of this interface may be passed to the {@link GLFW#glfwSetWindowPosCallback} method. */
public abstract class GLFWWindowPosCallback extends Closure.Void {

	private static final ByteBuffer    CIF  = staticAlloc(FFICIF.SIZEOF);
	private static final PointerBuffer ARGS = staticAllocPointer(3);

	static {
		prepareCIF(
			"GLFWWindowPosCallback",
			CALL_CONVENTION_DEFAULT,
			CIF, ffi_type_void,
			ARGS, ffi_type_pointer, ffi_type_sint32, ffi_type_sint32
		);
	}

	protected GLFWWindowPosCallback() {
		super(CIF);
	}

	/**
	 * Will be called from a libffi closure invocation. Decodes the arguments and passes them to {@link #invoke}.
	 *
	 * @param args pointer to an array of jvalues
	 */
	@Override
	protected void callback(long args) {
		invoke(
			memGetAddress(memGetAddress(POINTER_SIZE * 0 + args)),
			memGetInt(memGetAddress(POINTER_SIZE * 1 + args)),
			memGetInt(memGetAddress(POINTER_SIZE * 2 + args))
		);
	}

	/**
	 * Will be called when the specified window moves.
	 *
	 * @param window the window that was moved
	 * @param xpos   the new x-coordinate, in pixels, of the upper-left corner of the client area of the window
	 * @param ypos   the new y-coordinate, in pixels, of the upper-left corner of the client area of the window
	 */
	public abstract void invoke(long window, int xpos, int ypos);

	/** A functional interface for {@link GLFWWindowPosCallback}. */
	public interface SAM {
		void invoke(long window, int xpos, int ypos);
	}

}