package com.vidyo.vidyoconnector.tiles;

/**
 * Type of tiles
 */
public enum ViewType {
    LOCAL(2), REMOTE(0), SHARE(1);

    private int order;

    ViewType(int order) {
        this.order = order;
    }

    public int getOrder() {
        return this.order;
    }
}