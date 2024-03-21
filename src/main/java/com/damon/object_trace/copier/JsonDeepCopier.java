package com.damon.object_trace.copier;

import com.damon.object_trace.utils.JsonUtils;

/**
 * Use Json to clone(deep copy) object.
 */
public class JsonDeepCopier implements DeepCopier {

    @Override
    public <T> T copy(T object) {
        String json = JsonUtils.jsonToString(object);
        return JsonUtils.stringToBean(json, object.getClass());
    }
}
