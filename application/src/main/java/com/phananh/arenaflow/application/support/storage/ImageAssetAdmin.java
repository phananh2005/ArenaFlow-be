package com.phananh.arenaflow.application.support.storage;

import java.util.List;
import java.util.Set;

/** Thao tác admin độc lập với storage, chỉ job dọn asset mồ côi dùng, không bao giờ gọi trong transaction. */
public interface ImageAssetAdmin {

    AssetPage listAssets(String prefix, String cursor, int maxResults);

    /** Xoá tối đa 100 asset mỗi lời gọi; trả về các id mà storage báo đã xoá. */
    Set<String> deleteAssets(List<String> publicIds);
}
