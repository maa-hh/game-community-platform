package com.game.community.model.json;

/**
 * JSON 序列化视图。内部服务可以交换数据库定位字段，公开 API 只输出 publicId。
 */
public final class ApiJsonViews {

    private ApiJsonViews() {
    }

    public interface Public {
    }

    public interface Internal extends Public {
    }
}
