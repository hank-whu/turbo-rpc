package rpc.turbo.serialization;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import rpc.turbo.param.MethodParam;
import rpc.turbo.param.MethodParamClassResolver;
import rpc.turbo.protocol.Request;
import rpc.turbo.protocol.Response;

/**
 * 序列化，非通用，仅适用于Request、Response
 * 
 * @author Hank
 *
 */
public abstract class Serializer {

	private MethodParamClassResolver methodParamClassResolver;

	/**
	 * 仅server端设置
	 * 
	 * @param methodParamClassResolver
	 */
	public final void setClassResolver(MethodParamClassResolver methodParamClassResolver) {
		this.methodParamClassResolver = methodParamClassResolver;
	}

	/**
	 * server端根据serviceId获取相应的MethodParam class
	 * 
	 * @param serviceId
	 * @return 具体的MethodParam class
	 * 
	 * @see MethodParam
	 */
	public final Class<? extends MethodParam> getClass(int serviceId) {
		return methodParamClassResolver.getMethodParamClass(serviceId);
	}

	/**
	 * 序列化request
	 * 
	 * @param byteBuf
	 * @param request
	 * @throws IOException
	 */
	public abstract void writeRequest(ByteBuf byteBuf, Request request) throws IOException;

	/**
	 * 反序列化request
	 * 
	 * @param byteBuf
	 * @return
	 * @throws IOException
	 */
	public abstract Request readRequest(ByteBuf byteBuf) throws IOException;

	/**
	 * 序列化response
	 * 
	 * @param byteBuf
	 * @param response
	 * @throws IOException
	 */
	public abstract void writeResponse(ByteBuf byteBuf, Response response) throws IOException;

	/**
	 * 反序列化response
	 * 
	 * @param byteBuf
	 * @return
	 * @throws IOException
	 */
	public abstract Response readResponse(ByteBuf byteBuf) throws IOException;
}
