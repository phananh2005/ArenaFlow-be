package com.phananh.arenaflow.application.support.storage;

import java.util.List;

/** Một trang id asset đã lưu; {@code nextCursor} là null ở trang cuối. */
public record AssetPage(List<String> publicIds, String nextCursor) {
}
