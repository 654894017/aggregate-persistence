package com.damon.aggregate.persistence;


import cn.hutool.core.builder.EqualsBuilder;
import com.damon.aggregate.persistence.copier.DeepCopier;

public class Aggregate<R extends Versionable> {
    public static final int NEW_VERSION = 0;
    private R root;
    private R snapshot;

    public Aggregate(R root, DeepCopier deepCopier) {
        if (root == null) {
            return;
        }
        this.root = root;
        this.snapshot = deepCopier.copy(root);
    }

    /**
     * Whether the aggregate is changed.
     *
     * @return true if the aggregate is changed, false if the aggregate is unchanged.
     */
    public boolean isChanged() {
        return !EqualsBuilder.reflectionEquals(root, snapshot, false);
    }

    public boolean isNew() {
        return root.getVersion() == NEW_VERSION || root.getVersion() == null;
    }

    public R getRoot() {
        return root;
    }

    public void setRoot(R root) {
        this.root = root;
    }

    public R getSnapshot() {
        return snapshot;
    }
}


