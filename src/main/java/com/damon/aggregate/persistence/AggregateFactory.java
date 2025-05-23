package com.damon.aggregate.persistence;

import com.damon.aggregate.persistence.copier.DeepCopier;
import com.damon.aggregate.persistence.copier.JsonDeepCopier;

public class AggregateFactory {
    private static DeepCopier deepCopier = new JsonDeepCopier();

    private AggregateFactory() {
        throw new IllegalStateException("A factory class, please use static method");
    }

    public static <R extends Versionable> Aggregate<R> createAggregate(R root) {
        return new Aggregate(root, deepCopier);
    }

    public static <R extends Versionable> Aggregate<R> createAggregate(R root, DeepCopier deepCopier) {
        return new Aggregate(root, deepCopier);
    }

}
