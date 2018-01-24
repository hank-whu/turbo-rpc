package rpc.turbo.protocol;

import java.io.Serializable;

import rpc.turbo.param.MethodParam;
import rpc.turbo.serialization.Serializer;
import rpc.turbo.trace.Tracer;

/**
 * 请求体
 * 
 * @author Hank
 * 
 * @see Serializer
 *
 */
public class Request implements Serializable {
	private static final long serialVersionUID = 7798556948864269597L;

	private int requestId;
	private int serviceId;
	private Tracer tracer;
	private MethodParam methodParam;

	public int getRequestId() {
		return requestId;
	}

	public void setRequestId(int requestId) {
		this.requestId = requestId;
	}

	public int getServiceId() {
		return serviceId;
	}

	public void setServiceId(int serviceId) {
		this.serviceId = serviceId;
	}

	public Tracer getTracer() {
		return tracer;
	}

	public void setTracer(Tracer tracer) {
		this.tracer = tracer;
	}

	public MethodParam getMethodParam() {
		return methodParam;
	}

	public void setMethodParam(MethodParam methodParam) {
		this.methodParam = methodParam;
	}

	@Override
	public String toString() {
		return "Request{" + //
				"requestId=" + requestId + //
				", serviceId=" + serviceId + //
				", tracer=" + tracer + //
				'}';
	}
}
