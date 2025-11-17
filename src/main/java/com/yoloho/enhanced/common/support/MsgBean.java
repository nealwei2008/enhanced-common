package com.yoloho.enhanced.common.support;

import org.apache.http.HttpStatus;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 返回json信息的bean
 * 
 * @author wuzl
 * @author neal 修改代码结构，将返回数据统一固定为data
 * <blockquote><pre>
 * {
 *    code: 响应码，成功：200，失败：非200。取值符合 {@link org.apache.http.HttpStatus}
 *    msg: 错误提示，成功：空字符串；失败，对应的错误提示
 *    data: 成功响应的数据
 *    timestamp: 响应时间戳，单位s
 * }</pre></blockquote>
 */
public class MsgBean implements Serializable{
	private static final long serialVersionUID = 1L;

	protected int code = HttpStatus.SC_OK;
	protected String msg;

	protected Map<String, Object> dto = new HashMap<>();
	protected Map<String, Object> data = new HashMap<>();
	private Object dataObject;
	
	public MsgBean() {
		this(HttpStatus.SC_OK, "success");
	}
	
	public MsgBean(int code, String msg) {
	    this.msg = msg;
        this.code = code;
    }
	
	public int getCode() {
        return code;
    }
	
	public String getMsg() {
        return msg;
    }

	/**
	 * Custom property
	 * 与 {@link #setData(Object)} 互斥，且优先级低
	 * @param key
	 * @param value
	 * @comment
	 */
	public MsgBean put(String key, Object value) {
		data.put(key, value);
		return this;
	}

	/**
	 * Set properties batch
	 * 与 {@link #setData(Object)} 互斥，且优先级低
	 * @param map
	 * @return 
	 */
	public MsgBean putAll(Map<String, Object> map) {
		if (map != null && map.size() > 0) {
			data.putAll(map);
		}
		return this;
	}
	
	/**
	 * 设置返回data，与 {@link #put(String, Object)} 和 {@link #putAll(Map)} 互斥。
	 * 本方法优先级高，同时设置，则覆盖 {@link #put()}方法
	 * @param object
	 * @return
	 */
	public MsgBean setData(Object object) {
	    this.dataObject = object;
	    return this;
	}
	
	/**
	 * 400 Bad Request (HTTP/1.1 - RFC 2616)
	 */
	public MsgBean failure(String msg) {
		return failure(HttpStatus.SC_BAD_REQUEST, msg);
	}
	
	public MsgBean failure(int code, String msg) {
	    this.code = code;
        if(msg == null || msg.contains("dubbo") || msg.contains("Exception") ){
            msg = "网络繁忙，请重试";
        }
        this.msg = msg;
        return this;
    }
	
	/**
	 * Return the structure
	 * 
	 * @return
	 */
	public Map<String, Object> returnMsg() {
	    dto.put("msg", msg);
        dto.put("code", this.code);
        if (dataObject != null) {
            dto.put("data", dataObject);
        } else {
            dto.put("data", data);
        }
        dto.put("timestamp", (new Date()).getTime() / 1000);
		return dto;
	}

}
