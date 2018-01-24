package rpc.turbo.serialization.kryo;

import java.io.IOException;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.netty.buffer.ByteBuf;
import rpc.turbo.config.TurboConstants;
import rpc.turbo.param.MethodParam;
import rpc.turbo.protocol.Request;
import rpc.turbo.protocol.Response;
import rpc.turbo.protocol.ResponseStatus;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.serialization.TracerSerializer;
import rpc.turbo.trace.Tracer;
import rpc.turbo.util.ByteBufUtils;
import rpc.turbo.util.concurrent.AttachmentThreadUtils;

public class KryoSerializer extends Serializer {
	private static final Log logger = LogFactory.getLog(KryoSerializer.class);

	private static final int attachmentIndex = AttachmentThreadUtils.nextVarIndex();
	private static final Supplier<KryoContext> producer = () -> new KryoContext();
	private static final TracerSerializer tracerSerializer = new TracerSerializer();

	private static KryoContext kryoContext() {
		return AttachmentThreadUtils.getOrUpdate(attachmentIndex, producer);
	}

	public void writeRequest(ByteBuf byteBuf, Request request) throws IOException {
		final int beginWriterIndex = byteBuf.writerIndex();

		byteBuf.writerIndex(beginWriterIndex + TurboConstants.HEADER_FIELD_LENGTH);
		byteBuf.writeInt(request.getRequestId());
		ByteBufUtils.writeVarInt(byteBuf, request.getServiceId());
		tracerSerializer.write(byteBuf, request.getTracer());

		if (request.getMethodParam() == null) {
			byteBuf.writeBoolean(false);
		} else {
			byteBuf.writeBoolean(true);
			kryoContext().writeObject(byteBuf, request.getMethodParam());
		}

		int finishWriterIndex = byteBuf.writerIndex();
		int length = finishWriterIndex - beginWriterIndex - TurboConstants.HEADER_FIELD_LENGTH;

		byteBuf.setInt(beginWriterIndex, length);
	}

	public Request readRequest(ByteBuf byteBuf) throws IOException {
		int requestId = byteBuf.readInt();
		int serviceId = ByteBufUtils.readVarInt(byteBuf);
		Tracer tracer = tracerSerializer.read(byteBuf);

		MethodParam methodParam = null;
		if (byteBuf.readBoolean()) {
			Class<?> clazz = getClass(serviceId);
			methodParam = (MethodParam) kryoContext().readObject(byteBuf, clazz);
		}

		Request request = new Request();
		request.setRequestId(requestId);
		request.setServiceId(serviceId);
		request.setTracer(tracer);
		request.setMethodParam(methodParam);

		return request;
	}

	public void writeResponse(ByteBuf byteBuf, Response response) throws IOException {
		final int beginWriterIndex = byteBuf.writerIndex();

		byteBuf.writerIndex(beginWriterIndex + TurboConstants.HEADER_FIELD_LENGTH);
		byteBuf.writeInt(response.getRequestId());

		final int statusWriterIndex = byteBuf.writerIndex();

		try {
			byteBuf.writeByte(response.getStatusCode());
			tracerSerializer.write(byteBuf, response.getTracer());
			kryoContext().writeClassAndObject(byteBuf, response.getResult());
		} catch (Exception e) {
			if (logger.isWarnEnabled()) {
				logger.warn("kryo writeResponse error", e);
			}

			byteBuf.writerIndex(statusWriterIndex);
			byteBuf.writeByte(ResponseStatus.BAD_RESPONSE);
			tracerSerializer.write(byteBuf, response.getTracer());
			kryoContext().writeClassAndObject(byteBuf, e.getMessage());
		}

		int finishWriterIndex = byteBuf.writerIndex();
		int length = finishWriterIndex - beginWriterIndex - TurboConstants.HEADER_FIELD_LENGTH;

		byteBuf.setInt(beginWriterIndex, length);
	}

	public Response readResponse(ByteBuf byteBuf) throws IOException {
		int requestId = byteBuf.readInt();
		byte statusCode = byteBuf.readByte();
		Tracer tracer = tracerSerializer.read(byteBuf);

		Object result = kryoContext().readClassAndObject(byteBuf);

		Response response = new Response();
		response.setRequestId(requestId);
		response.setStatusCode(statusCode);
		response.setTracer(tracer);
		response.setResult(result);

		return response;
	}

}
