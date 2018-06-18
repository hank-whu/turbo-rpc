package rpc.turbo.serialization.protostuff;

import static rpc.turbo.util.concurrent.AttachmentThreadUtils.getOrUpdate;
import static rpc.turbo.util.concurrent.AttachmentThreadUtils.nextVarIndex;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.google.common.collect.Maps;

import io.netty.buffer.ByteBuf;
import io.protostuff.ByteBufInput;
import io.protostuff.ByteBufOutput;
import io.protostuff.Schema;
import io.protostuff.runtime.FastIdStrategy;
import io.protostuff.runtime.RuntimeSchema;
import rpc.turbo.config.TurboConstants;
import rpc.turbo.param.EmptyMethodParam;
import rpc.turbo.param.MethodParam;
import rpc.turbo.protocol.Request;
import rpc.turbo.protocol.Response;
import rpc.turbo.protocol.ResponseStatus;
import rpc.turbo.protocol.recycle.RecycleRequest;
import rpc.turbo.protocol.recycle.RecycleResponse;
import rpc.turbo.recycle.RecycleUtils;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.serialization.TracerSerializer;
import rpc.turbo.trace.Tracer;
import rpc.turbo.util.ByteBufUtils;
import rpc.turbo.util.concurrent.ConcurrentIntToObjectArrayMap;

public class ProtostuffSerializer extends Serializer {

	private static final TracerSerializer TRACER_SERIALIZER = new TracerSerializer();

	private static final int OUTPUT_ATTACHMENT_INDEX = nextVarIndex();
	private static final int INPUT_ATTACHMENT_INDEX = nextVarIndex();

	private static final Supplier<ByteBufOutput> OUTPUT_SUPPLIER = () -> new ByteBufOutput(null);
	private static final Supplier<ByteBufInput> INPUT_SUPPLIER = () -> new ByteBufInput(null, true);

	private final TracerDelegate tracerDelegate = new TracerDelegate();
	private final FastIdStrategy fastIdStrategy = new FastIdStrategy();

	/** 客户端服务端通用 */
	private final ConcurrentHashMap<Class<? extends MethodParam>, Schema<MethodParam>> schemaMap//
			= new ConcurrentHashMap<>(32, 0.5F);

	/** 服务端使用 */
	private final ConcurrentIntToObjectArrayMap<Schema<MethodParam>> fastServerSchemaMap//
			= new ConcurrentIntToObjectArrayMap<>();

	private final Schema<EmptyMethodParam> emptyMethodParamSchema//
			= RuntimeSchema.getSchema(EmptyMethodParam.class, fastIdStrategy);

	private final Schema<Response> responseSchema //
			= RuntimeSchema.getSchema(Response.class, fastIdStrategy);

	public ProtostuffSerializer() {
		fastIdStrategy.registerDelegate(tracerDelegate);
	}

	@Override
	public boolean isSupportedClassId() {
		return true;
	}

	@Override
	public void setClassIds(Map<Class<?>, Integer> classIds) {
		Map<String, Integer> pojoIDMap = Maps.newHashMapWithExpectedSize(classIds.size());

		pojoIDMap.put(Tracer.class.getName(), 0);

		for (Map.Entry<Class<?>, Integer> kv : classIds.entrySet()) {
			pojoIDMap.put(kv.getKey().getName(), kv.getValue() + 1);
		}

		fastIdStrategy.registerPojoID(pojoIDMap);
	}

	public void writeRequest(ByteBuf byteBuf, Request request) throws IOException {
		final int beginWriterIndex = byteBuf.writerIndex();

		byteBuf.writerIndex(beginWriterIndex + TurboConstants.HEADER_FIELD_LENGTH);
		byteBuf.writeInt(request.getRequestId());
		ByteBufUtils.writeVarInt(byteBuf, request.getServiceId());
		TRACER_SERIALIZER.write(byteBuf, request.getTracer());

		ByteBufOutput output = getOrUpdate(OUTPUT_ATTACHMENT_INDEX, OUTPUT_SUPPLIER);
		output.setByteBuf(byteBuf);

		if (request.getMethodParam() == null) {
			emptyMethodParamSchema.writeTo(output, null);
		} else {
			// serviceId 为服务端概念，相同的方法在不同的服务端 serviceId 会不相同，所以没法直接使用 serviceId 获取 schema
			// 如果能够证明这里比较慢，可以考虑在 request 中加入 methodId，这个在客户端是唯一的
			Class<? extends MethodParam> clazz = request.getMethodParam().getClass();
			Schema<MethodParam> schema = schema(clazz);

			schema.writeTo(output, request.getMethodParam());
		}

		int finishWriterIndex = byteBuf.writerIndex();
		int length = finishWriterIndex - beginWriterIndex - TurboConstants.HEADER_FIELD_LENGTH;

		byteBuf.setInt(beginWriterIndex, length);

		//System.out.println("request content: " + new String(ByteBufUtil.getBytes(byteBuf.duplicate())));
		//System.out.println("request length: " + byteBuf.writerIndex());

		RecycleUtils.release(request);
	}

