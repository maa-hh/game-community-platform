package com.game.community.model.base;

import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.json.ApiJsonViews;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用接口响应对象
 */
@Data
public class Result<T> implements Serializable {

    @JsonView(ApiJsonViews.Public.class)
    private Integer code;

    @JsonView(ApiJsonViews.Public.class)
    private String message;

    @JsonView(ApiJsonViews.Public.class)
    private T data;

    public static <T> Result<T> success(T data) {
        return success("操作成功", data);
    }

    public static <T> Result<T> success(String message) {
        return success(message, null);
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message == null || message.isBlank() ? "操作成功" : message);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
