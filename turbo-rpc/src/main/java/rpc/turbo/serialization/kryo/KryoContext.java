/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package rpc.turbo.serialization.kryo;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.DefaultStreamFactory;
import com.esotericsoftware.kryo.util.MapReferenceResolver;

import io.netty.buffer.ByteBuf;

public final class KryoContext {

	private final Kryo kryo;
	private final ByteBufInput input = new ByteBufInput(null);
	private final ByteBufOutput output = new ByteBufOutput(null);

	public KryoContext() {
		kryo = new Kryo(new FastClassResolver(), new MapReferenceResolver(), new DefaultStreamFactory());

		kryo.register(int[].class);
		kryo.register(long[].class);
		kryo.register(Object[].class);
		kryo.register(ArrayList.class);
		kryo.register(LinkedList.class);
		kryo.register(CopyOnWriteArrayList.class);
		kryo.register(HashMap.class);
		kryo.register(ConcurrentHashMap.class);
		kryo.register(TreeMap.class);
		kryo.register(HashSet.class);
		kryo.register(java.util.Date.class);
		kryo.register(java.sql.Date.class);
		kryo.register(LocalDate.class);
		kryo.register(LocalDateTime.class);
	}

	public void writeObject(ByteBuf buf, Object obj) throws IOException {
		output.setBuffer(buf);

		try {
			kryo.writeObject(output, obj);
		} finally {
			output.clear();
		}
	}

	public <T> T readObject(ByteBuf buf, Class<T> clazz) throws IOException {
		input.setBuffer(buf);

		try {
			return (T) kryo.readObject(input, clazz);
		} finally {
			input.clear();
		}
	}

	public void writeClassAndObject(ByteBuf buf, Object obj) throws IOException {
		output.setBuffer(buf);

		try {
			kryo.writeClassAndObject(output, obj);
		} finally {
			output.clear();
		}
	}

	@SuppressWarnings("unchecked")
	public <T> T readClassAndObject(ByteBuf buf) throws IOException {
		input.setBuffer(buf);

		try {
			return (T) kryo.readClassAndObject(input);
		} finally {
			input.clear();
		}
	}

}