	public Request readRequest(ByteBuf byteBuf) throws IOException {
		int requestId = byteBuf.readInt();
		int serviceId = ByteBufUtils.readVarInt(byteBuf);
		Tracer tracer = TRACER_SERIALIZER.read(byteBuf);

		Schema<MethodParam> schema = schema(serviceId);
		MethodParam methodParam = null;

		if (EmptyMethodParam.class.equals(schema.typeClass())) {
			methodParam = EmptyMethodParam.empty();
		} else {
			ByteBufInput input = getOrUpdate(INPUT_ATTACHMENT_INDEX, INPUT_SUPPLIER);
			input.setByteBuf(byteBuf, true);

			methodParam = schema.newMessage();
			schema.mergeFrom(input, methodParam);
		}

		Request request = RecycleRequest.newInstance(requestId, serviceId, tracer, methodParam);

		return request;
	}

	public void writeResponse(ByteBuf byteBuf, Response response) throws IOException {
		int beginWriterIndex = byteBuf.writerIndex();
		byteBuf.writerIndex(beginWriterIndex + TurboConstants.HEADER_FIELD_LENGTH);

		ByteBufOutput output = getOrUpdate(OUTPUT_ATTACHMENT_INDEX, OUTPUT_SUPPLIER);
		output.setByteBuf(byteBuf);

		final int statusWriterIndex = byteBuf.writerIndex();

		try {
			responseSchema.writeTo(output, response);
		} catch (Exception e) {
			e.printStackTrace();

			response.setStatusCode(ResponseStatus.BAD_RESPONSE);
			response.setResult(e.getMessage());

			byteBuf.writerIndex(statusWriterIndex);
			responseSchema.writeTo(output, response);
		}

		int finishWriterIndex = byteBuf.writerIndex();
		int length = finishWriterIndex - beginWriterIndex - TurboConstants.HEADER_FIELD_LENGTH;

		byteBuf.setInt(beginWriterIndex, length);

		RecycleUtils.release(response);
	}

	public Response readResponse(ByteBuf byteBuf) throws IOException {

		//System.out.println("response content: " + new String(ByteBufUtil.getBytes(byteBuf.duplicate())));
		//System.out.println("response length: " + byteBuf.readableBytes());

		ByteBufInput input = getOrUpdate(INPUT_ATTACHMENT_INDEX, INPUT_SUPPLIER);
		input.setByteBuf(byteBuf, true);

		Response response = RecycleResponse.newInstance(0, (byte) 0, null, null);
		responseSchema.mergeFrom(input, response);

		return response;
	}

	/**
	 * 客户端或者服务端获取ProtostuffSchema，该方法速度慢
	 * 
	 * @param clazz
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private Schema<MethodParam> schema(Class<? extends MethodParam> clazz) {
		Schema<MethodParam> schema = schemaMap.get(clazz);

		if (schema != null) {
			return schema;
		}

		schema = schemaMap.computeIfAbsent(clazz,
				k -> (Schema<MethodParam>) RuntimeSchema.getSchema(clazz, fastIdStrategy));
		return schema;
	}

	/**
	 * 服务端获取ProtostuffSchema，该方法速度快
	 * 
	 * @param serviceId
	 * @return
	 */
	private Schema<MethodParam> schema(int serviceId) {
		Schema<MethodParam> schema = fastServerSchemaMap.get(serviceId);

		if (schema != null) {
			return schema;
		}

		schema = fastServerSchemaMap.getOrUpdate(serviceId, () -> schema(getClass(serviceId)));

		return schema;
	}

}
