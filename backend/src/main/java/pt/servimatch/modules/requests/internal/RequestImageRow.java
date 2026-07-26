package pt.servimatch.modules.requests.internal;

import java.util.UUID;

record RequestImageRow(UUID imageAssetId, String objectKey, String contentType, int position) {
}
